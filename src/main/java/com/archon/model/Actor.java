package com.archon.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Living or active combat creature with inventory, stats, and reactive capabilities.
 */
public class Actor extends Entity {

    public enum Trigger {
        ON_ADJACENCY,
        ON_MOVEMENT_IN_LOS
    }

    /**
     * A deterministic, visible reaction. Fires at most once per round.
     */
    public record Readied(Trigger trigger, String description, int damage) {}

    private final CreatureStats stats;
    private final Inventory inventory;
    private final WeaponState weaponState;
    private final Map<BodyPart, Boolean> crippled;
    private Readied readied;
    private boolean readiedSpent;
    private boolean guarded;

    public Actor(String id, String name, char glyph, Vec2 pos, CreatureStats stats) {
        super(id, name, glyph, pos, stats.maxHp(), stats.maxHp());
        this.stats = stats;
        this.inventory = new Inventory();
        this.weaponState = new WeaponState();
        this.crippled = new EnumMap<>(BodyPart.class);
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

    public Map<BodyPart, Boolean> crippled() {
        return Collections.unmodifiableMap(crippled);
    }

    public void setCrippled(BodyPart part, boolean value) {
        crippled.put(part, value);
    }

    public boolean isGuarded() {
        return guarded;
    }

    public void setGuarded(boolean guarded) {
        this.guarded = guarded;
    }

    public Readied readied() {
        return readied;
    }

    public boolean isReadiedSpent() {
        return readiedSpent;
    }

    public void spendReaction() {
        this.readiedSpent = true;
    }

    public void resetReaction() {
        this.readiedSpent = false;
    }

    public Actor ready(Trigger tr, String desc, int dmg) {
        this.readied = new Readied(tr, desc, dmg);
        this.readiedSpent = false;
        return this;
    }

    public void resetRoundState() {
        this.guarded = false;
        this.readiedSpent = false;
    }

    @Override
    public int armor() {
        int arm = stats.armor();
        if (guarded) {
            arm += (int) Math.round(arm * 0.3);
        }
        return arm;
    }

    @Override
    public int evasion() {
        int ev = stats.evasion();
        if (Boolean.TRUE.equals(crippled.get(BodyPart.LEGS))) {
            ev -= 10;
        }
        return Math.max(0, ev);
    }

    public int strength() {
        return stats.strength();
    }

    /**
     * Creature combat behavior: attempts right hand first, falls back to left hand.
     */
    public Item mainHand() {
        Item right = inventory.getEquipped(EquipmentSlot.HAND_RIGHT);
        return right != null ? right : inventory.getEquipped(EquipmentSlot.HAND_LEFT);
    }

    /**
     * Disarms the creature's active equipped weapon, removing it from inventory and returning it.
     */
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
