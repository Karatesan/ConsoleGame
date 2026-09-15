package com.archon.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Living or active combat creature with inventory, stats, and reactive capabilities.
 */
public class Actor extends Entity {

    private final CreatureStats stats;
    private final Inventory inventory;
    private final WeaponState weaponState;

    public Actor(
            String id,
            String name,
            char glyph,
            Vec2 pos,
            CreatureStats stats
    ) {
        super(id, name, glyph, Kind.CREATURE, pos, stats.maxHp());
        this.stats = stats;
        this.inventory = new Inventory();
        this.weaponState = new WeaponState();
    }

    public CreatureStats stats() {
        return stats;
    }

    public Inventory inventory() {
        return inventory;
    }

    public WeaponState weaponState() {
        return weaponState;
    }

    @Override
    public Actor ready(Trigger trigger, String description, int damage) {
        super.ready(trigger, description, damage);
        return this;
    }

    @Override
    public int armor() {
        int armor = stats.armor();
        if (isGuarded()) {
            armor += (int) Math.round(armor * 0.3);
        }
        return armor;
    }

    @Override
    public int evasion() {
        int evasion = stats.evasion();
        if (isCrippled(BodyPart.LEGS)) {
            evasion -= 10;
        }
        return Math.max(0, evasion);
    }

    public int strength() {
        return stats.strength();
    }

    /**
     * Attempts the right hand first, then falls back to the left hand.
     */
    public Item mainHand() {
        Item right = inventory.getEquipped(EquipmentSlot.HAND_RIGHT);
        return right != null
                ? right
                : inventory.getEquipped(EquipmentSlot.HAND_LEFT);
    }

    /**
     * Unequips and returns the active hand item.
     */
    @Override
    public Item disarm() {
        Item right = inventory.getEquipped(EquipmentSlot.HAND_RIGHT);
        if (right != null) {
            inventory.equip(EquipmentSlot.HAND_RIGHT, null);
            return right;
        }

        Item left = inventory.getEquipped(EquipmentSlot.HAND_LEFT);
        if (left != null) {
            inventory.equip(EquipmentSlot.HAND_LEFT, null);
            return left;
        }

        return null;
    }

    public Item findInPack(String idOrName) {
        return inventory.findInPack(idOrName);
    }
}