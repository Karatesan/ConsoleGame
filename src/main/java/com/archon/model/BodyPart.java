package com.archon.model;

import static com.archon.model.Balance.*;

public enum BodyPart {
    HEAD ("head",  "h",  HEAD_HIT_MOD,  HEAD_DMG_MULT),
    TORSO("torso", "t",  TORSO_HIT_MOD, TORSO_DMG_MULT),
    ARM_L("arm.l", "la", ARM_L_HIT_MOD, ARM_L_DMG_MULT),
    ARM_R("arm.r", "ra", ARM_R_HIT_MOD, ARM_R_DMG_MULT),
    LEGS ("legs",  "l",  LEGS_HIT_MOD,  LEGS_DMG_MULT);

    public final String path, shortName;
    public final int hitMod;
    public final double damageMult;

    BodyPart(String path, String shortName, int hitMod, double damageMult) {
        this.path = path; this.shortName = shortName;
        this.hitMod = hitMod; this.damageMult = damageMult;
    }

    /** Accepts "head", "h", "arm.l", "la", ... Returns null when unknown. */
    public static BodyPart parse(String s) {
        if (s == null) return null;
        String v = s.toLowerCase();
        for (BodyPart p : values())
            if (p.path.equals(v) || p.shortName.equals(v)) return p;
        return null;
    }
}