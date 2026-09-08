package com.archon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Domain model encapsulating equipment slots and carried pack inventory.
 */
public final class Inventory {
    public static final int PACK_MAX = 8;
    public static final List<String> SLOTS =
            List.of("head", "torso", "legs", "hand/left", "hand/right");

    private final Map<String, Item> equipment = new LinkedHashMap<>();
    private final List<Item> pack = new ArrayList<>();

    public Inventory() {
        SLOTS.forEach(s -> equipment.put(s, null));
    }

    public Map<String, Item> equipment() {
        return equipment;
    }

    public List<Item> pack() {
        return pack;
    }

    public boolean isFull() {
        return pack.size() >= PACK_MAX;
    }

    public boolean packFull() {
        return isFull();
    }

    public Item getSlot(String slot) {
        return equipment.get(slot);
    }

    public void setSlot(String slot, Item item) {
        equipment.put(slot, item);
    }

    public boolean addToPack(Item item) {
        if (isFull()) return false;
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
