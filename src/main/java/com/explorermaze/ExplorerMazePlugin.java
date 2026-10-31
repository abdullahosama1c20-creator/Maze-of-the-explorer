package com.explorermaze;

import org.bukkit.plugin.java.JavaPlugin;

public final class ExplorerMazePlugin extends JavaPlugin {
    private InventoryStore store;
    private ItemFactory items;
    private Advancements advancements;
    private PackManager packs;
    private ArenaWorld arenaWorld;
    private ExplorerManager explorer;
    private MatchManager match;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new InventoryStore(this);
        items = new ItemFactory(this);
        advancements = new Advancements();
        packs = new PackManager(this);
        arenaWorld = new ArenaWorld(this);
        explorer = new ExplorerManager(this);
        match = new MatchManager(this);

        arenaWorld.init();

        getServer().getPluginManager().registerEvents(packs, this);
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
        ArenaCommand cmd = new ArenaCommand(this);
        getCommand("arena").setExecutor(cmd);
        getCommand("arena").setTabCompleter(cmd);

        String url = getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank())
            getLogger().warning("resource-pack.url is empty - players will NOT be asked to download the texture pack!");
        getLogger().info("ExplorerMaze enabled.");
    }

    @Override
    public void onDisable() {
        if (match != null) match.shutdown();
        if (explorer != null) explorer.stop();
        if (arenaWorld != null) arenaWorld.shutdown();
    }

    public InventoryStore store() { return store; }
    public ItemFactory items() { return items; }
    public Advancements advancements() { return advancements; }
    public PackManager packs() { return packs; }
    public ArenaWorld arenaWorld() { return arenaWorld; }
    public ExplorerManager explorer() { return explorer; }
    public MatchManager match() { return match; }
}
