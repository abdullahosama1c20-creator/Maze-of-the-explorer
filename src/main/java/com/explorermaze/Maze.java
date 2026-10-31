package com.explorermaze;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Pure-data maze. Every cell is {@code pw} x {@code pw} blocks and is separated by 1-block walls.
 * Generation is a recursive backtracker biased to keep going straight, which gives very long walls
 * and endless-feeling halls.
 */
public final class Maze {
    private static final int[] DX = {0, 1, 0, -1};   // N E S W
    private static final int[] DZ = {-1, 0, 1, 0};

    public final int n, pw, size, height, x0, z0, floorY;
    /** wall[x][z] == true means the whole column is solid. Coordinates are relative to (x0, z0). */
    public final boolean[][] wall;
    private final boolean[][][] pass;
    private final Random rng;

    public int exitCx, exitCz;
    public BlockFace doorFacing;
    public int doorX, doorZ;          // relative block coordinates of the door column
    private int[][] distStart, distExit;

    public Maze(int n, int pw, int height, int x0, int z0, int floorY, double straight, long seed) {
        this.n = n; this.pw = pw; this.height = height;
        this.x0 = x0; this.z0 = z0; this.floorY = floorY;
        this.size = n * (pw + 1) + 1;
        this.rng = new Random(seed);
        this.wall = new boolean[size][size];
        for (boolean[] col : wall) Arrays.fill(col, true);
        this.pass = new boolean[n][n][4];
        generate(straight);
        pickExit();
    }

    public int base(int c) { return 1 + c * (pw + 1); }

    private void generate(double straight) {
        boolean[][] vis = new boolean[n][n];
        Deque<int[]> st = new ArrayDeque<>();
        st.push(new int[]{0, 0, -1});
        vis[0][0] = true;
        while (!st.isEmpty()) {
            int[] c = st.peek();
            List<Integer> opts = new ArrayList<>(4);
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DX[d], nz = c[1] + DZ[d];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !vis[nx][nz]) opts.add(d);
            }
            if (opts.isEmpty()) { st.pop(); continue; }
            int d;
            if (c[2] >= 0 && opts.contains(c[2]) && rng.nextDouble() < straight) d = c[2];
            else d = opts.get(rng.nextInt(opts.size()));
            int nx = c[0] + DX[d], nz = c[1] + DZ[d];
            pass[c[0]][c[1]][d] = true;
            pass[nx][nz][(d + 2) % 4] = true;
            vis[nx][nz] = true;
            st.push(new int[]{nx, nz, d});
        }
        for (int cx = 0; cx < n; cx++) {
            for (int cz = 0; cz < n; cz++) {
                int bx = base(cx), bz = base(cz);
                for (int i = 0; i < pw; i++) for (int j = 0; j < pw; j++) wall[bx + i][bz + j] = false;
                if (pass[cx][cz][1]) for (int j = 0; j < pw; j++) wall[bx + pw][bz + j] = false;
                if (pass[cx][cz][2]) for (int i = 0; i < pw; i++) wall[bx + i][bz + pw] = false;
            }
        }
    }

    private int[][] bfs(int sx, int sz) {
        int[][] dist = new int[n][n];
        for (int[] r : dist) Arrays.fill(r, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[sx][sz] = 0;
        q.add(new int[]{sx, sz});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                if (!pass[c[0]][c[1]][d]) continue;
                int nx = c[0] + DX[d], nz = c[1] + DZ[d];
                if (dist[nx][nz] < 0) { dist[nx][nz] = dist[c[0]][c[1]] + 1; q.add(new int[]{nx, nz}); }
            }
        }
        return dist;
    }

    private void pickExit() {
        distStart = bfs(0, 0);
        int best = -1;
        for (int cx = 0; cx < n; cx++) {
            for (int cz = 0; cz < n; cz++) {
                boolean border = cx == 0 || cz == 0 || cx == n - 1 || cz == n - 1;
                if (!border || (cx == 0 && cz == 0)) continue;
                if (distStart[cx][cz] > best) { best = distStart[cx][cz]; exitCx = cx; exitCz = cz; }
            }
        }
        if (exitCz == 0)          { doorX = base(exitCx) + pw / 2; doorZ = 0;        doorFacing = BlockFace.SOUTH; }
        else if (exitCz == n - 1) { doorX = base(exitCx) + pw / 2; doorZ = size - 1; doorFacing = BlockFace.NORTH; }
        else if (exitCx == 0)     { doorX = 0;        doorZ = base(exitCz) + pw / 2; doorFacing = BlockFace.EAST; }
        else                      { doorX = size - 1; doorZ = base(exitCz) + pw / 2; doorFacing = BlockFace.WEST; }
        distExit = bfs(exitCx, exitCz);
    }

    // ------------------------------------------------------------------ queries

    public Location center(World w, int cx, int cz) {
        return new Location(w, x0 + base(cx) + pw / 2.0, floorY, z0 + base(cz) + pw / 2.0);
    }

    public Location start(World w) { return center(w, 0, 0); }

    public Location doorLocation(World w) { return new Location(w, x0 + doorX, floorY, z0 + doorZ); }

    public boolean isExitDoor(int x, int y, int z) {
        return x == x0 + doorX && z == z0 + doorZ && (y == floorY || y == floorY + 1);
    }

    public int openings(int cx, int cz) {
        int k = 0;
        for (int d = 0; d < 4; d++) if (pass[cx][cz][d]) k++;
        return k;
    }

    public boolean isStartOrExit(int cx, int cz) {
        return (cx == 0 && cz == 0) || (cx == exitCx && cz == exitCz);
    }

    private Location randomCell(World w) {
        return center(w, rng.nextInt(n), rng.nextInt(n));
    }

    /** A random spot well away from the start where the Explorer spawns. */
    public Location explorerSpawn(World w) {
        for (int i = 0; i < 200; i++) {
            int cx = rng.nextInt(n), cz = rng.nextInt(n);
            if (distStart[cx][cz] >= 10 && distStart[cx][cz] <= 18) return center(w, cx, cz);
        }
        return randomCell(w);
    }

    /** One of the cells in the furthest ~12% (by path length) from the exit. */
    public Location farFromExit(World w, Location avoid) {
        List<int[]> list = new ArrayList<>();
        for (int cx = 0; cx < n; cx++) for (int cz = 0; cz < n; cz++) list.add(new int[]{cx, cz, distExit[cx][cz]});
        list.sort((a, b) -> Integer.compare(b[2], a[2]));
        int top = Math.max(5, (int) (list.size() * 0.12));
        Location pick = null;
        for (int i = 0; i < 12; i++) {
            int[] c = list.get(rng.nextInt(top));
            pick = center(w, c[0], c[1]);
            if (avoid == null || avoid.getWorld() != w || pick.distanceSquared(avoid) > 40 * 40) break;
        }
        return pick;
    }

    /** A random cell between 20 and maxDist blocks away from {@code from} (used for patrolling). */
    public Location randomCellNear(World w, Location from, double maxDist) {
        for (int i = 0; i < 60; i++) {
            Location c = randomCell(w);
            double d = c.distance(from);
            if (d >= 20 && d <= maxDist) return c;
        }
        return randomCell(w);
    }

    /** A random cell whose horizontal distance to {@code c} is between min and max blocks. */
    public Location randomCellRing(World w, Location c, double min, double max) {
        for (int i = 0; i < 80; i++) {
            Location cell = randomCell(w);
            double dx = cell.getX() - c.getX(), dz = cell.getZ() - c.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= min && d <= max) return cell;
        }
        return randomCell(w);
    }

    /** A random cell at least minDist blocks from {@code avoid}. */
    public Location randomCellAwayFrom(World w, Location avoid, double minDist) {
        for (int i = 0; i < 60; i++) {
            Location c = randomCell(w);
            if (c.distanceSquared(avoid) >= minDist * minDist) return c;
        }
        return randomCell(w);
    }
}
