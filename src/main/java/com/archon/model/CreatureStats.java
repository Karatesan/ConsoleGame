package com.archon.model;

/**
 * Encapsulates base permanent creature attributes and progression.
 */
public final class CreatureStats {
    private int maxHp;
    private int armor;
    private int evasion;
    private int strength;

    public CreatureStats(int maxHp, int armor, int evasion, int strength) {
        this.maxHp = maxHp;
        this.armor = armor;
        this.evasion = evasion;
        this.strength = strength;
    }

    public static CreatureStats of(int maxHp, int armor, int evasion, int strength) {
        return new CreatureStats(maxHp, armor, evasion, strength);
    }

    public int maxHp() {
        return maxHp;
    }

    public int armor() {
        return armor;
    }

    public int evasion() {
        return evasion;
    }

    public int strength() {
        return strength;
    }

    public void increaseMaxHp(int delta) {
        this.maxHp += delta;
    }

    public void increaseArmor(int delta) {
        this.armor += delta;
    }

    public void increaseEvasion(int delta) {
        this.evasion += delta;
    }

    public void increaseStrength(int delta) {
        this.strength += delta;
    }
}
