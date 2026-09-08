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
        if (world.thrall != null && world.thrall.pos.equals(p)) return world.thrall;
        return world.entities.values().stream()
                .filter(e -> e.alive() && e.pos.equals(p))
                .findFirst().orElse(null);
    }

    public static boolean passable(World world, Vec2 p) {
        World.Tile t = world.tile(p);
        return t != null && !t.wall && entityAt(world, p) == null;
    }

    public static List<Entity> hostilesAdjacentTo(World world, Vec2 p) {
        return world.entities.values().stream()
                .filter(e -> e.alive() && e.kind == Entity.Kind.CREATURE)
                .filter(e -> e.pos.chebyshev(p) <= 1)
                .toList();
    }

    public static boolean lineOfSight(GameMap map, Vec2 a, Vec2 b) {
        int dx = Math.abs(b.x() - a.x()), dy = Math.abs(b.y() - a.y());
        int sx = a.x() < b.x() ? 1 : -1, sy = a.y() < b.y() ? 1 : -1;
        int err = dx - dy, x = a.x(), y = a.y();
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
            World.Tile t = map.tile(new Vec2(x, y));
            if (t == null || t.wall) return false;
        }
        return true;
    }

    public static boolean lineOfSight(World world, Vec2 a, Vec2 b) {
        return lineOfSight(world.map, a, b);
    }
}
