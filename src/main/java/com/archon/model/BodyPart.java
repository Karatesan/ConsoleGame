package com.archon.model;

public enum BodyPart {
    HEAD ("head",  "h",  -30, 2.5),
    TORSO("torso", "t",  +10, 1.0),
    ARM_L("arm.l", "la", -10, 1.0),
    ARM_R("arm.r", "ra", -10, 1.0),
    LEGS ("legs",  "l",   -5, 0.8);

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