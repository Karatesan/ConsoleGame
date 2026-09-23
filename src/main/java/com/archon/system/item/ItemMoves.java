package com.archon.system.item;

import com.archon.model.Actor;
import com.archon.model.EquipmentSlot;
import com.archon.model.GameMap;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.model.Vec2;

import java.util.Objects;

/**
 * Synchronous item operations over existing ownership boundaries.
 *
 * Expected rejection does not mutate either owner. Successful relocation assumes
 * valid initial ownership and leaves the item at its intended destination.
 * Unexpected mutation failures are programming defects, not rollback requests.
 */
public final class ItemMoves {

    public enum Result {
        SUCCESS(null),
        STALE_SOURCE("item no longer at its source"),
        PACK_FULL("pack full (" + Inventory.PACK_MAX + ")"),
        ALREADY_AT_DESTINATION("item is already at the destination"),
        NOT_PACKED("item is not in a pack"),
        SLOT_OCCUPIED("equipment slot is occupied");

        private final String reason;

        Result(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }

        /** For callers whose established invariants require relocation to succeed. */
        public void requireSuccess() {
            if (this != SUCCESS) {
                throw new IllegalStateException(reason);
            }
        }
    }

    private ItemMoves() {}

    /** Finds the current carried location by identity, or null when not carried. */
    public static ItemLocation carriedLocation(Actor owner, Item item) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(item, "item");

        Inventory inventory = owner.inventory();
        if (packed(inventory, item)) {
            return new ItemLocation.Pack(owner);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (inventory.equipped(slot) == item) {
                return new ItemLocation.Equipment(owner, slot);
            }
        }
        return null;
    }

    public static Result checkSource(GameMap map, Item item, ItemLocation source) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(source, "source");

        boolean present = switch (source) {
            case ItemLocation.Pack pack -> packed(pack.owner().inventory(), item);
            case ItemLocation.Equipment equipment ->
                    equipment.owner().inventory().equipped(equipment.slot()) == item;
            case ItemLocation.Contents contents -> contents.owner().contents() == item;
            case ItemLocation.Ground ground -> requireTile(map, ground.pos()).ground().contains(item);
        };
        return present ? Result.SUCCESS : Result.STALE_SOURCE;
    }

    /** Checks relocation into a pack, without reserving either source or destination. */
    public static Result checkToPack(GameMap map, Item item, ItemLocation source, Actor recipient) {
        Objects.requireNonNull(recipient, "recipient");

        Result selected = checkSource(map, item, source);
        if (selected != Result.SUCCESS) {
            return selected;
        }
        if (recipient.inventory().isPackFull()) {
            return Result.PACK_FULL;
        }
        if (carriedLocation(recipient, item) != null) {
            return Result.ALREADY_AT_DESTINATION;
        }
        return Result.SUCCESS;
    }

    /** Moves an external item into a pack; already-carried selections do not relocate. */
    public static Result toPack(GameMap map, Item item, ItemLocation source, Actor recipient) {
        Result checked = checkToPack(map, item, source, recipient);
        if (checked != Result.SUCCESS) {
            return checked;
        }

        requireMutation(detach(map, item, source));
        requireMutation(recipient.inventory().addToPack(item));
        return Result.SUCCESS;
    }

    /** Drops a carried item at its owner's current position. */
    public static Result drop(GameMap map, Item item, ItemLocation source) {
        Objects.requireNonNull(source, "source");
        if (!(source instanceof ItemLocation.Pack || source instanceof ItemLocation.Equipment)) {
            throw new IllegalArgumentException("drop requires a carried source");
        }

        Result selected = checkSource(map, item, source);
        if (selected != Result.SUCCESS) {
            return selected;
        }
        Vec2 destination = source.pos();
        if (requireTile(map, destination).ground().contains(item)) {
            return Result.ALREADY_AT_DESTINATION;
        }

        requireMutation(detach(map, item, source));
        map.placeGroundItem(destination, item);
        return Result.SUCCESS;
    }

    /** Equips a selected packed item without displacing an occupied slot. */
    public static Result equip(Item item, ItemLocation source, EquipmentSlot slot) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(slot, "slot");

        if (!(source instanceof ItemLocation.Pack pack)) {
            return Result.NOT_PACKED;
        }

        Inventory inventory = pack.owner().inventory();
        if (!packed(inventory, item)) {
            return Result.STALE_SOURCE;
        }
        if (inventory.equipped(slot) != null) {
            return Result.SLOT_OCCUPIED;
        }

        requireMutation(inventory.equipFromPack(item, slot));
        return Result.SUCCESS;
    }

    /** Removes exactly the selected source item, without inventing a destination. */
    public static Result remove(GameMap map, Item item, ItemLocation source) {
        Result selected = checkSource(map, item, source);
        if (selected != Result.SUCCESS) {
            return selected;
        }

        requireMutation(detach(map, item, source));
        return Result.SUCCESS;
    }

    private static boolean packed(Inventory inventory, Item item) {
        return inventory.pack().stream().anyMatch(candidate -> candidate == item);
    }

    private static boolean detach(GameMap map, Item item, ItemLocation source) {
        return switch (source) {
            case ItemLocation.Pack pack -> pack.owner().inventory().remove(item);
            case ItemLocation.Equipment equipment ->
                    equipment.owner().inventory().removeEquipped(equipment.slot()) == item;
            case ItemLocation.Contents contents -> contents.owner().removeContents() == item;
            case ItemLocation.Ground ground -> map.removeGroundItem(ground.pos(), item);
        };
    }

    private static GameMap.Tile requireTile(GameMap map, Vec2 position) {
        Objects.requireNonNull(position, "position");
        GameMap.Tile tile = map.tile(position);
        if (tile == null) {
            throw new IllegalArgumentException("Position is outside the map: " + position);
        }
        return tile;
    }

    private static void requireMutation(boolean completed) {
        if (!completed) {
            throw new IllegalStateException("item mutation failed after its preconditions were established");
        }
    }
}