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

    public Item getSlot(EquipmentSlot slot) {
        return inventory.getSlot(slot);
    }

    public Item getSlot(String path) {
        return inventory.getSlot(path);
    }

    public void setSlot(EquipmentSlot slot, Item item) {
        inventory.setSlot(slot, item);
    }

    public boolean setSlot(String path, Item item) {
        return inventory.setSlot(path, item);
    }

    public boolean packFull() {
        return inventory.isPackFull();
    }

    public Item findInPack(String idOrName) {
        return inventory.findInPack(idOrName);
    }

    public List<Item> pack() {
        return inventory.pack();
    }

    public Map<EquipmentSlot, Item> equipment() {
        return inventory.equipment();
    }

    /**
     * Creature combat behavior: attempts right hand first, falls back to left hand.
     */
    public Item mainHand() {
        Item right = inventory.getSlot(EquipmentSlot.HAND_RIGHT);
        return right != null ? right : inventory.getSlot(EquipmentSlot.HAND_LEFT);
    }
}