package com.archon.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** A creature with strength, equipment, carried items, and ranged-weapon state. */
public class Actor extends Entity {
    public enum Trigger {
        ON_ADJACENCY,
        ON_MOVEMENT_IN_LOS
    }

    public record Readied(Trigger trigger, String description, int damage) {
        public Readied {
            Objects.requireNonNull(trigger, "trigger");
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
            if (damage < 0) {
                throw new IllegalArgumentException("damage must be nonnegative");
            }
        }
    }

    private final Inventory inventory = new Inventory();
    private final Map<BodyPart, Boolean> crippled = new EnumMap<>(BodyPart.class);
    private final int strength;
    private Readied readied;
    private boolean readiedSpent;
    private boolean guarded;
    private boolean nocked;

    public Actor(String id, String name, char glyph, Vec2 pos, CreatureStats stats) {
        this(validatedStats(stats), id, name, glyph, pos);
    }

    private Actor(CreatureStats stats, String id, String name, char glyph, Vec2 pos) {
        super(
                id,
                name,
                glyph,
                Kind.CREATURE,
                pos,
                stats.maxHp(),
                stats.armor(),
                stats.evasion());
        this.strength = stats.strength();

        for (BodyPart bodyPart : BodyPart.values()) {
            crippled.put(bodyPart, false);
        }
    }

    private static CreatureStats validatedStats(CreatureStats stats) {
        return Objects.requireNonNull(stats, "creature stats cannot be null");
    }

    public Inventory inventory() {
        return inventory;
    }

    public Map<BodyPart, Boolean> crippled() {
        return Collections.unmodifiableMap(crippled);
    }

    public int strength() {
        return strength;
    }

    public boolean isGuarded() {
        return guarded;
    }

    public Readied readied() {
        return readied;
    }

    public boolean isReadiedSpent() {
        return readiedSpent;
    }

    public Actor ready(Trigger trigger, String description, int damage) {
        Objects.requireNonNull(trigger, "trigger");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be nonnegative");
        }

        readied = new Readied(trigger, description, damage);
        readiedSpent = false;
        return this;
    }

    public void setGuarded(boolean guarded) {
        this.guarded = guarded;
    }

    public void spendReadied() {
        readiedSpent = true;
    }

    public void resetRoundState() {
        readiedSpent = false;
        guarded = false;
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
