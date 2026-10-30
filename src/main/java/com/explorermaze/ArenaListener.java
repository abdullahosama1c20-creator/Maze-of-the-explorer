package com.explorermaze;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class ArenaListener implements Listener {
    private final ExplorerMazePlugin plugin;

    public ArenaListener(ExplorerMazePlugin plugin) { this.plugin = plugin; }

    private MatchManager m() { return plugin.match(); }

    private boolean protectedWorld(Player p) {
        return p.getWorld().equals(plugin.arenaWorld().world())
                && !(p.getGameMode() == GameMode.CREATIVE && p.hasPermission("explorermaze.admin"));
    }

    // ---- damage: no death screen. A lethal hit eliminates the player cleanly.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !m().isInArena(p)) return;
        if (!m().isRunning() || m().isClaiming(p)) { e.setCancelled(true); return; }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            m().eliminate(p, "&7The maze claimed you.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplorerHit(EntityDamageByEntityEvent e) {
        if (plugin.explorer().entity() != null && e.getDamager().equals(plugin.explorer().entity())) e.setCancelled(true);
    }

    // ---- no natural healing. Potions (MAGIC / MAGIC_REGEN) still work.
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p) || !m().isInArena(p)) return;
        switch (e.getRegainReason()) {
            case SATIATED, REGEN, EATING -> e.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && m().isInArena(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (protectedWorld(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (protectedWorld(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { if (m().isInArena(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getLocation().getWorld().equals(plugin.arenaWorld().world())
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!m().isInArena(p) || p.hasPermission("explorermaze.admin")) return;
        String msg = e.getMessage().toLowerCase(Locale.ROOT);
        if (msg.startsWith("/arena") || msg.startsWith("/explorer")) return;
        e.setCancelled(true);
        Msg.tell(p, "&cYou can't use commands in the arena. Use &e/arena leave&c to quit.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!m().isInArena(p)) return;
        Action a = e.getAction();

        // enchanting table in the reward room
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.ENCHANTING_TABLE && m().isClaiming(p)) {
            e.setCancelled(true);
            m().claim(p);
            return;
        }
        // exit door
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && m().isRunning()
                && e.getClickedBlock().getType() == Material.DARK_OAK_DOOR
                && plugin.arenaWorld().isExitDoor(e.getClickedBlock())) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 0.6f);
            m().win(p);
            return;
        }
        // custom items
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            ItemFactory.Kind kind = plugin.items().kindOf(hand);
            if (kind != null && m().isRunning() && !m().isClaiming(p)) {
                e.setCancelled(true);
                hand.setAmount(hand.getAmount() - 1);
                plugin.items().use(p, kind);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { m().leave(e.getPlayer()); }

    /** Crash recovery: if a snapshot is still on disk and the player is not in a match, give it back. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.store().has(p.getUniqueId()) && !m().isInArena(p)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && plugin.store().restore(p)) Msg.tell(p, "&aYour items were restored.");
            }, 10L);
        }
    }
}
