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
    private final ArrayList<Item> pack = new ArrayList<>();

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

    public Map<EquipmentSlot, Item> equipment() {
        return Collections.unmodifiableMap(equipment);
    }

    public List<Item> pack() {
        return Collections.unmodifiableList(pack);
    }

    public Item equipped(EquipmentSlot slot) {
        return slot == null ? null : equipment.get(slot);
    }

    /**
     * Places an item directly into an equipment slot during setup.
     * The item must not already exist elsewhere in this inventory.
     */
    public boolean placeInSlotForSetup(EquipmentSlot slot, Item item) {
        if (slot == null || item == null) {
            return false;
        }

        Item current = equipment.get(slot);
        if (current == item) {
            return true;
        }

        if (containsIdentityInPack(item) || containsIdentityInEquipmentExcept(item, slot)) {
            return false;
        }

        equipment.put(slot, item);
        return true;
    }

    public boolean addToPack(Item item) {
        if (item == null || isPackFull() || containsIdentity(item)) {
            return false;
        }

        pack.add(item);
        return true;
    }

    /**
     * Equips a packed item into the specified slot. Any displaced item is returned
     * to the pack after the incoming item is removed, allowing full-pack swaps.
     */
    public boolean equipFromPack(Item item, EquipmentSlot slot) {
        if (item == null || slot == null) {
            return false;
        }

        int packIndex = indexOfIdentityInPack(item);
        if (packIndex < 0) {
            return false;
        }

        Item displaced = equipment.get(slot);
        pack.remove(packIndex);
        equipment.put(slot, item);

        if (displaced != null) {
            pack.add(displaced);
        }

        return true;
    }

    /**
     * Unequips an item into the pack.
     *
     * @return {@code false} when the slot contains an item and the pack is full
     */
    public boolean unequipToPack(EquipmentSlot slot) {
        if (slot == null) {
            return false;
        }

        Item item = equipment.get(slot);
        if (item == null) {
            return true;
        }

        if (isPackFull()) {
            return false;
        }

        equipment.put(slot, null);
        pack.add(item);
        return true;
    }

    /**
     * Removes and returns the item equipped in the specified slot.
     */
    public Item removeEquipped(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }

        return equipment.put(slot, null);
    }

    /**
     * Removes the item by identity from the pack or equipment exactly once.
     */
    public boolean remove(Item item) {
        if (item == null) {
            return false;
        }

        int packIndex = indexOfIdentityInPack(item);
        if (packIndex >= 0) {
            pack.remove(packIndex);
            return true;
        }

        for (Map.Entry<EquipmentSlot, Item> entry : equipment.entrySet()) {
            if (entry.getValue() == item) {
                entry.setValue(null);
                return true;
            }
        }

        return false;
    }

    public Optional<Item> find(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        return pack.stream()
                .filter(item -> item.id().equalsIgnoreCase(query) || item.name().equalsIgnoreCase(query))
                .findFirst();
    }

    private boolean containsIdentity(Item item) {
        return containsIdentityInPack(item) || containsIdentityInEquipmentExcept(item, null);
    }

    private boolean containsIdentityInPack(Item item) {
        return indexOfIdentityInPack(item) >= 0;
    }

    private boolean containsIdentityInEquipmentExcept(Item item, EquipmentSlot excludedSlot) {
        for (Map.Entry<EquipmentSlot, Item> entry : equipment.entrySet()) {
            if (entry.getKey() != excludedSlot && entry.getValue() == item) {
                return true;
            }
        }
        return false;
    }

    private int indexOfIdentityInPack(Item item) {
        for (int index = 0; index < pack.size(); index++) {
            if (pack.get(index) == item) {
                return index;
            }
        }
        return -1;
    }
}