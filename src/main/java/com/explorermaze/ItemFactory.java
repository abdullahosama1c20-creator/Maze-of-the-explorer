package com.explorermaze;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

/** The helpful items found in maze chests, and what they do when used. */
public final class ItemFactory {
    public enum Kind { SLOWER, CLOAK, WHISPER }

    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    private final ExplorerMazePlugin plugin;
    private final NamespacedKey key;

    public ItemFactory(ExplorerMazePlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "kind");
    }

    // ------------------------------------------------------------------ item builders

    public ItemStack regenPot() {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.displayName(Msg.item("&dRegen Pot"));
        m.lore(List.of(Msg.item("&7Regeneration III for 12 seconds."), Msg.item("&8Your only way to heal.")));
        m.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 2), true);
        m.setColor(Color.FUCHSIA);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack speedPot() {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.displayName(Msg.item("&bSwiftness Draught"));
        m.lore(List.of(Msg.item("&7Speed II for 20 seconds.")));
        m.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 1), true);
        m.setColor(Color.AQUA);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack custom(Material mat, Kind kind, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item(name));
        m.lore(java.util.Arrays.stream(lore).map(Msg::item).toList());
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind.name());
        it.setItemMeta(m);
        return it;
    }

    public ItemStack explorerSlower() {
        return custom(Material.ENDER_EYE, Kind.SLOWER, "&3Explorer Slower",
                "&7Right-click: the Explorer is slowed for 12 seconds.");
    }

    public ItemStack shadowCloak() {
        return custom(Material.PHANTOM_MEMBRANE, Kind.CLOAK, "&8Shadow Cloak",
                "&7Right-click: the Explorer cannot see you for 10 seconds.");
    }

    public ItemStack exitWhisper() {
        return custom(Material.RECOVERY_COMPASS, Kind.WHISPER, "&6Exit Whisper",
                "&7Right-click: an arrow points toward the exit for 10 seconds.");
    }

    public Kind kindOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String s = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s == null) return null;
        try { return Kind.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    // ------------------------------------------------------------------ chest loot

    public void fillChest(Inventory inv, Random r) {
        int count = 2 + r.nextInt(3);
        for (int i = 0; i < count; i++) {
            ItemStack it;
            int w = r.nextInt(100);
            if (w < 28) it = regenPot();
            else if (w < 54) it = explorerSlower();
            else if (w < 72) it = speedPot();
            else if (w < 94) it = shadowCloak();
            else it = exitWhisper();
            inv.setItem(r.nextInt(inv.getSize()), it);
        }
    }

    // ------------------------------------------------------------------ using items

    public void use(Player p, Kind kind) {
        ExplorerManager ex = plugin.explorer();
        switch (kind) {
            case SLOWER -> {
                ex.slow(12);
                Msg.tell(p, "&3The Explorer's steps grow heavy... &7(12s)");
                p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.7f);
            }
            case CLOAK -> {
                ex.cloak(p, 10);
                Msg.tell(p, "&8Shadows hide you from the Explorer. &7(10s)");
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1f, 0.6f);
            }
            case WHISPER -> {
                Msg.tell(p, "&6The exit whispers...");
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
                final int[] left = {40};
                new BukkitRunnable() {
                    @Override public void run() {
                        if (left[0]-- <= 0 || !p.isOnline() || !plugin.match().isInArena(p)) { cancel(); return; }
                        org.bukkit.Location door = plugin.arenaWorld().maze().doorLocation(p.getWorld());
                        double dx = door.getX() - p.getLocation().getX(), dz = door.getZ() - p.getLocation().getZ();
                        double target = Math.toDegrees(Math.atan2(-dx, dz));
                        double rel = target - p.getLocation().getYaw();
                        while (rel > 180) rel -= 360;
                        while (rel < -180) rel += 360;
                        int idx = (Math.round((float) (rel / 45.0)) + 8) % 8;
                        Msg.bar(p, "&6Exit " + ARROWS[idx]);
                    }
                }.runTaskTimer(plugin, 0L, 5L);
            }
        }
        plugin.advancements().grant(p, "item");
    }
}
