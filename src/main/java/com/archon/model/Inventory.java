package com.archon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Domain model encapsulating equipment slots and carried pack inventory.
 */
public final class Inventory {
    public static final int PACK_MAX = 8;

    private final EnumMap<EquipmentSlot, Item> equipment = new EnumMap<>(EquipmentSlot.class);
    private final List<Item> pack = new ArrayList<>();

    public Inventory() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.put(slot, null);
        }
    }

    public boolean isPackFull() {
        return pack.size() >= PACK_MAX;
    }

    public boolean isPackEmpty() {
        return pack.isEmpty();
    }

    /**
     * Returns an unmodifiable view of all equipment slots and their contents.
     */
    public Map<EquipmentSlot, Item> equipment() {
        return Collections.unmodifiableMap(equipment);
    }

    /**
     * Returns an unmodifiable view of the carried pack contents.
     */
    public List<Item> pack() {
        return Collections.unmodifiableList(pack);
    }

    public Item equipped(EquipmentSlot slot) {
        return slot == null ? null : equipment.get(slot);
    }

    /**
     * Finds the first packed item matching the supplied id or name.
     */
    public Optional<Item> find(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        return pack.stream()
                .filter(item -> query.equalsIgnoreCase(item.id()) || query.equalsIgnoreCase(item.name()))
                .findFirst();
    }

    /**
     * Places an item directly into an equipment slot for scenario setup.
     * The item must not already exist in the pack or another equipment slot.
     * Replacing the current item in the target slot is permitted.
     */
    public boolean placeInSlotForSetup(EquipmentSlot slot, Item item) {
        if (slot == null || item == null || isPresentOutsideSlot(item, slot)) {
            return false;
        }

        equipment.put(slot, item);
        return true;
    }

    /**
     * Adds an item to the pack when capacity is available and the item is not
     * already located in this inventory.
     */
    public boolean addToPack(Item item) {
        if (item == null || isPackFull() || isPresentAnywhere(item)) {
            return false;
        }

        pack.add(item);
        return true;
    }

    /**
     * Removes a packed item by identity.
     */
    public boolean removeFromPack(Item item) {
        return removePackedInstance(item);
    }

    /**
     * Equips a packed item into a slot. Any displaced slot item is returned to
     * the pack after the source item has been removed.
     */
    public boolean equipFromPack(Item item, EquipmentSlot slot) {
        if (item == null || slot == null || countPackedInstances(item) != 1 || isEquippedAnywhere(item)) {
            return false;
        }

        Item displaced = equipment.get(slot);
        if (displaced != null && (containsPackedInstance(displaced) || isEquippedOutsideSlot(displaced, slot))) {
            return false;
        }

        removePackedInstance(item);
        equipment.put(slot, item);

        if (displaced != null) {
            pack.add(displaced);
        }

        return true;
    }

    /**
     * Unequips the item in a slot and places it into the pack.
     */
    public boolean unequipToPack(EquipmentSlot slot) {
        if (slot == null) {
            return false;
        }

        Item item = equipment.get(slot);
        if (item == null) {
            return true;
        }

        if (isPackFull() || containsPackedInstance(item) || isEquippedOutsideSlot(item, slot)) {
            return false;
        }

        equipment.put(slot, null);
        pack.add(item);
        return true;
    }

    /**
     * Removes and returns the item equipped in the given slot without placing it
     * into the pack.
     */
    public Item removeEquipped(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }

        return equipment.put(slot, null);
    }

    /**
     * Removes exactly one item instance from the pack or equipment for dropping.
     * Packed instances are removed before equipped instances.
     */
    public boolean remove(Item item) {
        if (item == null) {
            return false;
        }

        if (removePackedInstance(item)) {
            return true;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (equipment.get(slot) == item) {
                equipment.put(slot, null);
                return true;
            }
        }

        return false;
    }

    private boolean isPresentAnywhere(Item item) {
        return containsPackedInstance(item) || isEquippedAnywhere(item);
    }

    private boolean isPresentOutsideSlot(Item item, EquipmentSlot slot) {
        return containsPackedInstance(item) || isEquippedOutsideSlot(item, slot);
    }

    private boolean containsPackedInstance(Item item) {
        return countPackedInstances(item) > 0;
    }

    private int countPackedInstances(Item item) {
        int count = 0;
        for (Item packedItem : pack) {
            if (packedItem == item) {
                count++;
            }
        }
        return count;
    }

    private boolean isEquippedAnywhere(Item item) {
        for (Item equippedItem : equipment.values()) {
            if (equippedItem == item) {
                return true;
            }
        }
        return false;
    }

    private boolean isEquippedOutsideSlot(Item item, EquipmentSlot excludedSlot) {
        for (Map.Entry<EquipmentSlot, Item> entry : equipment.entrySet()) {
            if (entry.getKey() != excludedSlot && entry.getValue() == item) {
                return true;
            }
        }
        return false;
    }

    private boolean removePackedInstance(Item item) {
        if (item == null) {
            return false;
        }

        for (int index = 0; index < pack.size(); index++) {
            if (pack.get(index) == item) {
                pack.remove(index);
                return true;
            }
        }
        return false;
    }
}