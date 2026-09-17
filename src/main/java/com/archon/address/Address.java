package com.archon.address;

import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;

/** Parsed, unresolved reference. Everything addressable is a path. */
public sealed interface Address {
    record EntityAddr(String id, String path) implements Address { }
    record InventoryAddr(String path) implements Address { }
    record TileAddr(String spec, String layer) implements Address { }

    public static Address parse(String source) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("empty address");
        if (source.startsWith("@")) {
            String body = source.substring(1);
            String layer = null;
            int slash = body.indexOf('/');
            if (slash >= 0) {
                layer = body.substring(slash + 1);
                body = body.substring(0, slash);
            }
            if (layer != null && !layer.equals("floor") && !layer.equals("ceiling")) {
                throw new IllegalArgumentException("unknown layer \"" + layer + "\" (floor, ceiling)");
            }
            return new TileAddr(body, layer);
        }
        if (source.startsWith("/")) return new InventoryAddr(source.substring(1));
        int slash = source.indexOf('/');
        return slash < 0
                ? new EntityAddr(source, null)
                : new EntityAddr(source.substring(0, slash), source.substring(slash + 1));
    }

    static Vec2 resolveTileSpec(String spec, World world) {
        if (spec.equalsIgnoreCase("self")) return world.thrall().pos();
        if (spec.contains(",")) {
            String[] parts = spec.split(",");
            return new Vec2(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }
        String letters = spec.replaceAll("[0-9]", "");
        String digits = spec.replaceAll("[^0-9]", "");
        Vec2 direction = Vec2.dir(letters);
        if (direction != null) {
            int distance = digits.isEmpty() ? 1 : Integer.parseInt(digits);
            return new Vec2(
                    world.thrall().pos().x() + direction.x() * distance,
                    world.thrall().pos().y() + direction.y() * distance
            );
        }
        Entity entity = world.get(spec);
        if (entity != null) return entity.pos();
        throw new IllegalArgumentException(
                "unknown tile spec \"" + spec + "\" — use @x,y, @self, @e2, or @<entityId>"
        );
    }
}