package com.archon.address;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Vec2;
import com.archon.model.World;

public sealed interface Resolved permits Resolved.EntityTarget, Resolved.BodyTarget,
        Resolved.PackRoot, Resolved.PackedItem, Resolved.EquippedItem,
        Resolved.EmptyEquipmentSlot, Resolved.PropContents,
        Resolved.EmptyPropContents, Resolved.TileTarget {

    Pattern COORDINATE_PATTERN = Pattern.compile("^([+-]?\\d+),([+-]?\\d+)$");
    Pattern DIRECTION_PATTERN = Pattern.compile(
            "^(n|ne|e|se|s|sw|w|nw)([1-9]\\d*)?$",
            Pattern.CASE_INSENSITIVE);

    record EntityTarget(Entity entity) implements Resolved {
        public EntityTarget {
            entity = Objects.requireNonNull(entity, "entity");
        }
    }

    record BodyTarget(Entity entity, BodyPart bodyPart) implements Resolved {
        public BodyTarget {
            entity = Objects.requireNonNull(entity, "entity");
            bodyPart = Objects.requireNonNull(bodyPart, "bodyPart");
        }
    }

    record PackRoot(Actor owner) implements Resolved {
        public PackRoot {
            owner = Objects.requireNonNull(owner, "owner");
        }
    }

    record PackedItem(Actor owner, Item item) implements Resolved {
        public PackedItem {
            owner = Objects.requireNonNull(owner, "owner");
            item = Objects.requireNonNull(item, "item");
        }
    }

    record EquippedItem(Actor owner, EquipmentSlot slot, Item item) implements Resolved {
        public EquippedItem {
            owner = Objects.requireNonNull(owner, "owner");
            slot = Objects.requireNonNull(slot, "slot");
            item = Objects.requireNonNull(item, "item");
        }
    }

    record EmptyEquipmentSlot(Actor owner, EquipmentSlot slot) implements Resolved {
        public EmptyEquipmentSlot {
            owner = Objects.requireNonNull(owner, "owner");
            slot = Objects.requireNonNull(slot, "slot");
        }
    }

    record PropContents(Prop prop, Item item) implements Resolved {
        public PropContents {
            prop = Objects.requireNonNull(prop, "prop");
            item = Objects.requireNonNull(item, "item");
        }
    }

    record EmptyPropContents(Prop prop) implements Resolved {
        public EmptyPropContents {
            prop = Objects.requireNonNull(prop, "prop");
        }
    }

    record TileTarget(Vec2 pos, Address.Layer layer) implements Resolved {
        public TileTarget {
            pos = Objects.requireNonNull(pos, "pos");
            layer = Objects.requireNonNull(layer, "layer");
        }
    }

    public static Resolution resolve(Address address, World world) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(world, "world");

        return switch (address) {
            case Address.TileAddr tileAddress -> resolveTile(tileAddress, world);
            case Address.InventoryAddr inventoryAddress -> resolveInventory(world.thrall(), inventoryAddress.path());
            case Address.EntityAddr entityAddress -> resolveEntity(entityAddress, world);
        };
    }

    private static Resolution resolveTile(Address.TileAddr address, World world) {
        String spec = address.spec();
        if (spec == null || spec.isBlank() || containsWhitespace(spec)) {
            return failure(Resolution.Reason.INVALID_TILE_SPEC, "Tile specification is invalid.");
        }

        if (spec.equalsIgnoreCase("@self")) {
            Actor thrall = world.thrall();
            if (thrall == null || !thrall.alive()) {
                return failure(Resolution.Reason.DEAD_ENTITY, "The current actor is unavailable or dead.");
            }

            return resolveTilePosition(thrall.pos(), address.layer(), world);
        }

        Matcher coordinateMatcher = COORDINATE_PATTERN.matcher(spec);
        if (coordinateMatcher.matches()) {
            try {
                Vec2 position = new Vec2(
                        Integer.parseInt(coordinateMatcher.group(1)),
                        Integer.parseInt(coordinateMatcher.group(2)));
                return resolveTilePosition(position, address.layer(), world);
            } catch (NumberFormatException ignored) {
                return failure(Resolution.Reason.INVALID_TILE_SPEC, "Tile coordinates are outside the valid integer range.");
            }
        }

        if (spec.indexOf(',') >= 0) {
            return failure(Resolution.Reason.INVALID_TILE_SPEC, "Tile coordinates must use the strict x,y format.");
        }

        Matcher directionMatcher = DIRECTION_PATTERN.matcher(spec);
        if (directionMatcher.matches()) {
            int distance = parseDistance(directionMatcher.group(2));
            if (distance < 1) {
                return failure(Resolution.Reason.INVALID_TILE_SPEC, "Direction distance must be a positive integer.");
            }

            Actor thrall = world.thrall();
            if (thrall == null || !thrall.alive()) {
                return failure(Resolution.Reason.DEAD_ENTITY, "The current actor is unavailable or dead.");
            }
            if (thrall.pos() == null) {
                return failure(Resolution.Reason.INVALID_TILE_SPEC, "The current actor does not have a valid position.");
            }

            try {
                Vec2 position = offset(thrall.pos(), directionMatcher.group(1), distance);
                return resolveTilePosition(position, address.layer(), world);
            } catch (ArithmeticException ignored) {
                return failure(Resolution.Reason.INVALID_TILE_SPEC, "Directional tile coordinates are outside the valid integer range.");
            }
        }

        Entity entity = world.get(spec);
        if (entity == null) {
            return failure(Resolution.Reason.UNKNOWN_ENTITY, "No live entity matches the tile specification.");
        }
        if (!entity.alive()) {
            return failure(Resolution.Reason.DEAD_ENTITY, "The referenced entity is dead.");
        }

        return resolveTilePosition(entity.pos(), address.layer(), world);
    }

    private static Resolution resolveTilePosition(Vec2 position, Address.Layer layer, World world) {
        if (position == null) {
            return failure(Resolution.Reason.INVALID_TILE_SPEC, "The resolved entity does not have a valid position.");
        }

        if (!world.inBounds(position)) {
            return failure(Resolution.Reason.OUT_OF_BOUNDS, "The resolved tile is outside the world bounds.");
        }

        return found(new TileTarget(position, layer), "Tile target resolved.");
    }

    private static Resolution resolveEntity(Address.EntityAddr address, World world) {
        String id = address.id();
        if (id == null || id.isBlank()) {
            return failure(Resolution.Reason.UNKNOWN_ENTITY, "An entity identifier is required.");
        }

        Entity entity = world.get(id);
        if (entity == null) {
            return failure(Resolution.Reason.UNKNOWN_ENTITY, "No entity matches the requested identifier.");
        }
        if (!entity.alive()) {
            return failure(Resolution.Reason.DEAD_ENTITY, "The referenced entity is dead.");
        }

        String path = address.path();
        if (path == null) {
            return found(new EntityTarget(entity), "Entity target resolved.");
        }

        if (entity instanceof Prop prop && path.equals("contents")) {
            Item item = prop.contents();
            return item == null
                    ? found(new EmptyPropContents(prop), "The prop contents are empty.")
                    : found(new PropContents(prop, item), "Prop contents resolved.");
        }

        if (entity instanceof Actor actor) {
            Resolution inventoryResolution = resolveInventory(actor, path);
            if (inventoryResolution instanceof Resolution.Found) {
                return inventoryResolution;
            }

            Resolution.Failure failure = (Resolution.Failure) inventoryResolution;
            if (failure.reason() != Resolution.Reason.INVALID_PATH) {
                return failure;
            }
        }

        BodyPart bodyPart = BodyPart.parse(path);
        return bodyPart == null
                ? failure(Resolution.Reason.INVALID_PATH, "The requested entity path is invalid.")
                : found(new BodyTarget(entity, bodyPart), "Body-part target resolved.");
    }

    private static Resolution resolveInventory(Actor owner, String path) {
        if (owner == null || !owner.alive()) {
            return failure(Resolution.Reason.DEAD_ENTITY, "The inventory owner is unavailable or dead.");
        }
        if (path == null || path.isEmpty()) {
            return failure(Resolution.Reason.INVALID_PATH, "An inventory path is required.");
        }

        if (path.equals("pack")) {
            return found(new PackRoot(owner), "Pack target resolved.");
        }

        if (path.startsWith("pack/")) {
            String itemPath = path.substring("pack/".length());
            if (itemPath.isEmpty()) {
                return failure(Resolution.Reason.INVALID_PATH, "A packed item path is required.");
            }

            Item item = owner.inventory().find(itemPath).orElse(null);
            return item == null
                    ? failure(Resolution.Reason.INVALID_PATH, "No packed item matches the requested path.")
                    : found(new PackedItem(owner, item), "Packed item resolved.");
        }

        return EquipmentSlot.parse(path)
                .<Resolution>map(slot -> {
                    Item item = owner.inventory().equipped(slot);
                    return item == null
                            ? found(new EmptyEquipmentSlot(owner, slot), "Equipment slot is empty.")
                            : found(new EquippedItem(owner, slot, item), "Equipped item resolved.");
                })
                .orElseGet(() -> failure(Resolution.Reason.INVALID_PATH, "The requested inventory path is invalid."));
    }

    private static Resolution.Found found(Resolved target, String detail) {
        return new Resolution.Found(target, detail);
    }

    private static Resolution.Failure failure(Resolution.Reason reason, String detail) {
        return new Resolution.Failure(reason, detail);
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static int parseDistance(String value) {
        if (value == null) {
            return 1;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static Vec2 offset(Vec2 origin, String direction, int distance) {
        int dx;
        int dy;

        switch (direction.toLowerCase(Locale.ROOT)) {
            case "n" -> {
                dx = 0;
                dy = -1;
            }
            case "ne" -> {
                dx = 1;
                dy = -1;
            }
            case "e" -> {
                dx = 1;
                dy = 0;
            }
            case "se" -> {
                dx = 1;
                dy = 1;
            }
            case "s" -> {
                dx = 0;
                dy = 1;
            }
            case "sw" -> {
                dx = -1;
                dy = 1;
            }
            case "w" -> {
                dx = -1;
                dy = 0;
            }
            case "nw" -> {
                dx = -1;
                dy = -1;
            }
            default -> throw new IllegalArgumentException("Unsupported direction: " + direction);
        }

        return new Vec2(
                Math.addExact(origin.x(), Math.multiplyExact(dx, distance)),
                Math.addExact(origin.y(), Math.multiplyExact(dy, distance)));
    }
}