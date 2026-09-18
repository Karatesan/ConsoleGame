package com.archon.address;

import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;

/** Parsed, unresolved reference. Everything addressable is a path. */
public sealed interface Address {
    enum Layer { TILE, FLOOR, CEILING }

    record EntityAddr(String id, String path) implements Address { }
    record InventoryAddr(String path) implements Address { }
    record TileAddr(String spec, Layer layer) implements Address { }
    record Invalid(String reason) implements Address { }

    static Address parse(String source) {
        if (source == null || source.isBlank()) {
            return new Invalid("empty address");
        }
        if (source.endsWith("/") || source.contains("//")) {
            return new Invalid("address contains an empty path segment");
        }

        if (source.startsWith("@")) {
            int slash = source.indexOf('/');
            String spec = slash < 0
                    ? source.substring(1)
                    : source.substring(1, slash);
            String suffix = slash < 0 ? "" : source.substring(slash + 1);

            if (spec.isBlank()) {
                return new Invalid("empty tile specification");
            }

            Layer layer = switch (suffix) {
                case "" -> Layer.TILE;
                case "floor" -> Layer.FLOOR;
                case "ceiling" -> Layer.CEILING;
                default -> null;
            };

            return layer == null
                    ? new Invalid("unknown layer \"" + suffix + "\" (floor, ceiling)")
                    : new TileAddr(spec, layer);
        }

        if (source.startsWith("/")) {
            return new InventoryAddr(source.substring(1));
        }

        int slash = source.indexOf('/');
        String id = slash < 0 ? source : source.substring(0, slash);
        if (id.isBlank()) {
            return new Invalid("empty entity ID");
        }

        return new EntityAddr(id, slash < 0 ? "" : source.substring(slash + 1));
    }
}