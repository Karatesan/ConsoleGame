package com.archon.address;

import com.archon.model.*;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public sealed interface Resolved {

    sealed interface Target extends Resolved {
        Vec2 pos();
    }

    record OnEntity(Entity entity) implements Target {
        public Vec2 pos() { return entity.pos(); }
    }

    record OnBody(Actor actor, BodyPart part) implements Target {
        public Vec2 pos() { return actor.pos(); }
    }

    record OnItem(Item item, ItemLocation location) implements Target {
        public Vec2 pos() { return location.pos(); }
    }

    record OnLocation(ItemLocation location) implements Target {
        public Vec2 pos() { return location.pos(); }
    }

    record OnTile(Vec2 pos, String layer) implements Target {
        public OnTile(Vec2 pos) {
            this(pos, null);
        }
    }

    record Failure(String reason, boolean syntax) implements Resolved {
        static Failure syntax(String reason) { return new Failure(reason, true); }
        static Failure world(String reason) { return new Failure(reason, false); }
    }

    Pattern COORDINATES = Pattern.compile("([+-]?\\d+)\\s*,\\s*([+-]?\\d+)");
    Pattern RELATIVE = Pattern.compile("(ne|nw|se|sw|n|s|e|w)(\\d+)?", Pattern.CASE_INSENSITIVE);

    default Entity targetEntity() {
        return switch (this) {
            case OnEntity(Entity entity) -> entity;
            case OnBody(Actor actor, var ignored) -> actor;
            default -> null;
        };
    }

    default ItemLocation location() {
        return switch (this) {
            case OnItem(var ignored, var loc) -> loc;
            case OnLocation(var loc) -> loc;
            default -> null;
        };
    }

    static Resolved resolve(String source, World world) {
        if (source == null || source.isBlank()) {
            return Failure.syntax("empty address");
        }
        if (source.endsWith("/") || source.contains("//")) {
            return Failure.syntax("address contains an empty path segment");
        }
        if (source.startsWith("@")) {
            return resolveTile(source.substring(1), world);
        }
        if (source.startsWith("/")) {
            return resolvePath(source.substring(1), false, null, world);
        }

        int slash = source.indexOf('/');
        String id = slash < 0 ? source : source.substring(0, slash);
        if (id.isBlank()) {
            return Failure.syntax("empty entity ID");
        }
        return resolvePath(slash < 0 ? "" : source.substring(slash + 1), true, id, world);
    }

    private static Resolved resolvePath(String text, boolean isEntity, String id, World world) {
        BodyPart part = null;
        String query = null;
        EquipmentSlot slot = null;

        if (isEntity && (text.isEmpty() || text.equals("contents"))) {
            // valid entity-level path
        } else if (isEntity && text.startsWith("body/")) {
            part = BodyPart.parse(text.substring("body/".length()));
            if (part == null) return Failure.syntax("unknown target path \"" + text + "\"");
        } else if (text.equals("pack")) {
            // valid pack root
        } else if (text.startsWith("pack/")) {
            query = text.substring("pack/".length());
            if (query.isBlank()) return Failure.syntax("unknown target path \"" + text + "\"");
        } else {
            slot = EquipmentSlot.parse(text).orElse(null);
            if (slot == null) return Failure.syntax("unknown target path \"" + text + "\"");
        }

        Entity entity = isEntity ? world.get(id) : world.thrall();
        if (entity == null) return Failure.world("unknown entity \"" + id + "\"");
        if (!entity.alive()) return Failure.world(entity.id() + " is no longer alive");

        if (text.isEmpty()) return new OnEntity(entity);
        if (text.equals("contents")) {
            return entity instanceof Prop prop
                    ? selection(prop.contents(), new ItemLocation.Contents(prop))
                    : Failure.world("target does not support that path");
        }
        if (!(entity instanceof Actor actor)) {
            return Failure.world("target does not support that path");
        }
        if (part != null) return new OnBody(actor, part);
        if (slot != null) return selection(actor.inventory().equipped(slot), new ItemLocation.Equipment(actor, slot));
        if (query == null) return new OnLocation(new ItemLocation.Pack(actor));
        Item item = actor.inventory().find(query).orElse(null);
        return item != null
                ? new OnItem(item, new ItemLocation.Pack(actor))
                : Failure.world("no packed item \"" + query + "\" on " + actor.id());
    }

    private static Resolved resolveTile(String spec, World world) {
        if (spec.isBlank()) {
            return Failure.syntax("empty tile specification");
        }
        int slash = spec.indexOf('/');
        String tileSpec = slash < 0 ? spec : spec.substring(0, slash);
        String path = slash < 0 ? null : spec.substring(slash + 1);
        if (tileSpec.isBlank()) {
            return Failure.syntax("empty tile specification");
        }
        if (path != null && (path.isBlank() || path.contains("/"))) {
            return Failure.syntax("invalid tile path \"" + spec + "\"");
        }

        Resolved base = resolveTileAnchor(tileSpec, world);
        if (!(base instanceof OnTile(Vec2 pos, var ignored)) || path == null) {
            return base;
        }

        String key = path.toLowerCase(Locale.ROOT);
        String layer = normalizeLayer(key);
        if (layer != null) {
            return new OnTile(pos, layer);
        }
        Item item = findGroundItem(world.tile(pos), key);
        if (item != null) {
            return new OnItem(item, new ItemLocation.Ground(pos));
        }
        return Failure.world("no ground item or layer \"" + path + "\" at " + pos);
    }

    private static Resolved resolveTileAnchor(String spec, World world) {
        try {
            if (spec.contains(",")) {
                Matcher coords = COORDINATES.matcher(spec.trim());
                if (!coords.matches()) {
                    return Failure.syntax("invalid coordinates \"" + spec + "\"; use @x,y");
                }
                return bounded(Integer.parseInt(coords.group(1)), Integer.parseInt(coords.group(2)), world);
            }
            Matcher rel = RELATIVE.matcher(spec);
            if (rel.matches()) {
                Vec2 dir = Vec2.dir(rel.group(1));
                int dist = rel.group(2) == null ? 1 : Integer.parseInt(rel.group(2));
                return bounded(
                        world.thrall().pos().x() + (long) dir.x() * dist,
                        world.thrall().pos().y() + (long) dir.y() * dist,
                        world);
            }
        } catch (NumberFormatException exception) {
            return Failure.syntax("tile coordinate or distance is too large in \"" + spec + "\"");
        }

        Entity anchor = spec.equalsIgnoreCase("self") ? world.thrall() : world.get(spec);
        if (anchor != null) {
            if (!anchor.alive()) {
                return Failure.world(anchor.id() + " is no longer alive");
            }
            return bounded(anchor.pos().x(), anchor.pos().y(), world);
        }

        return Failure.world("unknown tile reference \"" + spec + "\"; use @x,y, @self, @e2, or @entityId");
    }

    private static String normalizeLayer(String key) {
        return switch (key) {
            case "gas", "floor", "steam", "oil_mist", "water", "oil", "acid", "ice" -> key;
            case "caustic", "caustic_gas" -> "caustic";
            default -> null;
        };
    }

    private static Item findGroundItem(GameMap.Tile tile, String key) {
        if (tile == null) return null;
        List<Item> ground = tile.ground();
        Item partial = null;
        for (int i = 0; i < ground.size(); i++) {
            Item item = ground.get(i);
            String id = item.id().toLowerCase(Locale.ROOT);
            String name = item.name().toLowerCase(Locale.ROOT);
            if (id.equals(key) || name.equals(key)) {
                return item;
            }
            if (partial == null && (id.contains(key) || name.contains(key))) {
                partial = item;
            }
        }
        return partial;
    }

    private static Resolved bounded(long x, long y, World world) {
        if (x < 0 || y < 0 || x >= world.width() || y >= world.height()) {
            return Failure.world("tile (" + x + "," + y + ") is outside the map");
        }
        return new OnTile(new Vec2((int) x, (int) y));
    }

    private static Resolved selection(Item item, ItemLocation location) {
        return item == null ? new OnLocation(location) : new OnItem(item, location);
    }
}
