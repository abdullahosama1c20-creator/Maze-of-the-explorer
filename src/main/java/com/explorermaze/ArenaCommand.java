package com.explorermaze;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ArenaCommand implements CommandExecutor, TabCompleter {
    private final ExplorerMazePlugin plugin;

    public ArenaCommand(ExplorerMazePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
                if (!p.hasPermission("explorermaze.play")) { Msg.tell(p, "&cNo permission."); return true; }
                plugin.packs().require(p, () -> plugin.match().join(p));
            }
            case "leave" -> {
                if (s instanceof Player p) plugin.match().leave(p);
            }
            case "pack" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
                boolean sent = plugin.packs().send(p);
                Msg.tell(p, sent ? "&7Pack loaded right now: &e" + plugin.packs().isLoaded(p)
                        + "&7. Re-sent the pack - watch the console for its status."
                        : "&cresource-pack.url is empty in config.yml.");
            }
            case "model" -> {
                for (String line : plugin.explorer().skin().status()) s.sendMessage(Msg.c(Msg.PREFIX + line));
            }
            case "status" -> s.sendMessage(Msg.c(Msg.PREFIX + "State: &e" + plugin.match().state()
                    + " &7| players: &e" + plugin.match().playerCount()
                    + " &7| maze ready: &e" + plugin.arenaWorld().isMazeReady()));
            case "forcestart", "stop", "regen", "reload" -> {
                if (!s.hasPermission("explorermaze.admin")) { s.sendMessage(Msg.c(Msg.PREFIX + "&cNo permission.")); return true; }
                switch (sub) {
                    case "forcestart" -> plugin.match().forceStart();
                    case "stop" -> plugin.match().stopAll();
                    case "regen" -> plugin.arenaWorld().buildMaze();
                    default -> { plugin.reloadConfig(); plugin.explorer().reloadSkin(); }
                }
                s.sendMessage(Msg.c(Msg.PREFIX + "&aDone."));
            }
            default -> s.sendMessage(Msg.c(Msg.PREFIX + "Usage: &e/arena [leave|status|pack|model]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("leave"); out.add("status"); out.add("pack"); out.add("model");
            if (s.hasPermission("explorermaze.admin")) { out.add("forcestart"); out.add("stop"); out.add("regen"); out.add("reload"); }
            out.removeIf(x -> !x.startsWith(args[0].toLowerCase(Locale.ROOT)));
        }
        return out;
    }
}
