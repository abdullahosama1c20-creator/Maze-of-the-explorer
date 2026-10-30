package com.explorermaze;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Saves a player's whole state (items, xp, health, location...) to disk before they enter the arena
 * and puts it back when they leave. Because it lives on disk it survives crashes and restarts.
 */
public final class InventoryStore {
    private final ExplorerMazePlugin plugin;
    private final File dir;

    public InventoryStore(ExplorerMazePlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "snapshots");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private File file(UUID id) { return new File(dir, id + ".yml"); }

    public boolean has(UUID id) { return file(id).exists(); }

    public void save(Player p) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("contents", new ArrayList<>(Arrays.asList(p.getInventory().getContents())));
        y.set("level", p.getLevel());
        y.set("exp", (double) p.getExp());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("sat", (double) p.getSaturation());
        y.set("gm", p.getGameMode().name());
        y.set("loc", p.getLocation());
        y.set("effects", new ArrayList<>(p.getActivePotionEffects()));
        try {
            y.save(file(p.getUniqueId()));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save snapshot for " + p.getName() + ": " + e.getMessage());
        }
    }

    public boolean restore(Player p) {
        File f = file(p.getUniqueId());
        if (!f.exists()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());

        List<?> list = y.getList("contents");
        ItemStack[] arr = new ItemStack[list == null ? 0 : list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i) instanceof ItemStack is ? is : null;
        p.getInventory().clear();
        if (arr.length <= p.getInventory().getSize()) p.getInventory().setContents(arr);

        p.setLevel(y.getInt("level"));
        p.setExp((float) y.getDouble("exp"));
        p.setFoodLevel(y.getInt("food", 20));
        p.setSaturation((float) y.getDouble("sat", 5.0));
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20.0 : max.getValue();
        p.setHealth(Math.max(1.0, Math.min(y.getDouble("health", 20.0), maxHp)));
        try {
            p.setGameMode(GameMode.valueOf(y.getString("gm", "SURVIVAL")));
        } catch (IllegalArgumentException ex) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        List<?> effects = y.getList("effects");
        if (effects != null) for (Object o : effects) if (o instanceof PotionEffect pe) p.addPotionEffect(pe);

        Location loc = y.getLocation("loc");
        if (loc == null || loc.getWorld() == null) loc = Bukkit.getWorlds().get(0).getSpawnLocation();
        p.teleport(loc);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return true;
    }
}
