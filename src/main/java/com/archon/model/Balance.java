package com.archon.model;

/**
 * Central gameplay tuning constants (C++ constexpr-style header).
 * Single source of truth for combat, body-part aiming, power levels, AI, and economy numbers.
 */
public final class Balance {
    private Balance() {}

    // --- Probability Clamps ---
    public static final int MIN_CHANCE = 5;
    public static final int MAX_CHANCE = 95;

    public static int clampChance(int chance) {
        return Math.clamp(chance, MIN_CHANCE, MAX_CHANCE);
    }

    // --- Body Part Aiming Modifiers (hitChance %, melee damageMult) ---
    public static final int HEAD_HIT_MOD         = -30;
    public static final double HEAD_DMG_MULT     = 2.5;
    public static final int TORSO_HIT_MOD        = +10;
    public static final double TORSO_DMG_MULT    = 1.0;
    public static final int ARM_L_HIT_MOD        = -10;
    public static final double ARM_L_DMG_MULT    = 1.0;
    public static final int ARM_R_HIT_MOD        = -10;
    public static final double ARM_R_DMG_MULT    = 1.0;
    public static final int LEGS_HIT_MOD         = -5;
    public static final double LEGS_DMG_MULT     = 0.8;

    // --- Melee Power Levels (AP cost, hitChance %, damageMult, crippleChance %) ---
    public static final int LIGHT_AP_COST        = 1;
    public static final int LIGHT_HIT_MOD        = +20;
    public static final double LIGHT_DMG_MULT    = 0.6;
    public static final int LIGHT_CRIPPLE_CHANCE = 15;

    public static final int NORMAL_AP_COST       = 2;
    public static final int NORMAL_HIT_MOD       = 0;
    public static final double NORMAL_DMG_MULT   = 1.0;
    public static final int NORMAL_CRIPPLE_CHANCE= 35;

    public static final int HEAVY_AP_COST        = 3;
    public static final int HEAVY_HIT_MOD        = -20;
    public static final double HEAVY_DMG_MULT    = 1.6;
    public static final int HEAVY_CRIPPLE_CHANCE = 60;

    // --- Melee & Ranged Combat ---
    public static final int BASE_HIT_CHANCE          = 70;
    public static final int BASE_CRIT_CHANCE         = 5;
    public static final double CRIT_DMG_MULT         = 2.0;
    public static final int UNARMED_DMG_MIN          = 2;
    public static final int UNARMED_DMG_MAX          = 4;
    public static final int MIN_DAMAGE               = 1;
    public static final int FORCE_WEAPON_WEAR        = 1;

    public static final int RANGED_DMG_MIN           = 5;
    public static final int RANGED_DMG_MAX           = 10;
    public static final int RANGED_POINT_BLANK_DIST  = 1;
    public static final int RANGED_OPTIMAL_DIST      = 4;
    public static final int RANGED_MEDIUM_DIST       = 8;
    public static final int RANGED_POINT_BLANK_MOD   = -20;
    public static final int RANGED_OPTIMAL_MOD       = 0;
    public static final int RANGED_MEDIUM_MOD        = -10;
    public static final int RANGED_LONG_MOD          = -25;

    // --- Guard & Disarm ---
    public static final int GUARD_ARMOR_BONUS_PCT    = 30;
    public static final int GUARD_MELEE_HIT_PENALTY  = 25;
    public static final int DISARM_CHANCE_BASE       = 45;
    public static final int DISARM_CHANCE_CRIPPLED   = 70;

    // --- Crippled Limb & Condition Penalties ---
    public static final int CRIPPLED_ARM_HIT_PENALTY = 20;
    public static final double CRIPPLED_ARM_DMG_MULT = 0.75;
    public static final int CRIPPLED_HEAD_EVA_PENALTY= 15;
    public static final int CRIPPLED_LEGS_MOVE_CHANCE= 50;

    public static final int HEAD_BLIND_CHANCE        = 40;
    public static final int BLIND_HIT_PENALTY        = 30;
    public static final int BLIND_EVA_PENALTY        = 15;
    public static final int BLIND_RECOVER_CHANCE     = 50;

    public static final int OIL_SLIP_CHANCE          = 40;
    public static final int PRONE_EVA_PENALTY        = 20;

    // --- Movement, Stealth & Thievery ---
    public static final int CAREFUL_STEP_BASE_CHANCE = 80;
    public static final int SNATCH_BASE_CHANCE       = 35;

    // --- Environment ---
    public static final int BURNING_TICK_DAMAGE      = 3;

    // --- AI Tuning ---
    public static final int AI_ACTIONS_PER_TURN      = 2;
    public static final int AI_AIM_LEGS_CHANCE       = 25;
    public static final int AI_STEP_DIST_WEIGHT      = 10;
    public static final int AI_STEP_BURN_PENALTY     = 50;
    public static final int AI_STEP_OIL_PENALTY      = 3;

    // --- Economy & Inventory Defaults ---
    public static final int DEFAULT_ROUND_AP         = 6;
    public static final int DEFAULT_UNTAXED_LINES    = 1;
    public static final int DEFAULT_LINE_TAX         = 1;
    public static final int DEFAULT_BREAK_PENALTY    = 1;
    public static final int PACK_MAX                 = 8;
    public static final int DEFAULT_STRENGTH         = 3;
}
