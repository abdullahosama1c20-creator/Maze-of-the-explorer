package com.explorermaze;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * How the Explorer looks. The default is the re-textured wither skeleton from the resource pack.
 * When the BetterModel plugin is installed and the model file is loaded, {@link BetterModelSkin}
 * puts the Blockbench model (with its animations) on top of the invisible wither skeleton.
 * The wither skeleton keeps doing the pathfinding, the line of sight and the catching.
 */
public interface ExplorerSkin {
    /** Shows the skin on the freshly spawned Explorer. Returns false when the plain wither skeleton is used. */
    boolean attach(LivingEntity explorer);

    /** Removes the skin (called before the entity is removed). */
    void detach();

    /** He saw someone and sprints after them (true), or is back to his slow patrol (false). */
    void chase(boolean chasing);

    /** He just spotted a player: play the "sees and hits" animation once. */
    void seesPlayer();

    /** Explorer Tracker item: make him glow through walls for a few seconds. */
    void reveal(int seconds);

    /** Lines for /arena model. */
    List<String> status();

    /** Picks the skin for this server: the BetterModel one when possible, otherwise the plain fallback. */
    static ExplorerSkin create(ExplorerMazePlugin plugin) {
        if (!plugin.getConfig().getBoolean("explorer.model.enabled", true))
            return new Fallback("disabled in config.yml, explorer.model.enabled");
        if (!Bukkit.getPluginManager().isPluginEnabled("BetterModel"))
            return new Fallback("the BetterModel plugin is not installed");
        try {
            // only touch BetterModel's classes when the plugin is really there
            Class.forName("kr.toxicity.model.api.BetterModel", false, ExplorerSkin.class.getClassLoader());
            return new BetterModelSkin(plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("BetterModel is installed but its API could not be used: " + t);
            return new Fallback("BetterModel API not usable: " + t.getClass().getSimpleName());
        }
    }

    /** The plain re-textured wither skeleton. */
    final class Fallback implements ExplorerSkin {
        private final String reason;

        Fallback(String reason) { this.reason = reason; }

        @Override public boolean attach(LivingEntity explorer) { return false; }
        @Override public void detach() { }
        @Override public void chase(boolean chasing) { }
        @Override public void seesPlayer() { }
        @Override public void reveal(int seconds) { }
        @Override public List<String> status() {
            return List.of("&7Skin: &ere-textured wither skeleton &8(" + reason + ")");
        }
    }
}
