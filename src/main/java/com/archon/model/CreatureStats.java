package com.archon.model;

/** Immutable creature initialization values. Live health and defenses belong to Entity. */
public record CreatureStats(int maxHp, int armor, int evasion, int strength) {
    public CreatureStats(int maxHp, int armor, int evasion) {
        this(maxHp, armor, evasion, 3);
    }
}
