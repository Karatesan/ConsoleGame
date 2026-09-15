package com.archon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Equipment and carried items. Every item instance occupies at most one location. */
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
        return equipment.get(slot);
    }

    public Optional<Item> find(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        return pack.stream()
                .filter(item -> item.id().equalsIgnoreCase(query) || item.name().equalsIgnoreCase(query))
                .findFirst();
    }

    /** Scenario/setup-only placement. Runtime transfers use named operations below. */
    public void placeInSlotForSetup(EquipmentSlot slot, Item item) {
        Objects.requireNonNull(slot, "equipment slot cannot be null");
        if (item == null) {
            throw new IllegalArgumentException("equipment item cannot be null");
        }
        if (contains(item)) {
            throw new IllegalArgumentException("item is already in this inventory");
        }

        equipment.put(slot, item);
    }

    public boolean addToPack(Item item) {
        if (item == null || isPackFull() || contains(item)) {
            return false;
        }

        pack.add(item);
        return true;
    }

    public boolean equipFromPack(Item item, EquipmentSlot slot) {
        Objects.requireNonNull(slot, "equipment slot cannot be null");

        if (item == null || !removePackedIdentity(item)) {
            return false;
        }

        Item displaced = equipment.put(slot, item);
        if (displaced != null) {
            pack.add(displaced);
        }

        return true;
    }

    public boolean unequipToPack(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "equipment slot cannot be null");

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

    public Item removeEquipped(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "equipment slot cannot be null");
        return equipment.put(slot, null);
    }

    /** Removes an owned item without moving it elsewhere. */
    public boolean remove(Item item) {
        if (item == null) {
            return false;
        }
        if (removePackedIdentity(item)) {
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

    private boolean contains(Item item) {
        if (pack.stream().anyMatch(candidate -> candidate == item)) {
            return true;
        }

        return equipment.values().stream().anyMatch(candidate -> candidate == item);
    }

    private boolean removePackedIdentity(Item item) {
        for (int index = 0; index < pack.size(); index++) {
            if (pack.get(index) == item) {
                pack.remove(index);
                return true;
            }
        }

        return false;
    }
}