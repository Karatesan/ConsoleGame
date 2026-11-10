package com.archon.system.spatial;

import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Vec2;
import com.archon.model.World;

import java.util.List;

/**
 * Domain service encapsulating geometry, raycasting (line of sight),
 * map passability, and entity occupancy checks.
 */
public final class SpatialService {

    private SpatialService() {}

    public static Entity entityAt(World world, Vec2 p) {
        if (world.thrall() != null && world.thrall().pos().equals(p)) return world.thrall();
        return world.entities().stream()
                .filter(e -> e.alive() && e.pos().equals(p))
                .findFirst()
                .orElse(null);
    }

    public static boolean passable(World world, Vec2 p) {
        GameMap.Tile tile = world.tile(p);
        return tile != null && !tile.isWall() && entityAt(world, p) == null;
    }

    public static List<Entity> hostilesAdjacentTo(World world, Vec2 p) {
        return world.entities().stream()
                .filter(e -> e.alive() && e.kind() == Entity.Kind.CREATURE)
                .filter(e -> e.pos().chebyshev(p) <= 1)
                .toList();
    }

    public static boolean lineOfSight(GameMap map, Vec2 a, Vec2 b) {
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
            if (tile == null || tile.isWall()) return false;
        }

        return true;
    }

    public static boolean lineOfSight(World world, Vec2 a, Vec2 b) {
        return lineOfSight(world.map(), a, b);
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
}