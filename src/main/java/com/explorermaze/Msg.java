package com.explorermaze;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Small text helpers. Uses '&' colour codes. */
public final class Msg {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final String PREFIX = "&5[&dExplorer&5] &7";

    private Msg() {}

    public static Component c(String s) { return LEGACY.deserialize(s); }

    public static Component item(String s) { return c(s).decoration(TextDecoration.ITALIC, false); }

    public static void tell(Player p, String s) { p.sendMessage(c(PREFIX + s)); }

    public static void bar(Player p, String s) { p.sendActionBar(c(s)); }

    public static void title(Player p, String title, String sub, int inMs, int stayMs, int outMs) {
        p.showTitle(Title.title(c(title), c(sub),
                Title.Times.times(Duration.ofMillis(inMs), Duration.ofMillis(stayMs), Duration.ofMillis(outMs))));
    }
}
