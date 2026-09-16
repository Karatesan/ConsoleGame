package com.archon.address;

public sealed interface Address {
    enum Layer {
        FLOOR,
        CEILING
    }

    record EntityAddr(String id, String path) implements Address {
    }

    record InventoryAddr(String path) implements Address {
    }

    record TileAddr(String spec, Layer layer) implements Address {
        public TileAddr {
            if (spec == null || spec.isBlank()) {
                throw new IllegalArgumentException("empty tile spec");
            }
            if (layer == null) {
                throw new IllegalArgumentException("layer is required");
            }
        }
    }

    public static Address parse(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("empty address");
        }

        if (source.startsWith("@")) {
            String body = source.substring(1);
            int slash = body.indexOf('/');

            if (slash < 0) {
                if (body.isBlank()) {
                    throw new IllegalArgumentException("empty tile spec");
                }
                return new TileAddr(body, Layer.FLOOR);
            }

            if (body.indexOf('/', slash + 1) >= 0) {
                throw new IllegalArgumentException("invalid tile layer syntax");
            }

            String spec = body.substring(0, slash);
            String layerName = body.substring(slash + 1);

            if (spec.isBlank()) {
                throw new IllegalArgumentException("empty tile spec");
            }
            if (layerName.isBlank()) {
                throw new IllegalArgumentException("invalid tile layer syntax");
            }

            Layer layer = switch (layerName) {
                case "floor" -> Layer.FLOOR;
                case "ceiling" -> Layer.CEILING;
                default -> throw new IllegalArgumentException(
                        "unknown layer \"" + layerName + "\" (floor, ceiling)"
                );
            };

            return new TileAddr(spec, layer);
        }

        if (source.startsWith("/")) {
            String path = source.substring(1);
            if (path.isBlank()) {
                throw new IllegalArgumentException("empty inventory path");
            }
            return new InventoryAddr(path);
        }

        int slash = source.indexOf('/');
        if (slash < 0) {
            if (source.isBlank()) {
                throw new IllegalArgumentException("empty entity id");
            }
            return new EntityAddr(source, null);
        }

        String id = source.substring(0, slash);
        String path = source.substring(slash + 1);

        if (id.isBlank()) {
            throw new IllegalArgumentException("empty entity id");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("empty entity path");
        }

        return new EntityAddr(id, path);
    }
}