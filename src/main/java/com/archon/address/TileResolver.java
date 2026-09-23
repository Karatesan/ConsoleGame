package com.archon.address;

import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;

final class TileResolver {
    private TileResolver() {}

    static Resolved resolve(Address.TileAddr address, World world) {
        return switch (AddressSyntax.tile(address.spec())) {
            case AddressSyntax.BadTile bad -> new Resolved.Failure(Resolved.Problem.INVALID_TILE, bad.reason());
            case AddressSyntax.AbsoluteTile absolute -> bounded(absolute.x(), absolute.y(), address.layer(), world);
            case AddressSyntax.RelativeTile relative ->
                    bounded(world.thrall().pos().x() + (long) relative.direction().x() * relative.distance(),
                            world.thrall().pos().y() + (long) relative.direction().y() * relative.distance(),
                            address.layer(), world);
            case AddressSyntax.AnchorTile anchor -> anchored(anchor.id(), address.layer(), world);
        };
    }

    private static Resolved anchored(String id, Address.Layer layer, World world) {
        Entity anchor = id.equalsIgnoreCase("self") ? world.thrall() : world.get(id);
        if (anchor == null) {
            return new Resolved.Failure(Resolved.Problem.UNKNOWN_ENTITY,
                    "unknown tile reference \"" + id + "\"; use @x,y, @self, @e2, or @entityId");
        }
        if (!anchor.alive()) {
            return new Resolved.Failure(Resolved.Problem.DEAD_ENTITY, anchor.id() + " is no longer alive");
        }
        return bounded(anchor.pos().x(), anchor.pos().y(), layer, world);
    }

    private static Resolved bounded(long x, long y, Address.Layer layer, World world) {
        if (x < 0 || y < 0 || x >= world.width() || y >= world.height()) {
            return new Resolved.Failure(Resolved.Problem.OUT_OF_BOUNDS,
                    "tile (" + x + "," + y + ") is outside the map");
        }
        return new Resolved.OnTile(new Vec2((int) x, (int) y), layer);
    }
}