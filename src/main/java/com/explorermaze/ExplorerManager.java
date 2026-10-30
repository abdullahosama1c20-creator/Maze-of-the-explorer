package com.explorermaze;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Explorer: an invulnerable, re-textured wither skeleton that patrols the maze.
 * <ul>
 *   <li>Near you: heartbeat sound + an eye on your screen.</li>
 *   <li>Sees you: he speeds up, you get Darkness + Slowness.</li>
 *   <li>Keeps you in sight (or touches you): 3 hearts of damage and you are dragged far from the exit.</li>
 * </ul>
 */
public final class ExplorerManager {
    private static final Key HUD_FONT = Key.key("explorermaze", "hud");
    private static final Title.Times EYE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(100));

    private final ExplorerMazePlugin plugin;
    private WitherSkeleton explorer;
    private BukkitTask task;
    private int tick;

    private final Map<UUID, Integer> gaze = new HashMap<>();
    private final Map<UUID, Long> immuneUntil = new HashMap<>();
    private final Map<UUID, Long> cloakUntil = new HashMap<>();
    private long slowedUntil;
    private Location patrolTarget, lastKnown;
    private long patrolSince;
    private boolean chasing;

    public ExplorerManager(ExplorerMazePlugin plugin) { this.plugin = plugin; }

    public LivingEntity entity() { return explorer; }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stop();
        Location sp = plugin.arenaWorld().maze().explorerSpawn(plugin.arenaWorld().world());
        spawn(sp);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    private void spawn(Location loc) {
        explorer = loc.getWorld().spawn(loc, WitherSkeleton.class, e -> {
            e.customName(Msg.c("&4The Explorer"));
            e.setCustomNameVisible(false);
            e.setInvulnerable(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCanPickupItems(false);
            e.setSilent(true);
            e.getEquipment().clear();
            Bukkit.getMobGoals().removeAllGoals(e);
            attr(e, Attribute.SCALE, 1.35);
            attr(e, Attribute.FOLLOW_RANGE, 160);
            attr(e, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            attr(e, Attribute.MOVEMENT_SPEED, plugin.getConfig().getDouble("arena.patrol-speed", 0.20));
        });
        chasing = false;
        patrolTarget = null;
        lastKnown = null;
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        if (explorer != null) { explorer.remove(); explorer = null; }
        gaze.clear(); immuneUntil.clear(); cloakUntil.clear();
        slowedUntil = 0;
    }

    public void forget(UUID id) { gaze.remove(id); immuneUntil.remove(id); cloakUntil.remove(id); }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    // ------------------------------------------------------------------ item hooks

    public void slow(int seconds) {
        slowedUntil = System.currentTimeMillis() + seconds * 1000L;
        if (explorer != null)
            explorer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 3, false, false, false));
    }

    public void cloak(Player p, int seconds) {
        cloakUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        gaze.put(p.getUniqueId(), 0);
    }

    // ------------------------------------------------------------------ main loop

    private void tick() {
        if (explorer == null || !explorer.isValid()) {
            if (plugin.match().isRunning()) {
                spawn(plugin.arenaWorld().maze().explorerSpawn(plugin.arenaWorld().world()));
            }
            return;
        }
        tick += 2;
        List<Player> players = plugin.match().mazePlayers();
        if (players.isEmpty()) return;

        FileConfigView cfg = new FileConfigView(plugin);
        long now = System.currentTimeMillis();
        Location el = explorer.getLocation();
        Player target = null;
        double targetDist = Double.MAX_VALUE;

        for (Player p : players) {
            if (!p.getWorld().equals(el.getWorld()) || p.isDead()) continue;
            UUID id = p.getUniqueId();
            double d = el.distance(p.getLocation());
            boolean cloaked = cloakUntil.getOrDefault(id, 0L) > now;
            boolean immune = immuneUntil.getOrDefault(id, 0L) > now;

            // ---- proximity cues: spooky sound + eye on screen
            if (d <= cfg.warnRange) {
                if (tick % 10 == 0) eye(p);
                if (tick % 20 == 0) {
                    float vol = (float) Math.max(0.4, 1.4 - d / cfg.warnRange);
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, vol, 1.0f);
                }
                if (d < 12 && tick % 60 == 0) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSE, 0.9f, 0.8f);
                if (d < 6 && tick % 40 == 0) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSEST, 1.0f, 0.7f);
            }

            // ---- sight
            boolean sees = !cloaked && !immune && d <= cfg.sightRange && explorer.hasLineOfSight(p);
            int g = gaze.getOrDefault(id, 0);
            if (sees) {
                if (g == 0) firstSight(p);
                g += 2;
                if (tick % 10 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 2, false, false, false));
                }
                if (d < targetDist) { target = p; targetDist = d; }
                lastKnown = p.getLocation().clone();
                if (g >= cfg.gazeTicks || d <= 2.4) {
                    gaze.put(id, 0);
                    caught(p);
                    continue;
                }
            } else {
                g = Math.max(0, g - 1);
            }
            gaze.put(id, g);
        }

        // ---- movement
        boolean nowChasing = target != null;
        if (nowChasing != chasing) {
            chasing = nowChasing;
            attr(explorer, Attribute.MOVEMENT_SPEED, chasing ? cfg.chaseSpeed : cfg.patrolSpeed);
        }
        if (tick % 10 != 0) return;
        if (target != null) {
            explorer.getPathfinder().moveTo(target.getLocation(), 1.0);
            patrolTarget = null;
        } else if (lastKnown != null) {
            if (el.distanceSquared(lastKnown) < 9) lastKnown = null;
            else explorer.getPathfinder().moveTo(lastKnown, 1.0);
        } else {
            if (patrolTarget == null || el.distanceSquared(patrolTarget) < 9 || now - patrolSince > 15000) {
                patrolTarget = plugin.arenaWorld().maze().randomCellNear(el.getWorld(), el, 70);
                patrolSince = now;
            }
            explorer.getPathfinder().moveTo(patrolTarget, 1.0);
        }
    }

    private void eye(Player p) {
        p.showTitle(Title.title(Component.text("\ue000").font(HUD_FONT), Component.empty(), EYE_TIMES));
    }

    private void firstSight(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.9f);
        Msg.bar(p, "&4&lHE SEES YOU");
        plugin.advancements().grant(p, "seen");
    }

    /** Damage + drag back to a far corner of the maze. */
    private void caught(Player p) {
        UUID id = p.getUniqueId();
        immuneUntil.put(id, System.currentTimeMillis() + 6000L);
        plugin.match().markCaught(p);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
        Msg.title(p, "&4&lCAUGHT", "&7He dragged you back into the dark...", 100, 1800, 400);
        p.damage(plugin.getConfig().getDouble("arena.caught-damage", 6.0));
        if (!plugin.match().isRunning() || !plugin.match().isInArena(p) || p.isDead()) return;

        Location far = plugin.arenaWorld().maze().farFromExit(p.getWorld(), p.getLocation());
        p.teleport(far);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false, false));
        plugin.advancements().grant(p, "caught");

        // the Explorer wanders off somewhere else
        Location away = plugin.arenaWorld().maze().randomCellAwayFrom(p.getWorld(), far, 50);
        explorer.teleport(away);
        lastKnown = null;
        patrolTarget = null;
    }

    /** Tiny holder so config is read once per tick. */
    private static final class FileConfigView {
        final double warnRange, sightRange, patrolSpeed, chaseSpeed;
        final int gazeTicks;

        FileConfigView(ExplorerMazePlugin plugin) {
            var c = plugin.getConfig();
            warnRange = c.getDouble("arena.warn-range", 28);
            sightRange = c.getDouble("arena.sight-range", 24);
            patrolSpeed = c.getDouble("arena.patrol-speed", 0.20);
            chaseSpeed = c.getDouble("arena.chase-speed", 0.34);
            gazeTicks = (int) (c.getDouble("arena.gaze-seconds", 3.0) * 20);
        }
    }
}
