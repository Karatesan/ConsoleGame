package com.archon.model;

/**
 * Immutable creature initialization values.
 * Live health and defenses are owned by {@link Entity}.
 */
public record CreatureStats(int maxHp, int armor, int evasion, int strength, int magic, double thermalMass) {

    /**
     * Creates creature initialization values with default strength, magic, and thermal mass.
     *
     * @param maxHp maximum health
     * @param armor initial armor value
     * @param evasion initial evasion value
     */
    public CreatureStats(int maxHp, int armor, int evasion) {
        this(maxHp, armor, evasion, Balance.DEFAULT_STRENGTH, Balance.BASE_MAGIC, 1.0);
    }

    /**
     * Creates creature initialization values with default magic and thermal mass.
     *
     * @param maxHp maximum health
     * @param armor initial armor value
     * @param evasion initial evasion value
     * @param strength initial strength value
     */
    public CreatureStats(int maxHp, int armor, int evasion, int strength) {
        this(maxHp, armor, evasion, strength, Balance.BASE_MAGIC, 1.0);
    }
}