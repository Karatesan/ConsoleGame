package com.archon.system.spatial;

import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain service encapsulating geometry, raycasting (line of sight / line of fire),
 * map passability, gas occlusion, and entity occupancy checks.
 */
public final class SpatialService {

    private SpatialService() {}

    public static Entity entityAt(World world, Vec2 p) {
        if (world.thrall() != null && world.thrall().alive() && world.thrall().pos().equals(p)) {
            return world.thrall();
        }
        List<Entity> entities = world.entities();
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e.alive() && e.pos().equals(p)) {
                return e;
            }
        }
        return null;
    }

    public static boolean passable(World world, Vec2 p) {
        GameMap.Tile tile = world.tile(p);
        return tile != null && !tile.isWall() && entityAt(world, p) == null;
    }

    public static List<Entity> hostilesAdjacentTo(World world, Vec2 p) {
        List<Entity> out = new ArrayList<>();
        List<Entity> entities = world.entities();
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e.alive() && e.kind() == Entity.Kind.CREATURE && e.pos().manhattan(p) == 1) {
                out.add(e);
            }
        }
        return out;
    }

    public static boolean hasGas(GameMap.Tile tile) {
        return tile != null && (tile.has(Tag.STEAM) || tile.has(Tag.SUPERHEATED_STEAM) || tile.has(Tag.OIL_MIST));
    }

    public static boolean isOpaqueGas(GameMap.Tile tile) {
        return tile != null && (tile.has(Tag.STEAM) || tile.has(Tag.OIL_MIST));
    }

    public static boolean lineOfSight(GameMap map, Vec2 a, Vec2 b) {
        return lineOfSight(map, a, b, false);
    }

    public static boolean lineOfSight(GameMap map, Vec2 a, Vec2 b, boolean ignoreGas) {
        GameMap.Tile end = map.tile(b);
        if (end == null || end.isWall()) {
            return false;
        }
        int dx = Math.abs(b.x() - a.x());
        int dy = Math.abs(b.y() - a.y());
        int sx = a.x() < b.x() ? 1 : -1;
        int sy = a.y() < b.y() ? 1 : -1;
        int err = dx - dy;
        int x = a.x();
        int y = a.y();

        while (x != b.x() || y != b.y()) {
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
            if (x == b.x() && y == b.y()) break;

            GameMap.Tile tile = map.tile(new Vec2(x, y));
            if (tile == null || tile.isWall() || (!ignoreGas && isOpaqueGas(tile))) {
                return false;
            }
        }

        return true;
    }

    public static boolean lineOfSight(World world, Vec2 a, Vec2 b) {
        return lineOfSight(world.map(), a, b, false);
    }

    public static boolean lineOfSight(World world, Vec2 a, Vec2 b, boolean ignoreGas) {
        return lineOfSight(world.map(), a, b, ignoreGas);
    }

    /** First opaque gas tile encountered along the ray from origin (exclusive) to target (inclusive), stopping before walls. */
    public static Vec2 firstGasOnLine(World world, Vec2 origin, Vec2 target) {
        if (origin.equals(target)) {
            return isOpaqueGas(world.tile(target)) ? target : null;
        }
        int dx = Math.abs(target.x() - origin.x());
        int dy = Math.abs(target.y() - origin.y());
        int sx = origin.x() < target.x() ? 1 : -1;
        int sy = origin.y() < target.y() ? 1 : -1;
        int err = dx - dy;
        int x = origin.x();
        int y = origin.y();

        while (x != target.x() || y != target.y()) {
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
            Vec2 p = new Vec2(x, y);
            GameMap.Tile tile = world.tile(p);
            if (tile == null || tile.isWall()) {
                return null;
            }
            if (isOpaqueGas(tile)) {
                return p;
            }
        }
        return null;
    }

    /** Strictly 4-way cardinal line of fire; blocked by walls and any intervening entity. */
    public static boolean lineOfFire(World world, Vec2 a, Vec2 b) {
        if (a.equals(b) || (a.x() != b.x() && a.y() != b.y())) {
            return false;
        }
        int sx = Integer.compare(b.x(), a.x());
        int sy = Integer.compare(b.y(), a.y());
        int x = a.x() + sx;
        int y = a.y() + sy;

        while (x != b.x() || y != b.y()) {
            Vec2 p = new Vec2(x, y);
            GameMap.Tile tile = world.tile(p);
            if (tile == null || tile.isWall() || entityAt(world, p) != null) {
                return false;
            }
            x += sx;
            y += sy;
        }
        return true;
    }

    /**
     * Siphon reach: Chebyshev <= SIPHON_REACH_CHEBYSHEV (including diagonals),
     * or cardinal LoF up to SIPHON_REACH_MANHATTAN. Allows gas endpoints; blocks intervening gas/walls/entities.
     */
    public static boolean siphonLineOfFire(World world, Vec2 a, Vec2 b) {
        GameMap.Tile end = world.tile(b);
        if (end == null || end.isWall()) {
            return false;
        }
        if (a.chebyshev(b) <= Balance.SIPHON_REACH_CHEBYSHEV) {
            return true;
        }
        if (a.x() != b.x() && a.y() != b.y()) {
            return false;
        }
        if (Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) > Balance.SIPHON_REACH_MANHATTAN) {
            return false;
        }
        int sx = Integer.compare(b.x(), a.x());
        int sy = Integer.compare(b.y(), a.y());
        int x = a.x() + sx;
        int y = a.y() + sy;

        while (x != b.x() || y != b.y()) {
            Vec2 p = new Vec2(x, y);
            GameMap.Tile tile = world.tile(p);
            if (tile == null || tile.isWall() || isOpaqueGas(tile) || entityAt(world, p) != null) {
                return false;
            }
            x += sx;
            y += sy;
        }
        return true;
    }
}