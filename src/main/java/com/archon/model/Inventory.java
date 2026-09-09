package com.archon.model;

import java.util.*;

/**
 * Domain model encapsulating equipment slots and carried pack inventory.
 */
public final class Inventory {
    public static final int PACK_MAX = 8;
    public static final List<String> SLOTS =
            Arrays.stream(EquipmentSlot.values()).map(s -> s.path).toList();

    private final Map<EquipmentSlot, Item> equipment = new EnumMap<>(EquipmentSlot.class);
    private final List<Item> pack = new ArrayList<>();

    public Inventory() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.put(slot, null);
        }
    }

    // --- State Queries ---

    public boolean isPackFull() {
        return pack.size() >= PACK_MAX;
    }

    public boolean isPackEmpty() {
        return pack.isEmpty();
    }

    public boolean isEmpty() {
        return isPackEmpty();
    }

    /** Unmodifiable view for CLI display/directory listings (e.g., "ls thrall/") */
    public Map<EquipmentSlot, Item> equipment() {
        return Collections.unmodifiableMap(equipment);
    }

    /** Unmodifiable view so external code cannot bypass PACK_MAX */
    public List<Item> pack() {
        return Collections.unmodifiableList(pack);
    }

    // --- Slot Access (Typed + String Overloads) ---

    public Item getSlot(EquipmentSlot slot) {
        return equipment.get(slot);
    }

    /** CLI-friendly string lookup (e.g. from Address.java / Resolved.java) */
    public Item getSlot(String path) {
        return EquipmentSlot.parse(path)
                .map(equipment::get)
                .orElse(null);
    }

    public void setSlot(EquipmentSlot slot, Item item) {
        equipment.put(slot, item);
    }

    public boolean setSlot(String path, Item item) {
        Optional<EquipmentSlot> slot = EquipmentSlot.parse(path);
        slot.ifPresent(s -> equipment.put(s, item));
        return slot.isPresent();
    }

    // --- High-Level Game Actions ---

    /**
     * Equips an item from the pack into the designated slot.
     * If a weapon/armor is already in that slot, it swaps it back into the pack.
     * Returns false if pack doesn't contain the item, or if the swap fails.
     */
    public boolean equipFromPack(Item item, EquipmentSlot slot) {
        if (!pack.contains(item)) return false;

        Item currentlyEquipped = equipment.get(slot);

        // Can't swap if slot is occupied and pack has no room for the swapped item
        if (currentlyEquipped != null && isPackFull()) {
            return false;
        }

        pack.remove(item);
        equipment.put(slot, item);

        if (currentlyEquipped != null) {
            pack.add(currentlyEquipped);
        }
        return true;
    }

    /**
     * Unequips an item from a slot and places it in the pack.
     * Returns false if the pack is full.
     */
    public boolean unequipToPack(EquipmentSlot slot) {
        Item item = equipment.get(slot);
        if (item == null) return true; // Slot is already empty
        if (isPackFull()) return false;

        equipment.put(slot, null);
        pack.add(item);
        return true;
    }

    /**
     * Unequips an item from whatever slot it is equipped in without placing in pack.
     */
    public boolean unequipItem(Item item) {
        if (item == null) return false;
        boolean found = false;
        for (Map.Entry<EquipmentSlot, Item> e : equipment.entrySet()) {
            if (e.getValue() == item) {
                e.setValue(null);
                found = true;
            }
        }
        return found;
    }

    // --- Pack Operations ---

    public boolean addToPack(Item item) {
        if (isPackFull() || item == null) return false;
        return pack.add(item);
    }

    public boolean removeFromPack(Item item) {
        return pack.remove(item);
    }

    public Optional<Item> find(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        return pack.stream()
                .filter(i -> i.id.equalsIgnoreCase(query) || i.name.equalsIgnoreCase(query))
                .findFirst();
    }

    public Item findInPack(String idOrName) {
        return find(idOrName).orElse(null);
    }
}