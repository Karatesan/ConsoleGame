package com.archon.model;

/** A creature with strength, equipment, carried items, and ranged-weapon state. */
public class Actor extends Entity {
    private Inventory inventory = new Inventory();
    private final int strength;
    private boolean nocked;

    public Actor(String id, String name, char glyph, Vec2 pos, CreatureStats stats) {
        super(id, name, glyph, Kind.CREATURE, pos, stats.maxHp(), stats.armor(), stats.evasion());
        this.strength = stats.strength();
    }

    public Inventory inventory() {
        return inventory;
    }

    public int strength() {
        return strength;
    }

    public boolean isNocked() {
        return nocked;
    }

    public void nock() {
        nocked = true;
    }

    public boolean fireNocked() {
        if (!nocked) {
            return false;
        }
        nocked = false;
        return true;
    }

    /** Right hand has priority for melee attacks. */
    public Item mainHand() {
        Item right = inventory.equipped(EquipmentSlot.HAND_RIGHT);
        return right != null ? right : inventory.equipped(EquipmentSlot.HAND_LEFT);
    }

    public Item disarm(EquipmentSlot slot) {
        return inventory.removeEquipped(slot);
    }
}