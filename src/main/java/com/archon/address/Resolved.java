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
            "^(n|ne|e|se|s|sw|w|nw)(?:\\s+([1-9]\\d*))?$",
            Pattern.CASE_INSENSITIVE);

    record EntityTarget(Entity entity) implements Resolved {
        public EntityTarget {
            entity = Objects.requireNonNull(entity, "entity");
        }
    }

    record BodyTarget(Entity entity, BodyPart part) implements Resolved {
        public BodyTarget {
            entity = Objects.requireNonNull(entity, "entity");
            part = Objects.requireNonNull(part, "part");
        }
    }

    record PackRoot(Actor actor) implements Resolved {
        public PackRoot {
            actor = Objects.requireNonNull(actor, "actor");
        }
    }

    record PackedItem(Actor actor, Item item) implements Resolved {
        public PackedItem {
            actor = Objects.requireNonNull(actor, "actor");
            item = Objects.requireNonNull(item, "item");
        }
    }

    record EquippedItem(Actor actor, EquipmentSlot slot, Item item) implements Resolved {
        public EquippedItem {
            actor = Objects.requireNonNull(actor, "actor");
            slot = Objects.requireNonNull(slot, "slot");
            item = Objects.requireNonNull(item, "item");
        }
    }

    record EmptyEquipmentSlot(Actor actor, EquipmentSlot slot) implements Resolved {
        public EmptyEquipmentSlot {
            actor = Objects.requireNonNull(actor, "actor");
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

    sealed interface Resolution permits Resolution.Found, Resolution.Failure {
        record Found(Resolved target) implements Resolution {
            public Found {
                target = Objects.requireNonNull(target, "target");
            }
        }

        record Failure(Reason reason) implements Resolution {
            public Failure {
                reason = Objects.requireNonNull(reason, "reason");
            }
        }

        enum Reason {
            INVALID_TILE_SPEC,
            UNKNOWN_ENTITY,
            DEAD_ENTITY,
            OUT_OF_BOUNDS,
            INVALID_PATH
        }
    }

    static Resolution resolve(Address address, World world) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(world, "world");

        return switch (address) {
            case Address.TileAddr tile -> resolveTile(tile, world);
            case Address.InventoryAddr inventory -> resolveInventory(world.thrall(), inventory.path());
            case Address.EntityAddr entity -> resolveEntity(entity, world);
        };
    }

    private static Resolution resolveTile(Address.TileAddr address, World world) {
        String spec = address.spec();
        if (spec == null) {
            return failure(Resolution.Reason.INVALID_TILE_SPEC);
        }

        String value = spec.trim();
        if (value.equalsIgnoreCase("self")) {
            Actor thrall = world.thrall();
            if (thrall == null || !thrall.alive()) {
                return failure(Resolution.Reason.DEAD_ENTITY);
            }

            return resolveTilePosition(thrall.pos(), address.layer(), world);
        }

        Matcher coordinate = COORDINATE_PATTERN.matcher(value);
        if (coordinate.matches()) {
            try {
                return resolveTilePosition(
                        new Vec2(
                                Integer.parseInt(coordinate.group(1)),
                                Integer.parseInt(coordinate.group(2))),
                        address.layer(),
                        world);
            } catch (NumberFormatException ignored) {
                return failure(Resolution.Reason.INVALID_TILE_SPEC);
            }
        }

        if (value.indexOf(',') >= 0) {
            return failure(Resolution.Reason.INVALID_TILE_SPEC);
        }

        Matcher direction = DIRECTION_PATTERN.matcher(value);
        if (direction.matches()) {
            int distance = parsePositiveDistance(direction.group(2));
            if (distance < 1) {
                return failure(Resolution.Reason.INVALID_TILE_SPEC);
            }

            Actor thrall = world.thrall();
            if (thrall == null || !thrall.alive() || thrall.pos() == null) {
                return failure(Resolution.Reason.DEAD_ENTITY);
            }

            try {
                return resolveTilePosition(
                        offset(thrall.pos(), direction.group(1), distance),
                        address.layer(),
                        world);
            } catch (ArithmeticException ignored) {
                return failure(Resolution.Reason.INVALID_TILE_SPEC);
            }
        }

        Entity entity = world.get(value);
        if (entity == null) {
            return failure(Resolution.Reason.UNKNOWN_ENTITY);
        }
        if (!entity.alive()) {
            return failure(Resolution.Reason.DEAD_ENTITY);
        }

        return resolveTilePosition(entity.pos(), address.layer(), world);
    }

    private static Resolution resolveTilePosition(Vec2 pos, Address.Layer layer, World world) {
        if (pos == null) {
            return failure(Resolution.Reason.INVALID_TILE_SPEC);
        }

        return world.inBounds(pos)
                ? found(new TileTarget(pos, layer))
                : failure(Resolution.Reason.OUT_OF_BOUNDS);
    }

    private static Resolution resolveEntity(Address.EntityAddr address, World world) {
        String id = address.id();
        if (id == null || id.isBlank()) {
            return failure(Resolution.Reason.UNKNOWN_ENTITY);
        }

        Entity entity = world.get(id);
        if (entity == null) {
            return failure(Resolution.Reason.UNKNOWN_ENTITY);
        }
        if (!entity.alive()) {
            return failure(Resolution.Reason.DEAD_ENTITY);
        }

        String path = address.path();
        if (path == null) {
            return found(new EntityTarget(entity));
        }

        if (entity instanceof Prop prop && path.equals("contents")) {
            Item item = prop.contents();
            return item == null
                    ? found(new EmptyPropContents(prop))
                    : found(new PropContents(prop, item));
        }

        if (entity instanceof Actor actor) {
            Resolution inventoryResolution = resolveInventory(actor, path);
            if (inventoryResolution instanceof Resolution.Found
                    || ((Resolution.Failure) inventoryResolution).reason()
                            != Resolution.Reason.INVALID_PATH) {
                return inventoryResolution;
            }
        }

        BodyPart part = BodyPart.parse(path);
        return part == null
                ? failure(Resolution.Reason.INVALID_PATH)
                : found(new BodyTarget(entity, part));
    }

    private static Resolution resolveInventory(Actor actor, String path) {
        if (actor == null || !actor.alive()) {
            return failure(Resolution.Reason.DEAD_ENTITY);
        }
        if (path == null || path.isEmpty()) {
            return failure(Resolution.Reason.INVALID_PATH);
        }

        if (path.equals("pack")) {
            return found(new PackRoot(actor));
        }

        if (path.startsWith("pack/")) {
            String itemPath = path.substring("pack/".length());
            if (itemPath.isEmpty()) {
                return failure(Resolution.Reason.INVALID_PATH);
            }

            Item item = actor.inventory().find(itemPath).orElse(null);
            return item == null
                    ? failure(Resolution.Reason.INVALID_PATH)
                    : found(new PackedItem(actor, item));
        }

        return EquipmentSlot.parse(path)
                .<Resolution>map(slot -> {
                    Item item = actor.inventory().equipped(slot);
                    return item == null
                            ? found(new EmptyEquipmentSlot(actor, slot))
                            : found(new EquippedItem(actor, slot, item));
                })
                .orElseGet(() -> failure(Resolution.Reason.INVALID_PATH));
    }

    private static Resolution.Found found(Resolved target) {
        return new Resolution.Found(target);
    }

    private static Resolution.Failure failure(Resolution.Reason reason) {
        return new Resolution.Failure(reason);
    }

    private static int parsePositiveDistance(String value) {
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