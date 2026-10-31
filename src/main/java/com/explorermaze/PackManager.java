package com.explorermaze;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Makes sure players have the texture pack loaded before they may enter the arena. */
public final class PackManager implements Listener {
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("explorermaze-resourcepack".getBytes());

    private final ExplorerMazePlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Runnable> pending = new HashMap<>();

    public PackManager(ExplorerMazePlugin plugin) { this.plugin = plugin; }

    public boolean isLoaded(Player p) { return loaded.contains(p.getUniqueId()); }

    /** Sends the pack right now (used by /arena pack and by require()). Returns false if no URL is set. */
    public boolean send(Player p) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return false;
        byte[] hash = null;
        String sha = plugin.getConfig().getString("resource-pack.sha1", "");
        if (sha != null && sha.trim().length() == 40) {
            try { hash = HexFormat.of().parseHex(sha.trim()); } catch (IllegalArgumentException ignored) { }
        } else if (sha != null && !sha.isBlank()) {
            plugin.getLogger().warning("resource-pack.sha1 must be exactly 40 hex characters - ignoring it.");
        }
        p.addResourcePack(PACK_ID, url.trim(), hash,
                Msg.c(plugin.getConfig().getString("resource-pack.prompt", "Texture pack required")), true);
        return true;
    }

    public void require(Player p, Runnable onReady) {
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (!required || loaded.contains(p.getUniqueId())) { onReady.run(); return; }
        if (url == null || url.isBlank()) {
            Msg.tell(p, "&eNo resource pack URL is configured on this server - continuing without it.");
            onReady.run();
            return;
        }
        pending.put(p.getUniqueId(), onReady);
        Msg.tell(p, "&dDownload the texture pack to enter. Accept the prompt!");
        send(p);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        plugin.getLogger().info("Resource pack status for " + p.getName() + ": " + e.getStatus());
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                loaded.add(p.getUniqueId());
                Runnable r = pending.remove(p.getUniqueId());
                if (r != null) r.run();
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                if (pending.remove(p.getUniqueId()) != null)
                    Msg.tell(p, "&cThe texture pack is required to play. Run &e/arena&c again and accept it.");
            }
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
        pending.remove(e.getPlayer().getUniqueId());
    }
}
