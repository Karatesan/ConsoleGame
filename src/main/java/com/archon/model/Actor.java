package com.archon.model;

/**
 * Living combat creature with inventory and physical attributes.
 */
public class Actor extends Entity {

    private final Inventory inventory;
    private final int strength;
    private boolean nocked;

    public Actor(
            String id,
            String name,
            char glyph,
            Vec2 pos,
            CreatureStats stats
    ) {
        super(
                id,
                name,
                glyph,
                Kind.CREATURE,
                pos,
                stats.maxHp(),
                stats.armor(),
                stats.evasion()
        );
        this.inventory = new Inventory();
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

    /**
     * Attempts the right hand first, then falls back to the left hand.
     */
    public Item mainHand() {
        Item right = inventory.equipped(EquipmentSlot.HAND_RIGHT);
        return right != null
                ? right
                : inventory.equipped(EquipmentSlot.HAND_LEFT);
    }

    public Item disarm(EquipmentSlot slot) {
        return inventory.removeEquipped(slot);
    }
}