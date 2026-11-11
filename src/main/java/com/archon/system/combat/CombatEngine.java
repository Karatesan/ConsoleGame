    package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.EquipmentSlot;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.model.Tag;
import com.archon.model.World;
import com.archon.system.environment.Destruction;
import com.archon.system.item.ItemMoves;

import static com.archon.model.Balance.*;

/**
 * Domain service encapsulating combat calculations and state mutations
 * for melee strikes, ranged attacks, and disarm maneuvers.
 */
public final class CombatEngine {
    private CombatEngine() {}

    public record MeleeHitResult(boolean hit, int hitChance, int damage, BodyPart part, Item weapon, boolean killed,
                                 boolean weaponDegraded, boolean crippledPart, boolean crit, boolean blindedTarget,
                                 Destruction.Result destruction) {}

    public record DisarmResult(boolean success, Item weapon) {}

    public record RangedHitResult(boolean hit, int hitChance, int damage, BodyPart part, boolean killed,
                                  boolean crippledPart, boolean crit, boolean blindedTarget,
                                  Destruction.Result destruction) {}

    public static MeleeHitResult resolveMelee(Dice dice, World world, Actor attacker, Entity target, BodyPart part,
                                              boolean aimed, String power, boolean force) {
        int powerHitMod = "light".equals(power) ? LIGHT_HIT_MOD : "heavy".equals(power) ? HEAVY_HIT_MOD : NORMAL_HIT_MOD;
        int aimMod = (aimed && part != null) ? part.hitMod : 0;
        int hitChance = BASE_HIT_CHANCE + aimMod + powerHitMod - effectiveEvasion(target);
        if (attacker.isCrippled(BodyPart.ARM_R)) {
            hitChance -= CRIPPLED_ARM_HIT_PENALTY;
        }
        if (attacker.has(Tag.BLIND)) {
            hitChance -= BLIND_HIT_PENALTY;
        }
        if (target instanceof Actor actor && actor.isGuarded() && !force) {
            hitChance -= GUARD_MELEE_HIT_PENALTY;
        }

        hitChance = Balance.clampChance(hitChance);
        Item weapon = attacker.mainHand();
        if (!dice.chance(hitChance)) {
            return new MeleeHitResult(false, hitChance, 0, part, weapon, false, false, false, false, false, Destruction.Result.EMPTY);
        }

        int baseDamage = rollMeleeDamage(dice, attacker);
        double powerDmgMult = "light".equals(power) ? LIGHT_DMG_MULT : "heavy".equals(power) ? HEAVY_DMG_MULT : NORMAL_DMG_MULT;
        double partDmgMult = part != null ? part.damageMult : 1.0;
        double multiplier = partDmgMult * powerDmgMult;
        if (attacker.isCrippled(BodyPart.ARM_R)) {
            multiplier *= CRIPPLED_ARM_DMG_MULT;
        }

        // Fallout 2 Critical Hit Math: Base + Targeted Shot Crit Bonus
        int critBonus = (aimed && part != null) ? part.critBonus : 0;
        int critChance = Balance.clampChance(BASE_CRIT_CHANCE + critBonus);
        boolean crit = dice.chance(critChance);
        if (crit) {
            multiplier *= CRIT_DMG_MULT;
        }

        // Critical hits bypass armor (Fallout 2 "Ignores Armor" rule)
        int armor = crit ? 0 : effectiveArmor(target);
        int damage = Math.max(MIN_DAMAGE, (int) Math.round(baseDamage * multiplier) - armor);
        target.takeDamage(damage);

        boolean weaponDegraded = force && weapon != null;
        if (weaponDegraded) {
            weapon.wear(FORCE_WEAPON_WEAR);
        }

        boolean killed = !target.alive();
        boolean crippledPart = !killed && part != null && rollCripple(dice, target, part, power);
        boolean blindedTarget = !killed && part != null && rollBlind(dice, target, part);
        Destruction.Result destruction = Destruction.Result.EMPTY;
        if (killed) {
            destruction = Destruction.onKilled(world, target);
        }
        return new MeleeHitResult(true, hitChance, damage, part, weapon, killed, weaponDegraded, crippledPart, crit, blindedTarget, destruction);
    }

    public static int rollMeleeDamage(Dice dice, Actor attacker) {
        Item weapon = attacker.mainHand();
        int roll = weapon == null
                ? dice.between(UNARMED_DMG_MIN, UNARMED_DMG_MAX)
                : dice.between(weapon.minDamage(), weapon.maxDamage());
        return roll + attacker.strength();
    }

    public static int baseMeleeDamage(Actor attacker) {
        Item weapon = attacker.mainHand();
        int avg = weapon == null
                ? (UNARMED_DMG_MIN + UNARMED_DMG_MAX) / 2
                : (weapon.minDamage() + weapon.maxDamage()) / 2;
        return avg + attacker.strength();
    }

    public static DisarmResult attemptDisarm(Dice dice, World world, Actor owner, EquipmentSlot slot) {
        if (slot != EquipmentSlot.HAND_LEFT && slot != EquipmentSlot.HAND_RIGHT) {
            throw new IllegalArgumentException("disarm requires a hand slot");
        }

        Item weapon = owner.inventory().equipped(slot);
        if (weapon == null) {
            return new DisarmResult(false, null);
        }
        int disarmChance = owner.isCrippled(slot == EquipmentSlot.HAND_RIGHT ? BodyPart.ARM_R : BodyPart.ARM_L)
                ? DISARM_CHANCE_CRIPPLED : DISARM_CHANCE_BASE;
        if (!dice.chance(disarmChance)) {
            return new DisarmResult(false, weapon);
        }

        ItemMoves.drop(world.map(), weapon, new ItemLocation.Equipment(owner, slot)).requireSuccess();
        return new DisarmResult(true, weapon);
    }

    public static RangedHitResult resolveRanged(Dice dice, World world, Actor attacker, Entity target, BodyPart part, boolean aimed) {
        int distance = target.pos().chebyshev(attacker.pos());
        int rangeModifier = distance <= RANGED_POINT_BLANK_DIST ? RANGED_POINT_BLANK_MOD
                : distance <= RANGED_OPTIMAL_DIST ? RANGED_OPTIMAL_MOD
                : distance <= RANGED_MEDIUM_DIST ? RANGED_MEDIUM_MOD : RANGED_LONG_MOD;
        int armPenalty = (attacker.isCrippled(BodyPart.ARM_L) || attacker.isCrippled(BodyPart.ARM_R))
                ? -CRIPPLED_ARM_HIT_PENALTY : 0;
        int blindPenalty = attacker.has(Tag.BLIND) ? -BLIND_HIT_PENALTY : 0;
        int aimMod = (aimed && part != null) ? part.hitMod : 0;
        int hitChance = Balance.clampChance(BASE_HIT_CHANCE + aimMod + rangeModifier + armPenalty + blindPenalty - effectiveEvasion(target));

        if (!dice.chance(hitChance)) {
            return new RangedHitResult(false, hitChance, 0, part, false, false, false, false, Destruction.Result.EMPTY);
        }

        Item weapon = attacker.mainHand();
        int baseDamage = (weapon != null && weapon.maxDamage() > 0)
                ? dice.between(weapon.minDamage(), weapon.maxDamage())
                : dice.between(RANGED_DMG_MIN, RANGED_DMG_MAX);
        double multiplier = part != null ? part.damageMult : 1.0;

        // Fallout 2 Critical Hit Math: Base + Targeted Shot Crit Bonus
        int critBonus = (aimed && part != null) ? part.critBonus : 0;
        int critChance = Balance.clampChance(BASE_CRIT_CHANCE + critBonus);
        boolean crit = dice.chance(critChance);
        if (crit) {
            multiplier *= CRIT_DMG_MULT;
        }

        // Critical hits bypass armor (Fallout 2 "Ignores Armor" rule)
        int armor = crit ? 0 : effectiveArmor(target);
        int damage = Math.max(MIN_DAMAGE, (int) Math.round(baseDamage * multiplier) - armor);
        target.takeDamage(damage);

        boolean killed = !target.alive();
        boolean crippledPart = !killed && part != null && rollCripple(dice, target, part, "normal");
        boolean blindedTarget = !killed && part != null && rollBlind(dice, target, part);
        Destruction.Result destruction = Destruction.Result.EMPTY;
        if (killed) {
            destruction = Destruction.onKilled(world, target);
        }
        return new RangedHitResult(true, hitChance, damage, part, killed, crippledPart, crit, blindedTarget, destruction);
    }

    private static int effectiveArmor(Entity target) {
        int base = target.armor();
        return (target instanceof Actor a && a.isGuarded())
                ? Math.max(base + 1, (int) Math.round(base * (1.0 + GUARD_ARMOR_BONUS_PCT / 100.0)))
                : base;
    }

    private static int effectiveEvasion(Entity target) {
        int eva = target.evasion();
        if (target instanceof Actor a && a.isCrippled(BodyPart.HEAD)) {
            eva -= CRIPPLED_HEAD_EVA_PENALTY;
        }
        if (target.has(Tag.BLIND)) {
            eva -= BLIND_EVA_PENALTY;
        }
        if (target.has(Tag.PRONE)) {
            eva -= PRONE_EVA_PENALTY;
        }
        return eva;
    }

    private static boolean rollCripple(Dice dice, Entity target, BodyPart part, String power) {
        if (!(target instanceof Actor actor) || part == BodyPart.TORSO || actor.isCrippled(part)) {
            return false;
        }
        int chance = "heavy".equals(power) ? HEAVY_CRIPPLE_CHANCE
                : "light".equals(power) ? LIGHT_CRIPPLE_CHANCE : NORMAL_CRIPPLE_CHANCE;
        return dice.chance(chance) && actor.cripple(part);
    }

    private static boolean rollBlind(Dice dice, Entity target, BodyPart part) {
        if (part != BodyPart.HEAD || target.has(Tag.BLIND) || !dice.chance(HEAD_BLIND_CHANCE)) {
            return false;
        }
        target.applyTag(Tag.BLIND);
        if (target instanceof Actor actor) {
            actor.clearReadied();
        }
        return true;
    }

    public static void dropMainHand(World world, Actor actor) {
        Item item = actor.mainHand();
        if (item != null) {
            EquipmentSlot slot = actor.inventory().equipped(
                    EquipmentSlot.HAND_RIGHT) == item ? EquipmentSlot.HAND_RIGHT : EquipmentSlot.HAND_LEFT;
            ItemMoves.drop(world.map(), item, new ItemLocation.Equipment(actor, slot)).requireSuccess();
        }
    }
}