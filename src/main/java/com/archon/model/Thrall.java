package com.archon.model;

import java.util.List;
import java.util.Map;

public final class Thrall extends Entity {
    public static final int PACK_MAX = Inventory.PACK_MAX;
    public static final List<String> SLOTS = Inventory.SLOTS;

    private final Inventory inventory = new Inventory();
    public final Map<String, Item> slots = inventory.equipment();
    public final List<Item> pack = inventory.pack();
    public boolean nocked;
    public int strength = 3;

    public Thrall(Vec2 pos, int hp) {
        super("self", "Thrall", 'T', Kind.CREATURE, pos, hp);
        tags.add(Tag.ORGANIC); tags.add(Tag.FLESH); tags.add(Tag.FLAMMABLE);
        identified = true;
    }

    public Inventory inventory() {
        return inventory;
    }

    public Item mainHand() {
        Item r = slots.get("hand/right");
        return r != null ? r : slots.get("hand/left");
    }

    public boolean packFull() { return inventory.isFull(); }

    public Item findInPack(String idOrName) {
        return inventory.findInPack(idOrName);
    }
}