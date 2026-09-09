package com.archon.model;

import java.util.Map;

public final class Thrall extends Entity {
    private final Inventory inventory = new Inventory();
    public boolean nocked;
    public int strength = 3;

    public Thrall(Vec2 pos, int hp) {
        super("self", "Thrall", 'T', Kind.CREATURE, pos, hp);
        tags.add(Tag.ORGANIC);
        tags.add(Tag.FLESH);
        tags.add(Tag.FLAMMABLE);
        identified = true;
    }

    public Inventory inventory() {
        return inventory;
    }

    /**
     * Creature combat behavior: attempts right hand first, falls back to left hand.
     */
    public Item mainHand() {
        Item right = inventory.getSlot(EquipmentSlot.HAND_RIGHT);
        return right != null ? right : inventory.getSlot(EquipmentSlot.HAND_LEFT);
    }
}