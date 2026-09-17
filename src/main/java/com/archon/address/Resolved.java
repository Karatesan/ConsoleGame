package com.archon.address;

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

    Pattern COORDINATE_SPEC = Pattern.compile("[+-]?\\d+,[+-]?\\d+");
    Pattern DIRECTION_SPEC = Pattern.compile("(?:ne|se|sw|nw|n|e|s|w)(?:[1-9]\\d*)?");
    Pattern DIRECTION_PREFIX = Pattern.compile("(?:ne|se|sw|nw|n|e|s|w).*");

    record EntityTarget(Entity entity) implements Resolved {
        public EntityTarget {
            Objects.requireNonNull(entity, "entity");
        }
    }

    record BodyTarget(Entity entity, BodyPart bodyPart) implements Resolved {
        public BodyTarget {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(bodyPart, "bodyPart");
        }
    }

    record PackRoot(Actor owner) implements Resolved {
        public PackRoot {
            Objects.requireNonNull(owner, "owner");
        }
    }

    record PackedItem(Actor owner, Item item) implements Resolved {
        public PackedItem {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(item, "item");
        }
    }

    record EquippedItem(Actor owner, EquipmentSlot slot, Item item) implements Resolved {
        public EquippedItem {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(item, "item");
        }
    }

    record EmptyEquipmentSlot(Actor owner, EquipmentSlot slot) implements Resolved {
        public EmptyEquipmentSlot {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(slot, "slot");
        }
    }

    record PropContents(Prop prop, Item item) implements Resolved {
        public PropContents {
            Objects.requireNonNull(prop, "prop");
            Objects.requireNonNull(item, "item");
        }
    }

    record EmptyPropContents(Prop prop) implements Resolved {
        public EmptyPropContents {
            Objects.requireNonNull(prop, "prop");
        }
    }

    record TileTarget(Vec2 pos, Address.Layer layer) implements Resolved {
        public TileTarget {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(layer, "layer");
        }
    }

    static Resolution resolve(Address address, World world) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(world, "world");

        return switch (address) {
            case Address.TileAddr tile -> resolveTile(tile, world);
            case Address.InventoryAddr inventory -> resolveInventory(world.thrall(), inventory.path());
            case Address.EntityAddr entityAddress -> resolveEntity(entityAddress, world);
        };
    }

    private static Resolution resolveTile(Address.TileAddr address, World world) {
        String spec = address.spec();
        if (spec == null || spec.isEmpty()) {
            return new Resolution.Failure(Resolution.Reason.INVALID_TILE_SPEC, "Tile specification is empty.");
        }

        if (!isBuiltInTileSpec(spec)) {
            if (isMalformedTileSpec(spec)) {
                return new Resolution.Failure(
                        Resolution.Reason.INVALID_TILE_SPEC,
                        "Invalid tile specification: " + spec);
            }

            Entity entity = world.get(spec);
            if (entity == null) {
                return new Resolution.Failure(
                        Resolution.Reason.UNKNOWN_ENTITY,
                        "Unknown entity: " + spec);
            }
            if (!entity.alive()) {
                return new Resolution.Failure(
                        Resolution.Reason.DEAD_ENTITY,
                        "Entity is dead: " + spec);
            }
        }

        Vec2 pos = Address.resolveTileSpec(spec, world);
        if (pos == null) {
            return new Resolution.Failure(
                    Resolution.Reason.INVALID_TILE_SPEC,
                    "Invalid tile specification: " + spec);
        }
        if (!world.inBounds(pos)) {
            return new Resolution.Failure(
                    Resolution.Reason.OUT_OF_BOUNDS,
                    "Tile is outside world bounds: " + spec);
        }

        return new Resolution.Found(new TileTarget(pos, address.layer()));
    }

    private static Resolution resolveEntity(Address.EntityAddr address, World world) {
        Entity entity = world.get(address.id());
        if (entity == null) {
            return new Resolution.Failure(
                    Resolution.Reason.UNKNOWN_ENTITY,
                    "Unknown entity: " + address.id());
        }
        if (!entity.alive()) {
            return new Resolution.Failure(
                    Resolution.Reason.DEAD_ENTITY,
                    "Entity is dead: " + address.id());
        }

        String path = address.path();
        if (path == null) {
            return new Resolution.Found(new EntityTarget(entity));
        }

        if (entity instanceof Prop prop && path.equals("contents")) {
            Item item = prop.contents();
            return item == null
                    ? new Resolution.Found(new EmptyPropContents(prop))
                    : new Resolution.Found(new PropContents(prop, item));
        }

        if (entity instanceof Actor actor) {
            Resolution inventoryResolution = resolveInventory(actor, path);
            if (!(inventoryResolution instanceof Resolution.Failure failure)
                    || failure.reason() != Resolution.Reason.INVALID_PATH) {
                return inventoryResolution;
            }
        }

        BodyPart bodyPart = BodyPart.parse(path);
        if (bodyPart != null) {
            return new Resolution.Found(new BodyTarget(entity, bodyPart));
        }

        return new Resolution.Failure(
                Resolution.Reason.INVALID_PATH,
                "Invalid path for entity " + address.id() + ": " + path);
    }

    private static Resolution resolveInventory(Actor owner, String path) {
        if (path == null || path.isEmpty()) {
            return new Resolution.Failure(
                    Resolution.Reason.INVALID_PATH,
                    "Inventory path is empty.");
        }

        if (path.equals("pack")) {
            return new Resolution.Found(new PackRoot(owner));
        }

        if (path.startsWith("pack/")) {
            String itemPath = path.substring("pack/".length());
            if (itemPath.isEmpty()) {
                return new Resolution.Failure(
                        Resolution.Reason.INVALID_PATH,
                        "Packed item path is empty.");
            }

            Item item = owner.inventory().find(itemPath).orElse(null);
            return item == null
                    ? new Resolution.Failure(
                            Resolution.Reason.INVALID_PATH,
                            "No packed item at path: " + itemPath)
                    : new Resolution.Found(new PackedItem(owner, item));
        }

        return EquipmentSlot.parse(path)
                .<Resolution>map(slot -> {
                    Item item = owner.inventory().equipped(slot);
                    return item == null
                            ? new Resolution.Found(new EmptyEquipmentSlot(owner, slot))
                            : new Resolution.Found(new EquippedItem(owner, slot, item));
                })
                .orElseGet(() -> new Resolution.Failure(
                        Resolution.Reason.INVALID_PATH,
                        "Invalid inventory path: " + path));
    }

    private static boolean isBuiltInTileSpec(String spec) {
        if (spec.equals("self")) {
            return true;
        }

        Matcher coordinateMatcher = COORDINATE_SPEC.matcher(spec);
        if (coordinateMatcher.matches()) {
            return true;
        }

        Matcher directionMatcher = DIRECTION_SPEC.matcher(spec);
        return directionMatcher.matches();
    }

    private static boolean isMalformedTileSpec(String spec) {
        if (spec.indexOf(',') >= 0) {
            return true;
        }

        Matcher directionPrefixMatcher = DIRECTION_PREFIX.matcher(spec);
        return directionPrefixMatcher.matches();
    }
}