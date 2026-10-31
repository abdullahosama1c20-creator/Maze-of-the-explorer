package com.explorermaze;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** The whole lobby -> maze -> reward flow. One match at a time. */
public final class MatchManager {
    public enum State { IDLE, WAITING, RUNNING, ENDING }

    private final ExplorerMazePlugin plugin;
    private State state = State.IDLE;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> claiming = new HashSet<>();
    private final Map<UUID, Integer> caughtCount = new HashMap<>();
    private final Map<UUID, Set<String>> opened = new HashMap<>();
    private long runStart;
    private int startCount;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, BukkitTask> claimTimers = new HashMap<>();
    private BukkitTask countdownTask;
    private int countdown;

    public MatchManager(ExplorerMazePlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ queries

    public State state() { return state; }
    public boolean isRunning() { return state == State.RUNNING; }
    public boolean isInArena(Player p) { return players.contains(p.getUniqueId()); }
    public boolean isClaiming(Player p) { return claiming.contains(p.getUniqueId()); }
    public int playerCount() { return players.size(); }
    public int countdown() { return countdown; }
    public int markCaught(Player p) { return caughtCount.merge(p.getUniqueId(), 1, Integer::sum); }

    /** Counts distinct chests a player opened (for the Ransacker advancement). */
    public void noteChest(Player p, org.bukkit.block.Block b) {
        Set<String> s = opened.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        s.add(b.getX() + "," + b.getY() + "," + b.getZ());
        if (s.size() >= 5) plugin.advancements().grant(p, "ransacker");
    }

    /** Players currently inside the maze (not in the reward room). */
    public List<Player> mazePlayers() {
        List<Player> out = new ArrayList<>();
        if (state != State.RUNNING) return out;
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && !claiming.contains(id)) out.add(p);
        }
        return out;
    }

    private List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (UUID id : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ joining / leaving

    public void join(Player p) {
        UUID id = p.getUniqueId();
        if (players.contains(id)) { Msg.tell(p, "&cYou are already in the arena. Use &e/arena leave&c."); return; }
        if (state == State.RUNNING || state == State.ENDING) {
            Msg.tell(p, "&cA run is in progress. Try again in a moment!");
            return;
        }
        if (plugin.store().has(id)) plugin.store().restore(p);   // leftover from a crash
        plugin.store().save(p);
        players.add(id);
        prepare(p);
        p.teleport(plugin.arenaWorld().lobbySpawn());
        plugin.advancements().grant(p, "entered");
        Msg.title(p, "&5&lTHE ARENA", "&7Your items are safe. You will get them back.", 300, 2500, 500);

        for (Player o : online()) Msg.tell(o, "&e" + p.getName() + " &7joined the arena. &8(" + players.size() + ")");

        if (state == State.IDLE) {
            state = State.WAITING;
            countdown = plugin.getConfig().getInt("arena.wait-seconds", 60);
            plugin.arenaWorld().buildMaze();
            startCountdown();
        }
    }

    public void leave(Player p) {
        UUID id = p.getUniqueId();
        if (!players.remove(id)) return;
        cleanupPlayer(p);
        Msg.tell(p, "&7You left the arena.");
        if (players.isEmpty()) reset();
    }

    public void eliminate(Player p, String reasonLine) {
        UUID id = p.getUniqueId();
        if (!players.remove(id)) return;
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 0.7f);
        cleanupPlayer(p);
        Msg.title(p, "&c&lYOU LOST", reasonLine, 200, 3000, 800);
        if (players.isEmpty()) reset();
    }

    /** Gives the player their old life back (items, xp, location...). */
    private void cleanupPlayer(Player p) {
        UUID id = p.getUniqueId();
        claiming.remove(id);
        BukkitTask t = claimTimers.remove(id);
        if (t != null) t.cancel();
        plugin.explorer().forget(id);
        p.clearTitle();
        plugin.store().restore(p);
    }

    private void prepare(Player p) {
        p.getInventory().clear();
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.ADVENTURE);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20.0 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    // ------------------------------------------------------------------ countdown

    private void startCountdown() {
        countdownTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != State.WAITING) { cancel(); return; }
                if (players.isEmpty()) { cancel(); reset(); return; }
                if (countdown > 0) countdown--;
                for (Player p : online()) {
                    if (countdown > 0) {
                        Msg.bar(p, "&5The Explorer awakens in &c" + countdown + "s &8| &7" + players.size() + " player(s)");
                        if (countdown <= 5) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    } else {
                        Msg.bar(p, "&5Preparing the maze...");
                    }
                }
                if (countdown <= 0 && plugin.arenaWorld().isMazeReady()) { cancel(); start(); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void forceStart() { if (state == State.WAITING) countdown = 0; }

    // ------------------------------------------------------------------ the run

    private void later(long ticks, Runnable r) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> { if (state == State.RUNNING) r.run(); }, ticks));
    }

    private void titleAll(String t, String s, int in, int stay, int out) {
        for (Player p : online()) if (!claiming.contains(p.getUniqueId())) Msg.title(p, t, s, in, stay, out);
    }

    private void soundAll(Sound s, float vol, float pitch) {
        for (Player p : online()) p.playSound(p.getLocation(), s, vol, pitch);
    }

    private void start() {
        state = State.RUNNING;
        caughtCount.clear();
        opened.clear();
        startCount = players.size();
        final int introTicks = 230;
        for (Player p : online()) {
            prepare(p);
            p.teleport(plugin.arenaWorld().mazeStart());
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, introTicks, 7, false, false, false));
        }
        later(2, () -> {
            titleAll("&4&lTHE EXPLORER", "&7He walks these halls. And he is watching.", 400, 3200, 600);
            soundAll(Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.5f);
        });
        later(70, () -> {
            titleAll("&e&lESCAPE THE MAZE", "&7Find the &6dark oak door &7hidden in the walls.", 300, 3200, 500);
            soundAll(Sound.AMBIENT_CAVE, 1f, 0.7f);
        });
        later(140, () -> {
            titleAll("&c&lDO NOT DIE", "&7Stay out of the &4Explorer's &7sight. He drags you back.", 300, 3200, 500);
            soundAll(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f);
        });
        later(210, () -> {
            titleAll("&4&lRUN", "", 100, 1200, 400);
            soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.6f);
            for (Player p : online()) p.removePotionEffect(PotionEffectType.SLOWNESS);
            runStart = System.currentTimeMillis();
            plugin.explorer().start();
            startExitAmbience();
        });
    }

    /** A faint chime + soul flames at the exit so it can be found by ear when you are close. */
    private void startExitAmbience() {
        if (!plugin.getConfig().getBoolean("maze.exit-ambience", true)) return;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.RUNNING || !plugin.arenaWorld().isMazeReady()) return;
            var w = plugin.arenaWorld().world();
            Location door = plugin.arenaWorld().maze().doorLocation(w);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, door.clone().add(0.5, 1.0, 0.5), 12, 0.3, 0.6, 0.3, 0.01);
            for (Player p : mazePlayers()) {
                if (!p.getWorld().equals(w)) continue;
                double d = p.getLocation().distance(door);
                if (d <= 45) p.playSound(door, Sound.BLOCK_AMETHYST_BLOCK_CHIME, (float) Math.max(0.15, 1.0 - d / 45.0), 0.6f);
            }
        }, 40L, 40L));
    }

    /** A player stepped through the exit door. */
    public void win(Player p) {
        if (state != State.RUNNING || !players.contains(p.getUniqueId())) return;
        state = State.ENDING;
        plugin.explorer().stop();
        UUID winner = p.getUniqueId();

        for (UUID id : new ArrayList<>(players)) {
            if (id.equals(winner)) continue;
            Player o = Bukkit.getPlayer(id);
            players.remove(id);
            if (o != null) {
                o.playSound(o.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 0.7f);
                cleanupPlayer(o);
                Msg.title(o, "&c&lYOU LOST", "&7" + p.getName() + " escaped the maze. You win nothing.", 200, 3500, 800);
            }
        }

        claiming.add(winner);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.teleport(plugin.arenaWorld().roomSpawn());
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.2f);
        Msg.title(p, "&5&lYOU ESCAPED", "&7Press the &denchanting table&7.", 300, 4000, 800);
        plugin.advancements().grant(p, "escaped");
        if (!caughtCount.containsKey(winner)) plugin.advancements().grant(p, "untouched");
        if (startCount >= 2) plugin.advancements().grant(p, "outrun");
        if (System.currentTimeMillis() - runStart <= 5 * 60_000L) plugin.advancements().grant(p, "speedrunner");

        // particles around the table while waiting
        final BukkitTask fx = new BukkitRunnable() {
            @Override public void run() {
                if (!claiming.contains(winner)) { cancel(); return; }
                p.getWorld().spawnParticle(Particle.ENCHANT, plugin.arenaWorld().roomTable(), 40, 0.8, 0.6, 0.8, 0.5);
            }
        }.runTaskTimer(plugin, 5L, 5L);
        tasks.add(fx);

        int timeout = plugin.getConfig().getInt("rewards.claim-timeout-seconds", 90);
        claimTimers.put(winner, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player w = Bukkit.getPlayer(winner);
            if (w != null && claiming.contains(winner)) {
                players.remove(winner);
                cleanupPlayer(w);
                Msg.tell(w, "&cYou took too long - the room collapsed. No rewards.");
                if (players.isEmpty()) reset();
            }
        }, timeout * 20L));
    }

    /** The winner pressed the enchanting table. */
    public void claim(Player p) {
        UUID id = p.getUniqueId();
        if (!claiming.contains(id)) return;
        players.remove(id);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        cleanupPlayer(p);       // restores original inventory + location first...
        giveRewards(p);         // ...then adds the prize on top
        if (players.isEmpty()) reset();
    }

    private void giveRewards(Player p) {
        var c = plugin.getConfig();
        int levels = c.getInt("rewards.xp-levels", 100);
        int ingots = c.getInt("rewards.netherite-ingots", 3);
        p.giveExpLevels(levels);
        give(p, new ItemStack(Material.NETHERITE_INGOT, ingots));
        Msg.title(p, "&6&lVICTORY", "&7+" + levels + " levels  +" + ingots + " netherite ingots", 200, 4000, 800);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Msg.tell(p, "&aYou received &e" + levels + " XP levels &aand &e" + ingots + " netherite ingots&a!");

        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() < c.getDouble("rewards.ancient-city.chance", 0.35)) {
            rollLoot(p, "chests/ancient_city", c.getInt("rewards.ancient-city.rolls", 2));
            Msg.tell(p, "&5&lBONUS! &7Ancient City treasure x" + c.getInt("rewards.ancient-city.rolls", 2));
        }
        if (r.nextDouble() < c.getDouble("rewards.end-city.chance", 0.20)) {
            rollLoot(p, "chests/end_city_treasure", c.getInt("rewards.end-city.rolls", 1));
            Msg.tell(p, "&d&lBONUS! &7End City treasure x" + c.getInt("rewards.end-city.rolls", 1));
        }
    }

    private void rollLoot(Player p, String path, int rolls) {
        LootTable table = Bukkit.getLootTable(NamespacedKey.minecraft(path));
        if (table == null) { plugin.getLogger().warning("Loot table not found: " + path); return; }
        for (int i = 0; i < rolls; i++) {
            LootContext ctx = new LootContext.Builder(p.getLocation()).build();
            Collection<ItemStack> loot = table.populateLoot(ThreadLocalRandom.current(), ctx);
            for (ItemStack s : loot) give(p, s);
        }
    }

    private void give(Player p, ItemStack s) {
        for (ItemStack left : p.getInventory().addItem(s).values())
            p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    // ------------------------------------------------------------------ admin / shutdown

    public void stopAll() {
        for (UUID id : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(id);
            players.remove(id);
            if (p != null) { cleanupPlayer(p); Msg.tell(p, "&cThe arena was shut down by an admin."); }
        }
        reset();
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) cleanupPlayer(p);
        }
        players.clear();
        reset();
    }

    private void reset() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        for (BukkitTask t : claimTimers.values()) t.cancel();
        claimTimers.clear();
        plugin.explorer().stop();
        claiming.clear();
        caughtCount.clear();
        opened.clear();
        state = State.IDLE;
    }
}
