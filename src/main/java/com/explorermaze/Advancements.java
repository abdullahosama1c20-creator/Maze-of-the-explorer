package com.explorermaze;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.ArrayList;

/** Grants advancements from the optional datapack. Silently does nothing if the datapack is missing. */
public final class Advancements {
    public void grant(Player p, String id) {
        Advancement a = Bukkit.getAdvancement(new NamespacedKey("explorermaze", id));
        if (a == null) return;
        AdvancementProgress ap = p.getAdvancementProgress(a);
        if (ap.isDone()) return;
        for (String crit : new ArrayList<>(ap.getRemainingCriteria())) ap.awardCriteria(crit);
    }
}
