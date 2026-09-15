package com.archon.model;

/**
 * Encapsulates immutable base creature attributes used during initialization.
 * Live health and defensive state are owned by the entity.
 */
public record CreatureStats(int maxHp, int armor, int evasion, int strength) {

    public CreatureStats {
        if (maxHp < 0) {
            throw new IllegalArgumentException("maxHp must not be negative");
        }
        if (strength < 0) {
            throw new IllegalArgumentException("strength must not be negative");
        }
    }

    public CreatureStats(int maxHp, int armor, int evasion) {
        this(maxHp, armor, evasion, 3);
    }

    public static CreatureStats of(int maxHp, int armor, int evasion, int strength) {
        return new CreatureStats(maxHp, armor, evasion, strength);
    }
}