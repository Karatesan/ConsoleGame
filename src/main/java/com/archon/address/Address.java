package com.archon.address;

import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;

/** Parsed, unresolved reference. Everything addressable is a path. */
public sealed interface Address {

    record EntityAddr(String id, String path) implements Address {}
    record InventoryAddr(String path) implements Address {}
    record TileAddr(String spec, String layer) implements Address {}

    /** Throws IllegalArgumentException with a player-facing message on malformed input. */
    static Address parse(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("empty address");

        if (s.startsWith("@")) {
            String body = s.substring(1);
            String layer = null;
            int slash = body.indexOf('/');
            if (slash >= 0) { layer = body.substring(slash + 1); body = body.substring(0, slash); }
            if (layer != null && !layer.equals("floor") && !layer.equals("ceiling"))
                throw new IllegalArgumentException("unknown layer \"" + layer + "\" (floor, ceiling)");
            return new TileAddr(body, layer);
        }
        if (s.startsWith("/")) return new InventoryAddr(s.substring(1));

        int slash = s.indexOf('/');
        return slash < 0 ? new EntityAddr(s, null)
                : new EntityAddr(s.substring(0, slash), s.substring(slash + 1));
    }

    /** Resolve a tile spec: "5,4" | "self" | "e2" | "<entityId>" */
    static Vec2 resolveTileSpec(String spec, World w) {
        if (spec.equalsIgnoreCase("self")) return w.thrall.pos;

        if (spec.contains(",")) {
            String[] p = spec.split(",");
            return new Vec2(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
        }

        String letters = spec.replaceAll("[0-9]", "");
        String digits  = spec.replaceAll("[^0-9]", "");
        Vec2 d = Vec2.dir(letters);
        if (d != null) {
            int n = digits.isEmpty() ? 1 : Integer.parseInt(digits);
            return new Vec2(w.thrall.pos.x() + d.x() * n, w.thrall.pos.y() + d.y() * n);
        }

        Entity e = w.get(spec);
        if (e != null) return e.pos;

        throw new IllegalArgumentException(
                "unknown tile spec \"" + spec + "\" — use @x,y, @self, @e2, or @<entityId>");
    }
}