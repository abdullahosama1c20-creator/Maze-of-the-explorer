package com.explorermaze;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/** Owns the void arena world: the spawn arena, the maze, and the reward room. */
public final class ArenaWorld {
    static final int LOBBY_Y = 150;
    static final int MAZE_X0 = 2000, MAZE_Z0 = 2000, MAZE_FLOOR_Y = 64;
    static final int ROOM_X = 4000, ROOM_Y = 80, ROOM_Z = 4000;

    private final ExplorerMazePlugin plugin;
    private final Random rng = new Random();
    private World world;
    private Maze maze;
    private volatile boolean ready;
    private BukkitTask buildTask;

    public ArenaWorld(ExplorerMazePlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ setup

    public void init() {
        String name = plugin.getConfig().getString("arena.world-name", "explorer_arena");
        world = Bukkit.getWorld(name);
        if (world == null) {
            WorldCreator wc = new WorldCreator(name);
            wc.generator(new VoidGenerator());
            wc.generateStructures(false);
            wc.environment(World.Environment.NORMAL);
            world = wc.createWorld();
        }
        if (world == null) throw new IllegalStateException("Could not create arena world '" + name + "'");
        world.setSpawnLocation(0, LOBBY_Y + 1, 0);
        if (world.getBlockAt(0, LOBBY_Y - 1, 0).getType() != Material.SEA_LANTERN) buildLobby();
        if (world.getBlockAt(ROOM_X, ROOM_Y, ROOM_Z).getType() != Material.ENCHANTING_TABLE) buildRewardRoom();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> world.setTime(18000L), 20L, 200L);
    }

    public World world() { return world; }
    public Maze maze() { return maze; }
    public boolean isMazeReady() { return ready && maze != null; }

    public Location lobbySpawn() {
        double a = rng.nextDouble() * Math.PI * 2, r = 8 + rng.nextDouble() * 3;
        double x = 0.5 + Math.cos(a) * r, z = 0.5 + Math.sin(a) * r;
        float yaw = (float) Math.toDegrees(Math.atan2(-(0.5 - x), 0.5 - z));
        return new Location(world, x, LOBBY_Y, z, yaw, 10f);
    }

    public Location mazeStart() { return maze.start(world); }

    public Location roomSpawn() { return new Location(world, ROOM_X + 0.5, ROOM_Y, ROOM_Z + 3.5, 180f, 15f); }

    public Location roomTable() { return new Location(world, ROOM_X + 0.5, ROOM_Y + 1.2, ROOM_Z + 0.5); }

    public boolean isExitDoor(Block b) {
        return maze != null && b.getWorld().equals(world) && maze.isExitDoor(b.getX(), b.getY(), b.getZ());
    }

    private void set(int x, int y, int z, Material m) { world.getBlockAt(x, y, z).setType(m, false); }

    // ------------------------------------------------------------------ spawn arena

    private Material floorMat(double d, int dx, int dz) {
        boolean spoke = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
        if (d < 3.5) return Material.SEA_LANTERN;
        if (d < 5) return Material.AMETHYST_BLOCK;
        if (spoke && d < 20) return Material.CHISELED_POLISHED_BLACKSTONE;
        if (d < 13) return ((dx + dz) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.POLISHED_BLACKSTONE;
        if (d < 14) return Material.CRYING_OBSIDIAN;
        if (d < 19) return ((dx + dz) & 1) == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE_BRICKS;
        if (d < 20) return Material.AMETHYST_BLOCK;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private void buildLobby() {
        final int R = 24, y0 = LOBBY_Y;
        for (int dx = -R - 2; dx <= R + 2; dx++) {
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 0.5) continue;
                set(dx, y0 - 1, dz, floorMat(d, dx, dz));
                set(dx, y0 - 2, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                set(dx, y0 - 3, dz, Material.BLACKSTONE);
                if (d > R - 1.5) {
                    for (int y = y0; y <= y0 + 3; y++)
                        set(dx, y, dz, y == y0 + 3 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        // glass dome with blackstone ribs
        double rd = R + 0.5;
        for (int dx = -R - 2; dx <= R + 2; dx++) {
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                for (int dy = 4; dy <= R + 2; dy++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist < rd - 0.9 || dist > rd + 0.4) continue;
                    boolean rib = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
                    set(dx, y0 + dy, dz, rib ? Material.CHISELED_POLISHED_BLACKSTONE : Material.PURPLE_STAINED_GLASS);
                }
            }
        }
        // eight pillars topped with sea lanterns
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4;
            int px = (int) Math.round(Math.cos(ang) * 18), pz = (int) Math.round(Math.sin(ang) * 18);
            for (int y = 0; y <= 14; y++) {
                Material m = y == 0 ? Material.POLISHED_BLACKSTONE_BRICKS
                        : (y % 4 == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                set(px, y0 + y, pz, m);
            }
            set(px, y0 + 15, pz, Material.SEA_LANTERN);
            set(px, y0 + 16, pz, Material.AMETHYST_BLOCK);
            // braziers between the pillars
            double a2 = ang + Math.PI / 8;
            int bx = (int) Math.round(Math.cos(a2) * 12), bz = (int) Math.round(Math.sin(a2) * 12);
            set(bx, y0, bz, Material.SOUL_CAMPFIRE);
        }
        // central dais + beam of end rods
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 4.5) set(dx, y0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
            if (d < 2.5) set(dx, y0 + 1, dz, Material.AMETHYST_BLOCK);
        }
        set(0, y0 + 2, 0, Material.SEA_LANTERN);
        for (int y = 3; y <= 10; y++) set(0, y0 + y, 0, Material.END_ROD);
        plugin.getLogger().info("Spawn arena built.");
    }

    // ------------------------------------------------------------------ reward room

    private void buildRewardRoom() {
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int dy = -1; dy <= 6; dy++) {
            boolean shell = Math.abs(dx) == 5 || Math.abs(dz) == 5 || dy == -1 || dy == 6;
            set(ROOM_X + dx, ROOM_Y + dy, ROOM_Z + dz, shell ? Material.BLACK_CONCRETE : Material.AIR);
        }
        set(ROOM_X, ROOM_Y, ROOM_Z, Material.ENCHANTING_TABLE);
        Light l = (Light) Material.LIGHT.createBlockData();
        l.setLevel(4);
        world.getBlockAt(ROOM_X, ROOM_Y + 3, ROOM_Z).setBlockData(l, false);
    }

    // ------------------------------------------------------------------ maze

    private Material wallMat(int x, int y, int z) {
        int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 100);
        return h < 78 ? Material.BEDROCK : Material.SCULK;
    }

    private Material floorMat(int x, int z) {
        int h = Math.floorMod(x * 668265263 ^ z * 374761393, 100);
        return h < 70 ? Material.SCULK : Material.BEDROCK;
    }

    /** (Re)generates a brand new maze, building it a few thousand blocks per tick. */
    public void buildMaze() {
        if (buildTask != null) buildTask.cancel();
        ready = false;
        FileConfiguration c = plugin.getConfig();
        final Maze m = new Maze(c.getInt("maze.cells", 45), c.getInt("maze.path-width", 3),
                c.getInt("maze.wall-height", 7), MAZE_X0, MAZE_Z0, MAZE_FLOOR_Y,
                c.getDouble("maze.straightness", 0.88), System.nanoTime());
        maze = m;
        for (int cx = MAZE_X0 >> 4; cx <= (MAZE_X0 + m.size) >> 4; cx++)
            for (int cz = MAZE_Z0 >> 4; cz <= (MAZE_Z0 + m.size) >> 4; cz++)
                world.addPluginChunkTicket(cx, cz, plugin);

        final int perTick = Math.max(500, c.getInt("maze.blocks-per-tick", 3000));
        buildTask = new BukkitRunnable() {
            int idx = 0;
            final int total = m.size * m.size;

            @Override public void run() {
                int budget = perTick;
                while (idx < total && budget > 0) {
                    int x = idx / m.size, z = idx % m.size;
                    int bx = m.x0 + x, bz = m.z0 + z;
                    set(bx, m.floorY - 1, bz, floorMat(x, z));
                    set(bx, m.floorY + m.height, bz, Material.BEDROCK);
                    for (int y = m.floorY; y < m.floorY + m.height; y++)
                        set(bx, y, bz, m.wall[x][z] ? wallMat(bx, y, bz) : Material.AIR);
                    budget -= m.height + 2;
                    idx++;
                }
                if (idx >= total) {
                    cancel();
                    decorate(m);
                    ready = true;
                    plugin.getLogger().info("Maze ready (" + m.n + "x" + m.n + " cells).");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void decorate(Maze m) {
        FileConfiguration c = plugin.getConfig();
        double chestDead = c.getDouble("maze.chest-chance-dead-end", 0.40);
        double chestOther = c.getDouble("maze.chest-chance-other", 0.02);
        double lantern = c.getDouble("maze.lantern-chance", 0.03);
        BlockFaceHelper faces = new BlockFaceHelper();

        for (int cx = 0; cx < m.n; cx++) {
            for (int cz = 0; cz < m.n; cz++) {
                Location ctr = m.center(world, cx, cz);
                int bx = ctr.getBlockX(), bz = ctr.getBlockZ();
                boolean special = m.isStartOrExit(cx, cz);
                if (special || rng.nextDouble() < lantern) {
                    Lantern ld = (Lantern) Material.SOUL_LANTERN.createBlockData();
                    ld.setHanging(true);
                    world.getBlockAt(bx, m.floorY + m.height - 1, bz).setBlockData(ld, false);
                }
                if (special) continue;
                double chance = m.openings(cx, cz) == 1 ? chestDead : chestOther;
                if (rng.nextDouble() < chance) {
                    Block b = world.getBlockAt(bx, m.floorY, bz);
                    org.bukkit.block.data.type.Chest cd =
                            (org.bukkit.block.data.type.Chest) Material.CHEST.createBlockData();
                    cd.setFacing(faces.random(rng));
                    b.setBlockData(cd, false);
                    BlockState st = b.getState();
                    if (st instanceof org.bukkit.block.Chest chest) {
                        plugin.items().fillChest(chest.getBlockInventory(), rng);
                    }
                }
            }
        }
        // the exit: a dark oak door set into the outer wall
        Door d = (Door) Material.DARK_OAK_DOOR.createBlockData();
        d.setFacing(m.doorFacing);
        d.setOpen(false);
        d.setHalf(Bisected.Half.BOTTOM);
        world.getBlockAt(m.x0 + m.doorX, m.floorY, m.z0 + m.doorZ).setBlockData(d, false);
        d.setHalf(Bisected.Half.TOP);
        world.getBlockAt(m.x0 + m.doorX, m.floorY + 1, m.z0 + m.doorZ).setBlockData(d, false);
    }

    /** Tiny helper so the chest facing stays in one place. */
    private static final class BlockFaceHelper {
        private static final org.bukkit.block.BlockFace[] F = {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST};
        org.bukkit.block.BlockFace random(Random r) { return F[r.nextInt(4)]; }
    }

    public void shutdown() { if (buildTask != null) buildTask.cancel(); }
}
