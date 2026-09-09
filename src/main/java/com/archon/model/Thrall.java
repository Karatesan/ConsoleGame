package com.archon.model;

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

    public Item findInPack(String idOrName) {
        return inventory.findInPack(idOrName);
    }

    /**
     * Creature combat behavior: attempts right hand first, falls back to left hand.
     */
    public Item mainHand() {
        Item right = inventory.getEquipped(EquipmentSlot.HAND_RIGHT);
        return right != null ? right : inventory.getEquipped(EquipmentSlot.HAND_LEFT);
    }
}