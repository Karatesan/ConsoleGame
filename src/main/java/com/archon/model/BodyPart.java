package com.archon.model;

import static com.archon.model.Balance.*;

public enum BodyPart {
    HEAD ("head",  "h",  HEAD_HIT_MOD,  HEAD_DMG_MULT,  HEAD_CRIT_BONUS),
    TORSO("torso", "t",  TORSO_HIT_MOD, TORSO_DMG_MULT, TORSO_CRIT_BONUS),
    ARM_L("arm.l", "la", ARM_L_HIT_MOD, ARM_L_DMG_MULT, ARM_L_CRIT_BONUS),
    ARM_R("arm.r", "ra", ARM_R_HIT_MOD, ARM_R_DMG_MULT, ARM_R_CRIT_BONUS),
    LEGS ("legs",  "l",  LEGS_HIT_MOD,  LEGS_DMG_MULT,  LEGS_CRIT_BONUS);

    public final String path, shortName;
    public final int hitMod;
    public final double damageMult;
    public final int critBonus;

    BodyPart(String path, String shortName, int hitMod, double damageMult, int critBonus) {
        this.path = path; this.shortName = shortName;
        this.hitMod = hitMod; this.damageMult = damageMult;
        this.critBonus = critBonus;
    }

    /** Accepts "head", "h", "arm.l", "la", ... Returns null when unknown. */
    public static BodyPart parse(String s) {
        if (s == null) return null;
        String v = s.toLowerCase();
        for (BodyPart p : values())
            if (p.path.equals(v) || p.shortName.equals(v)) return p;
        return null;
    }

    /** Weighted hit location roll for untargeted strikes (Fallout 2 style). */
    public static BodyPart rollLocation(Dice dice) {
        int roll = dice.between(1, 100);
        if (roll <= 50) return TORSO;
        if (roll <= 65) return ARM_L;
        if (roll <= 80) return ARM_R;
        if (roll <= 95) return LEGS;
        return HEAD;
    }
}