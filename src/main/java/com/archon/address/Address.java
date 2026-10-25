// src/main/java/com/archon/address/Address.java
package com.archon.address;

import com.archon.model.BodyPart;
import com.archon.model.EquipmentSlot;
import com.archon.model.Vec2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed, unresolved reference. Everything addressable is a path. */
public sealed interface Address {
    enum Layer { TILE, FLOOR, CEILING }

    sealed interface Path {}
    record RootPath() implements Path {}
    record ContentsPath() implements Path {}
    record BodyPath(BodyPart part) implements Path {}
    record PackPath(String query) implements Path {}
    record SlotPath(EquipmentSlot slot) implements Path {}

    sealed interface TileSpec {}
    record AbsoluteTile(int x, int y) implements TileSpec {}
    record RelativeTile(Vec2 direction, int distance) implements TileSpec {}
    record AnchorTile(String id) implements TileSpec {}

    record EntityAddr(String id, Path path) implements Address {}
    record InventoryAddr(Path path) implements Address {}
    record TileAddr(TileSpec spec, Layer layer) implements Address {}
    record Invalid(String reason) implements Address {}

    Pattern COORDINATES = Pattern.compile("([+-]?\\d+)\\s*,\\s*([+-]?\\d+)");
    Pattern RELATIVE = Pattern.compile("(ne|nw|se|sw|n|s|e|w)(\\d+)?", Pattern.CASE_INSENSITIVE);

    static Address parse(String source) {
        if (source == null || source.isBlank()) {
            return new Invalid("empty address");
        }
        if (source.endsWith("/") || source.contains("//")) {
            return new Invalid("address contains an empty path segment");
        }

        if (source.startsWith("@")) return parseTile(source);
        if (source.startsWith("/")) return parsePath(source.substring(1), false, null);

        int slash = source.indexOf('/');
        String id = slash < 0 ? source : source.substring(0, slash);
        if (id.isBlank()) {
            return new Invalid("empty entity ID");
        }

        return parsePath(slash < 0 ? "" : source.substring(slash + 1), true, id);
    }

    private static Address parsePath(String text, boolean isEntity, String entityId) {
        Path path = null;
        if (isEntity && text.isEmpty()) {
            path = new RootPath();
        } else if (isEntity && text.equals("contents")) {
            path = new ContentsPath();
        } else if (isEntity && text.startsWith("body/")) {
            BodyPart part = BodyPart.parse(text.substring("body/".length()));
            if (part != null) path = new BodyPath(part);
        } else if (text.equals("pack")) {
            path = new PackPath(null);
        } else if (text.startsWith("pack/")) {
            String query = text.substring("pack/".length());
            if (!query.isBlank()) path = new PackPath(query);
        } else {
            path = EquipmentSlot.parse(text).<Path>map(SlotPath::new).orElse(null);
        }

        if (path == null) {
            return new Invalid("unknown target path \"" + text + "\"");
        }
        return isEntity ? new EntityAddr(entityId, path) : new InventoryAddr(path);
    }

    private static Address parseTile(String source) {
        int slash = source.indexOf('/');
        String spec = slash < 0 ? source.substring(1) : source.substring(1, slash);
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
        if (layer == null) {
            return new Invalid("unknown layer \"" + suffix + "\" (floor, ceiling)");
        }

        try {
            if (spec.contains(",")) {
                Matcher coordinates = COORDINATES.matcher(spec.trim());
                if (!coordinates.matches()) {
                    return new Invalid("invalid coordinates \"" + spec + "\"; use @x,y");
                }
                return new TileAddr(new AbsoluteTile(
                        Integer.parseInt(coordinates.group(1)),
                        Integer.parseInt(coordinates.group(2))
                ), layer);
            }

            Matcher relative = RELATIVE.matcher(spec);
            if (relative.matches()) {
                return new TileAddr(new RelativeTile(
                        Vec2.dir(relative.group(1)),
                        relative.group(2) == null ? 1 : Integer.parseInt(relative.group(2))
                ), layer);
            }
        } catch (NumberFormatException exception) {
            return new Invalid("tile coordinate or distance is too large in \"" + spec + "\"");
        }
        return new TileAddr(new AnchorTile(spec), layer);
    }
}