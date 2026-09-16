package com.archon.model;

/**
 * Immutable creature initialization values.
 * Live health and defenses are owned by {@link Entity}.
 */
public record CreatureStats(int maxHp, int armor, int evasion, int strength) {

    /**
     * Creates creature initialization values with the default strength of {@code 3}.
     *
     * @param maxHp maximum health
     * @param armor initial armor value
     * @param evasion initial evasion value
     */
    public CreatureStats(int maxHp, int armor, int evasion) {
        this(maxHp, armor, evasion, 3);
    }
}