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

    // --- Body Part Aiming Modifiers (hitChance %, melee damageMult, critChanceBonus %) ---
    public static final int HEAD_HIT_MOD         = -40;
    public static final double HEAD_DMG_MULT     = 2.5;
    public static final int HEAD_CRIT_BONUS      = 35;
    public static final int TORSO_HIT_MOD        = 0;
    public static final double TORSO_DMG_MULT    = 1.0;
    public static final int TORSO_CRIT_BONUS     = 0;
    public static final int ARM_L_HIT_MOD        = -20;
    public static final double ARM_L_DMG_MULT    = 1.0;
    public static final int ARM_L_CRIT_BONUS     = 15;
    public static final int ARM_R_HIT_MOD        = -20;
    public static final double ARM_R_DMG_MULT    = 1.0;
    public static final int ARM_R_CRIT_BONUS     = 15;
    public static final int LEGS_HIT_MOD         = -15;
    public static final double LEGS_DMG_MULT     = 0.8;
    public static final int LEGS_CRIT_BONUS      = 10;

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

    public static final int AIM_EXTRA_AP         = 1;

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
    public static final int DEFAULT_ROUND_AP         = 60   ;
    public static final int DEFAULT_UNTAXED_LINES    = 100;
    public static final int DEFAULT_LINE_TAX         = 1;
    public static final int DEFAULT_BREAK_PENALTY    = 1;
    public static final int PACK_MAX                 = 8;
    public static final int DEFAULT_STRENGTH         = 3;

    // --- Psionic Physics Magic v2: 1A. Temperature Thresholds ---
    public static final int ABS_ZERO           = -273;
    public static final int DEEP_FREEZE_TEMP   = -20;
    public static final int FREEZE_TEMP        = 0;
    public static final int CHILLED_MAX_TEMP   = 15;
    public static final int AMBIENT_TEMP       = 20;
    public static final int BODY_TEMP          = 36;
    public static final int WARM_MELT_TEMP     = 36;
    public static final int BOIL_TEMP                  = 100;
    public static final int STEAM_CLEAR_TEMP           = 80;
    public static final int SUPERHEATED_STEAM_TEMP     = 140;
    public static final int SUPERHEATED_INFERNO_TEMP   = 180;
    public static final int IGNITE_TEMP                = 200;
    public static final int BURN_CAP                   = 250;
    public static final int INFERNO_MIN_TEMP           = 300;
    public static final int MAX_CREATURE_TEMP          = 500;

    // --- 1B. Temperature Change Per Round ---
    public static final int CHILL_WARM_STEP       = 10;
    public static final int CREATURE_HOT_COOL_STEP= 30;
    public static final int TILE_DECAY_STEP       = 10;
    public static final int TILE_HOT_COOL_STEP    = 60;
    public static final double FROZEN_DELTA_MULT  = 0.5;
    public static final int THAW_RESET_TEMP       = 36;

    // --- 1C. Action Points ---
    public static final int AP_PER_ROUND       = 6;
    public static final int AP_SIPHON          = 2;
    public static final int AP_PUSH            = 2;
    public static final int AP_PULL            = 2;
    public static final int AP_BOLT_PAYLOAD    = 1;
    public static final int AP_BOLT_KIN        = 2;
    public static final int AP_BOLT_KIN_AIMED  = 3;
    public static final int AP_BLAST           = 2;
    public static final int AP_CHANNEL_THERMAL = 0;
    public static final int AP_CHANNEL_FORCE   = 1;
    public static final int AP_SIPHON_BLOOD    = 3;
    public static final int AP_BOLT_BLOOD      = 2;
    public static final int AP_BLAST_BLOOD     = 3;
    public static final int AP_CHANNEL_BLOOD   = 0;
    public static final int BLOOD_SURGE_AP     = 3;
    public static final int AP_PASS_R          = 0;
    public static final int AP_STAND_UP        = 1;

    // --- 1D. Magic, Damage and Temperature Deltas ---
    public static final int BASE_MAGIC           = 3;
    public static final int DMG_KIN              = 3;
    public static final int DMG_KIN_MIN          = 2;
    public static final int DMG_KIN_MAX          = 5;
    public static final int DMG_HEAT                   = 1;
    public static final int DMG_INFERNO                = 3;
    public static final int DMG_ACID                   = 4;
    public static final int DMG_BLOOD                  = 10;
    public static final int DMG_BLOOD_BLAST            = 9;
    public static final int BOLT_DMG_VAR               = 1;
    public static final int BLOOD_BOLT_VAR             = 2;
    public static final double BLAST_DMG_MULT          = 0.5;
    public static final int BLAST_ARM_PRONE_CHANCE     = 20;
    public static final int TD_HEAT                    = 100;
    public static final int TD_INFERNO                 = 200;
    public static final int TD_WATER                   = -10;
    public static final int SIPHON_COOL_DELTA          = -30;
    public static final int SUPERHEATED_STEAM_TEMP_DELTA = 60;
    public static final int FUEL_AIR_DMG_HEAT          = 8;
    public static final int FUEL_AIR_DMG_INFERNO       = 10;
    public static final int TAR_DMG                    = 1;
    public static final int STONE_STRESS_DMG           = 1;

    // --- 1E. Status Damage and Durations ---
    public static final int SCALD_FLASH_DMG           = 5;
    public static final int SCALD_DOT_DMG             = 3;
    public static final int BURN_TICK_DMG             = 5;
    public static final int BURN_TICK_BASE            = 3;
    public static final int BURN_TEMP_STEP            = 20;
    public static final int FROSTBITE_DMG             = 3;
    public static final int CRYO_DMG                  = 6;
    public static final int CRYO_ARMOR_SHRED          = 1;
    public static final int THERMAL_SHOCK_DMG         = 12;
    public static final int THERMAL_SHOCK_ARMOR_SHRED = 1;
    public static final int SHATTER_BASE_DMG          = 8;
    public static final int SHATTER_ARMOR_SHRED       = 1;
    public static final int ACID_ARMOR_SHRED          = 1;
    public static final int CAUSTIC_DMG               = 3;
    public static final int BLEED_DMG                 = 2;
    public static final int BLEED_TURNS               = 3;
    public static final int CRIT_BLEED_BONUS_TURNS    = 2;
    public static final int CAUTERIZE_SCALD_DMG       = 3;
    public static final int OIL_TURNS                 = 3;
    public static final int PROP_BURN_TURNS           = 3;
    public static final int TAR_BURN_TURNS            = 6;
    public static final int WET_TURNS                 = 3;
    public static final int SLIP_CHANCE               = 40;
    public static final int EVA_CHILLED               = -15;
    public static final int EVA_FROZEN                = -20;
    public static final int CHILLED_AP_STEP           = 1;
    public static final int CRIPPLED_AP_STEP          = 1;
    public static final int TAR_AP_STEP               = 1;
    public static final int BLIND_LINGER_TURNS        = 1;
    public static final int WEAPON_CLATTER_WEAR       = 1;

    // --- 1F. Range, Force, Movement and Collision ---
    public static final int PUSH_RANGE              = 5;
    public static final int BOLT_RANGE              = 8;
    public static final int BLAST_RANGE             = 6;
    public static final int FORCE_COLLISION_DMG     = 6;
    public static final int SLAM_MIN_TRAVEL         = 1;
    public static final int RES_GAS                 = 0;
    public static final int RES_LIGHT               = 1;
    public static final int RES_MEDIUM              = 2;
    public static final int RES_HEAVY               = 99;
    public static final int FORCE_FOCUS_BONUS       = 2;
    public static final int SIPHON_REACH_CHEBYSHEV  = 1;
    public static final int SIPHON_REACH_MANHATTAN  = 4;

    // --- 1G. Channel and Concentration ---
    public static final int CHANNEL_MAX_TURNS = 2;
    public static final int CONC_BASE         = 75;
    public static final int CONC_PER_DMG      = 3;

    // --- 1H. Hit, Crit and Aimed Zones ---
    public static final int BASE_HIT                   = 80;
    public static final int BASE_CRIT                  = 5;
    public static final int SEVERITY_TIERS             = 3;
    public static final double CRIT_MULT_TORSO         = 1.5;
    public static final double CRIT_MULT_LIMB_T1       = 2.0;
    public static final double CRIT_MULT_LIMB_T2       = 2.5;
    public static final double CRIT_MULT_LIMB_T3       = 3.0;
    public static final double CRIT_MULT_HEAD_T1       = 2.0;
    public static final double CRIT_MULT_HEAD_T2       = 3.0;
    public static final double CRIT_MULT_HEAD_T3       = 4.0;
    public static final double CRIT_HEAT_TEMP_MULT     = 1.5;
    public static final int AIMED_NONCRIT_STATUS_CHANCE= 20;
    public static final int CRIT_STATUS_CHANCE_T1      = 60;
    public static final int CRIT_STATUS_CHANCE_T2      = 80;
    public static final int CRIT_STATUS_CHANCE_T3      = 90;
    public static final int ARM_DISARM_CHANCE_T1       = 20;
    public static final int ARM_DISARM_CHANCE_T2       = 45;
    public static final int ARM_DISARM_CHANCE_T3       = 75;
    public static final int LEG_CRIPPLE_CHANCE_T2      = 45;
    public static final int LEG_CRIPPLE_CHANCE_T3      = 75;
    public static final int ZONE_HEAD_HIT              = -25;
    public static final int ZONE_HEAD_CRIT             = 30;
    public static final int ZONE_ARM_HIT               = -15;
    public static final int ZONE_ARM_CRIT              = 15;
    public static final int ZONE_LEGS_HIT              = -10;
    public static final int ZONE_LEGS_CRIT             = 10;
    public static final int DAZED_TURNS                = 2;
    public static final int DAZED_HIT_PENALTY          = -20;
    public static final int SPELL_BLIND_HIT_PENALTY    = -35;
    public static final int BLIND_MAX_RANGE            = 1;
    public static final int STUN_TURNS                 = 2;
    public static final int NUMB_ARM_TURNS             = 2;
    public static final double NUMB_ARM_DMG_MULT       = 0.5;
    public static final int CRIPPLED_TURNS             = 3;
    public static final int LEG_RESIST_BASE            = 80;
    public static final int LEG_RESIST_PER_STR         = 5;

    // --- 1I. Forbidden (Blood) Magic ---
    public static final int HP_COST_BOLT_BLOOD      = 8;
    public static final int HP_COST_BLAST_BLOOD     = 10;
    public static final int HP_COST_SIPHON_BLOOD    = 6;
    public static final int HP_COST_CHANNEL_BLOOD   = 7;
    public static final int BLOOD_BOIL_BASE         = 7;
    public static final int BLOOD_SIPHON_BLEED_MULT = 4;
    public static final int BLOOD_HEMORRHAGE_DMG    = 4;
    public static final int BLOOD_KILL_HEAL         = 5;
    public static final int BLOOD_NOVA_KILL_HEAL    = 4;
}
