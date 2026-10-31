package com.explorermaze;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityHideOption;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The Explorer as a Blockbench model (BetterModel plugin, API 3.6) riding on the invisible wither skeleton.
 * Only created by {@link ExplorerSkin#create} when BetterModel is enabled, so these classes are never loaded otherwise.
 * <ul>
 *   <li>{@code idle} / {@code walk}: played by BetterModel itself when the model has them.</li>
 *   <li>{@code run} (config {@code explorer.model.run-animation}): loops while he chases someone.</li>
 *   <li>{@code sees_and_hits} (config {@code explorer.model.sees-animation}): once, when he spots a player.</li>
 * </ul>
 */
final class BetterModelSkin implements ExplorerSkin {
    private static final int GLOW_RED = 0xC00000;

    private final ExplorerMazePlugin plugin;
    private EntityTracker tracker;
    private BukkitTask glowTask;
    private boolean running;        // the run loop should be playing
    private boolean seesPlaying;    // the one-shot "sees and hits" animation is playing

    BetterModelSkin(ExplorerMazePlugin plugin) { this.plugin = plugin; }

    private String modelName() { return plugin.getConfig().getString("explorer.model.name", "the_explorer"); }
    private String runAnim() { return plugin.getConfig().getString("explorer.model.run-animation", "run"); }
    private String seesAnim() { return plugin.getConfig().getString("explorer.model.sees-animation", "sees_and_hits"); }
    private boolean live() { return tracker != null && !tracker.isClosed(); }

    @Override
    public boolean attach(LivingEntity explorer) {
        detach();
        ModelRenderer renderer = BetterModel.modelOrNull(modelName());
        if (renderer == null) {
            plugin.getLogger().warning("BetterModel has no model named '" + modelName() + "'. Put " + modelName()
                    + ".bbmodel into plugins/BetterModel/models/ and run /bettermodel reload. Using the wither skeleton look.");
            return false;
        }
        try {
            tracker = renderer.getOrCreate(BukkitAdapter.adapt((Entity) explorer));
            tracker.hideOption(EntityHideOption.DEFAULT);      // the wither skeleton underneath stays invisible
            tracker.scaler(ModelScaler.value((float) plugin.getConfig().getDouble("explorer.model.scale", 1.0)));
            running = false;
            seesPlaying = false;
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not put the model on the Explorer: " + t);
            tracker = null;
            return false;
        }
    }

    @Override
    public void detach() {
        if (glowTask != null) { glowTask.cancel(); glowTask = null; }
        if (tracker != null) {
            try { tracker.close(); } catch (Throwable ignored) { }
            tracker = null;
        }
        running = false;
        seesPlaying = false;
    }

    @Override
    public void chase(boolean chasing) {
        if (chasing == running) return;
        running = chasing;
        if (!live()) return;
        if (chasing) {
            if (!seesPlaying) startRun();      // otherwise the run starts when "sees and hits" has finished
        } else {
            tracker.stopAnimation(runAnim());
        }
    }

    private void startRun() {
        tracker.animate(runAnim(), AnimationModifier.builder().type(AnimationIterator.Type.LOOP).build());
    }

    @Override
    public void seesPlayer() {
        if (!live()) return;
        tracker.stopAnimation(runAnim());
        seesPlaying = true;
        boolean started = tracker.animate(seesAnim(), AnimationModifier.DEFAULT_WITH_PLAY_ONCE, () -> {
            // called when the animation ends, on BetterModel's thread: hop back to the server thread
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    seesPlaying = false;
                    if (running && live()) startRun();
                });
            } catch (RuntimeException ignored) {    // plugin already disabled
                seesPlaying = false;
            }
        });
        if (!started) {                              // animation name not in the model
            seesPlaying = false;
            if (running) startRun();
        }
    }

    @Override
    public void reveal(int seconds) {
        if (!live()) return;
        tracker.update(TrackerUpdateAction.glowColor(GLOW_RED));
        tracker.update(TrackerUpdateAction.glow(true));
        if (glowTask != null) glowTask.cancel();
        glowTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            glowTask = null;
            if (live()) tracker.update(TrackerUpdateAction.glow(false));
        }, seconds * 20L);
    }

    @Override
    public List<String> status() {
        List<String> out = new ArrayList<>();
        ModelRenderer renderer = BetterModel.modelOrNull(modelName());
        if (renderer == null) {
            out.add("&7Skin: &cBetterModel model '&e" + modelName() + "&c' is not loaded. &7Loaded models: &e"
                    + (BetterModel.modelKeys().isEmpty() ? "none" : String.join(", ", BetterModel.modelKeys())));
            out.add("&7Put &e" + modelName() + ".bbmodel &7into &eplugins/BetterModel/models/ &7and run &e/bettermodel reload&7.");
            return out;
        }
        out.add("&7Skin: &aBetterModel model '&e" + modelName() + "&a' &7(scale "
                + plugin.getConfig().getDouble("explorer.model.scale", 1.0) + ")");
        out.add("&7Animations in the file: &e" + String.join(", ", renderer.animations().keySet()));
        for (String a : List.of("idle", "walk", runAnim(), seesAnim()))
            out.add((renderer.animation(a).isPresent() ? "&a  found: &e" : "&c  missing: &e") + a);
        out.add("&7Model on the Explorer right now: &e" + (live() ? "yes" : "no"));
        return out;
    }
}
