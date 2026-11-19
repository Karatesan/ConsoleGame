package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.model.Tag;
import com.archon.model.World;
import com.archon.system.item.ItemMoves;
import com.archon.system.physics.EntityReactions;
import com.archon.system.physics.ThermalState;

import java.util.List;

/** Tab 3D/3E spell hit, crit, 3-tier severity ladder, and aimed/element crit status math. */
public final class SpellCombat {

    public record RollResult(
            boolean hit,
            boolean crit,
            int hitRoll,
            int critRoll,
            int hitChance,
            int critChance,
            int severityTier,
            double critMult,
            boolean ignoreArmor
    ) {}

    private SpellCombat() {}

    public static int effectiveEvasion(Entity target) {
        return CombatEngine.effectiveEvasion(target);
    }

    public static int zoneHitMod(BodyPart zone) {
        if (zone == null) return 0;
        return switch (zone) {
            case HEAD -> Balance.ZONE_HEAD_HIT;
            case ARM_L, ARM_R -> Balance.ZONE_ARM_HIT;
            case LEGS -> Balance.ZONE_LEGS_HIT;
            default -> 0;
        };
    }

    public static int zoneCritMod(BodyPart zone) {
        if (zone == null) return 0;
        return switch (zone) {
            case HEAD -> Balance.ZONE_HEAD_CRIT;
            case ARM_L, ARM_R -> Balance.ZONE_ARM_CRIT;
            case LEGS -> Balance.ZONE_LEGS_CRIT;
            default -> 0;
        };
    }

    public static int hitChance(Actor caster, Entity target, BodyPart zone) {
        if (target == null || target.kind() != Entity.Kind.CREATURE) {
            return 100;
        }
        int casterMods = 0;
        if (caster != null) {
            if (caster.has(Tag.DAZED)) casterMods += Balance.DAZED_HIT_PENALTY;
            if (caster.has(Tag.BLIND)) casterMods += Balance.SPELL_BLIND_HIT_PENALTY;
        }
        return Math.clamp(Balance.BASE_HIT - effectiveEvasion(target) + zoneHitMod(zone) + casterMods, 0, 100);
    }

    public static int critChance(int hitChance, BodyPart zone, Tag payload) {
        if (hitChance <= 0 || payload == Tag.WATER || payload == Tag.OIL) {
            return 0;
        }
        return Math.clamp(Balance.BASE_CRIT + zoneCritMod(zone), 0, 100);
    }

    public static double critMult(BodyPart zone, int severityTier) {
        if (severityTier <= 0) {
            return 1.0;
        }
        if (zone == null || zone == BodyPart.TORSO) {
            return Balance.CRIT_MULT_TORSO;
        }
        if (zone == BodyPart.HEAD) {
            return switch (severityTier) {
                case 1 -> Balance.CRIT_MULT_HEAD_T1;
                case 2 -> Balance.CRIT_MULT_HEAD_T2;
                default -> Balance.CRIT_MULT_HEAD_T3;
            };
        }
        return switch (severityTier) {
            case 1 -> Balance.CRIT_MULT_LIMB_T1;
            case 2 -> Balance.CRIT_MULT_LIMB_T2;
            default -> Balance.CRIT_MULT_LIMB_T3;
        };
    }

    public static RollResult rollAttack(Dice dice, int hitChance, int critChance, BodyPart zone) {
        int hitRoll = dice.check(hitChance).roll();
        boolean hit = hitChance > 0 && hitRoll <= hitChance;
        if (!hit) {
            return new RollResult(false, false, hitRoll, 0, hitChance, critChance, 0, 1.0, false);
        }
        int critRoll = critChance > 0 ? dice.check(critChance).roll() : 0;
        boolean crit = critChance > 0 && critRoll <= critChance;
        int tier = !crit ? 0 : Math.min(Balance.SEVERITY_TIERS,
                1 + (int) Math.floor((double) Balance.SEVERITY_TIERS * (critChance - critRoll) / critChance));
        double mult = crit ? critMult(zone, tier) : 1.0;
        boolean ignoreArmor = tier >= 2;
        return new RollResult(true, crit, hitRoll, critRoll, hitChance, critChance, tier, mult, ignoreArmor);
    }

    public static RollResult roll(Dice dice, Actor caster, Entity target, BodyPart zone, Tag payload) {
        if (target == null || target.kind() != Entity.Kind.CREATURE) {
            return new RollResult(true, false, 1, 0, 100, 0, 0, 1.0, false);
        }
        int hc = hitChance(caster, target, zone);
        int cc = critChance(hc, zone, payload);
        return rollAttack(dice, hc, cc, zone);
    }

    public static int applyBoltStatusesAndBonuses(
            Dice dice, World world, Entity target, BodyPart zone, boolean aimed,
            Tag payload, Item pipedItem, RollResult roll, List<String> log) {
        return applyBoltStatusesAndBonuses(dice, world, target, zone, aimed, payload, pipedItem, roll, false, log);
    }

    public static int applyBoltStatusesAndBonuses(
            Dice dice, World world, Entity target, BodyPart zone, boolean aimed,
            Tag payload, Item pipedItem, RollResult roll, boolean reactionConsumed, List<String> log) {
        if (target == null || target.kind() != Entity.Kind.CREATURE || !roll.hit()) {
            return 0;
        }
        if (!target.alive()) {
            if (aimed && zone == BodyPart.HEAD && log != null) {
                String fatality = target.name() + "'s head is ripped apart in a psionic fatality!";
                int fallsIdx = log.indexOf(target.name() + " falls.");
                if (fallsIdx >= 0) log.add(fallsIdx, fatality);
                else log.add(fatality);
            }
            return 0;
        }

        int extraDmg = 0;
        int tier = roll.severityTier();
        int critStatusChance = tier == 1 ? Balance.CRIT_STATUS_CHANCE_T1
                : tier == 2 ? Balance.CRIT_STATUS_CHANCE_T2 : Balance.CRIT_STATUS_CHANCE_T3;

        if (aimed && zone != null && zone != BodyPart.TORSO) {
            if (!roll.crit()) {
                switch (zone) {
                    case HEAD -> {
                        if (dice.chance(Balance.AIMED_NONCRIT_STATUS_CHANCE)) {
                            applyDazed(target, log);
                        }
                    }
                    case ARM_L, ARM_R -> {
                        if (dice.chance(Balance.AIMED_NONCRIT_STATUS_CHANCE)) {
                            applyNumbArm(target, zone, log);
                        }
                    }
                    case LEGS -> {
                        int str = target instanceof Actor a ? a.strength() : 0;
                        int resist = Math.clamp(Balance.LEG_RESIST_BASE + str * Balance.LEG_RESIST_PER_STR,
                                100 - Balance.AIMED_NONCRIT_STATUS_CHANCE, 100);
                        int r = dice.between(1, 100);
                        if (r > resist) {
                            ThermalState.applyProne(target);
                            ThermalState.addLog(log, target.name() + " is swept off their feet!");
                        }
                    }
                    default -> {}
                }
            } else {
                switch (zone) {
                    case HEAD -> {
                        if (dice.chance(critStatusChance)) {
                            applyDazed(target, log);
                        }
                        if (tier == 3 && dice.chance(Balance.CRIT_STATUS_CHANCE_T3)) {
                            target.applyTag(Tag.STUNNED);
                            target.stunTurns = Balance.STUN_TURNS;
                            ThermalState.applyProne(target);
                            ThermalState.addLog(log, target.name() + " is stunned and knocked prone!");
                        }
                    }
                    case ARM_L, ARM_R -> {
                        if (dice.chance(critStatusChance)) {
                            applyNumbArm(target, zone, log);
                        }
                        int disarmChance = tier == 1 ? Balance.ARM_DISARM_CHANCE_T1
                                : tier == 2 ? Balance.ARM_DISARM_CHANCE_T2 : Balance.ARM_DISARM_CHANCE_T3;
                        if (target instanceof Actor actor && dice.chance(disarmChance)) {
                            EquipmentSlot slot = zone == BodyPart.ARM_L ? EquipmentSlot.HAND_LEFT : EquipmentSlot.HAND_RIGHT;
                            Item held = actor.inventory().equipped(slot);
                            if (held != null) {
                                ItemMoves.drop(world.map(), held, new ItemLocation.Equipment(actor, slot)).requireSuccess();
                                ThermalState.addLog(log, actor.name() + " drops " + held.name() + "!");
                            }
                        }
                    }
                    case LEGS -> {
                        if (dice.chance(critStatusChance)) {
                            ThermalState.applyProne(target);
                            ThermalState.addLog(log, target.name() + " is knocked prone!");
                        }
                        if (tier >= 2) {
                            int crippleChance = tier == 2 ? Balance.LEG_CRIPPLE_CHANCE_T2 : Balance.LEG_CRIPPLE_CHANCE_T3;
                            if (dice.chance(crippleChance)) {
                                target.applyTag(Tag.CRIPPLED);
                                target.crippledTurns = Balance.CRIPPLED_TURNS;
                                ThermalState.addLog(log, target.name() + "'s legs are crippled!");
                            }
                        }
                    }
                    default -> {}
                }
            }
            return 0;
        }

        if (!roll.crit()) {
            return 0;
        }

        boolean isStone = pipedItem != null && (pipedItem.has(Tag.STONE) || "stone".equalsIgnoreCase(pipedItem.id()));
        if (payload == Tag.FORCE || isStone) {
            if (dice.chance(critStatusChance)) {
                ThermalState.applyProne(target);
                ThermalState.addLog(log, target.name() + " is slammed prone!");
            }
            if (tier == 3 && dice.chance(Balance.CRIT_STATUS_CHANCE_T3)) {
                applyDazed(target, log);
            }
        } else if ((payload == Tag.FIRE || payload == Tag.INFERNO) && !reactionConsumed) {
            if (!target.has(Tag.WET) && !target.has(Tag.FROZEN) && !target.has(Tag.BURNING)
                    && dice.chance(critStatusChance)) {
                ThermalState.igniteEntity(target);
                ThermalState.addLog(log, target.name() + " ignites from the critical blast!");
            }
            if (tier == 3 && dice.chance(Balance.CRIT_STATUS_CHANCE_T3)) {
                extraDmg += Balance.SCALD_FLASH_DMG;
                ThermalState.addLog(log, "T3 thermal surge (+" + Balance.SCALD_FLASH_DMG + " scald)!");
                ThermalState.damageEntity(world, target, Balance.SCALD_FLASH_DMG, false, log);
            }
        } else if (payload == Tag.ACID) {
            if (!target.has(Tag.FROZEN)) {
                if (dice.chance(critStatusChance)) {
                    ThermalState.shredArmor(target, Balance.ACID_ARMOR_SHRED);
                    ThermalState.addLog(log, target.name() + "'s armor corrodes further (-" + Balance.ACID_ARMOR_SHRED + " -> " + target.armor() + ")!");
                }
                if (tier == 3 && dice.chance(Balance.CRIT_STATUS_CHANCE_T3)) {
                    ThermalState.applyAcidToFloorOnly(world, target.pos());
                    ThermalState.addLog(log, "Acid spills across " + target.pos() + "!");
                }
            }
        } else if (payload == Tag.BLOOD) {
            if (target.has(Tag.BLEEDING)) {
                if (dice.chance(critStatusChance)) {
                    target.bleedTurns += Balance.CRIT_BLEED_BONUS_TURNS;
                    ThermalState.addLog(log, "Bleeding deepens (+" + Balance.CRIT_BLEED_BONUS_TURNS + "t)!");
                }
                if (tier == 3 && dice.chance(Balance.CRIT_STATUS_CHANCE_T3)) {
                    extraDmg += Balance.BLEED_DMG;
                    ThermalState.addLog(log, "T3 blood hemorrhage (+" + Balance.BLEED_DMG + " bleed)!");
                    ThermalState.damageEntity(world, target, Balance.BLEED_DMG, false, log);
                }
            }
        }
        return extraDmg;
    }

    private static void applyDazed(Entity target, List<String> log) {
        target.applyTag(Tag.DAZED);
        target.dazedTurns = Balance.DAZED_TURNS;
        ThermalState.clearActiveDefense(target);
        ThermalState.addLog(log, target.name() + " is dazed!");
    }

    private static void applyNumbArm(Entity target, BodyPart zone, List<String> log) {
        target.applyTag(Tag.NUMB_ARM);
        target.numbArmTurns = Balance.NUMB_ARM_TURNS;
        target.numbArmPart = zone;
        ThermalState.addLog(log, target.name() + "'s " + zone.path + " goes numb!");
    }
}
