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
import com.archon.system.physics.ThermalState;

import java.util.List;

import static com.archon.model.Balance.*;

/**
 * Domain service encapsulating combat calculations and state mutations
 * for melee strikes, ranged attacks, and disarm maneuvers.
 */
public final class CombatEngine {
    private CombatEngine() {}

    public record HitResult(boolean hit, int hitRoll, int hitChance, int damage, int armorBlocked,
                             BodyPart part, Item weapon, boolean crit, int critRoll, int critChance,
                             boolean killed, boolean weaponDegraded, boolean crippledPart,
                             Tag statusApplied, Destruction.Result destruction) {
        public boolean blindedTarget() {
            return statusApplied == Tag.BLIND;
        }
    }

    public record DisarmResult(boolean success, Item weapon) {}

    public static HitResult resolveMelee(Dice dice, World world, Actor attacker, Entity target, BodyPart part,
                                         boolean aimed, String power, boolean force) {
        return resolveMelee(dice, world, attacker, target, part, aimed, power, force, null);
    }

    public static HitResult resolveMelee(Dice dice, World world, Actor attacker, Entity target, BodyPart part,
                                         boolean aimed, String power, boolean force, List<String> log) {
        int powerHitMod = "light".equals(power) ? LIGHT_HIT_MOD : "heavy".equals(power) ? HEAVY_HIT_MOD : NORMAL_HIT_MOD;
        int aimMod = (aimed && part != null) ? part.hitMod : 0;
        int hitChance = BASE_HIT_CHANCE + aimMod + powerHitMod - effectiveEvasion(target);
        if (attacker.isCrippled(BodyPart.ARM_R)) {
            hitChance -= CRIPPLED_ARM_HIT_PENALTY;
        }
        if (attacker.has(Tag.BLIND)) {
            hitChance -= BLIND_HIT_PENALTY;
        }
        if (attacker.has(Tag.DAZED)) {
            hitChance += DAZED_HIT_PENALTY;
        }
        if (target instanceof Actor actor && actor.isGuarded() && !force) {
            hitChance -= GUARD_MELEE_HIT_PENALTY;
        }

        hitChance = Balance.clampChance(hitChance);
        Dice.Roll hitRoll = dice.check(hitChance);
        Item weapon = attacker.mainHand();
        if (!hitRoll.hit()) {
            return new HitResult(false, hitRoll.roll(), hitChance, 0, 0, part, weapon, false, 0, 0, false, false, false, null, Destruction.Result.EMPTY);
        }

        int baseDamage = rollMeleeDamage(dice, attacker);
        double powerDmgMult = "light".equals(power) ? LIGHT_DMG_MULT : "heavy".equals(power) ? HEAVY_DMG_MULT : NORMAL_DMG_MULT;
        double partDmgMult = part != null ? part.damageMult : 1.0;
        double multiplier = partDmgMult * powerDmgMult;
        if (attacker.isCrippled(BodyPart.ARM_R)) {
            multiplier *= CRIPPLED_ARM_DMG_MULT;
        }
        return finishAttack(dice, world, attacker, target, part, aimed, power, force,
                hitRoll, hitChance, weapon, baseDamage, multiplier, log);
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

    public static HitResult resolveRanged(Dice dice, World world, Actor attacker, Entity target, BodyPart part, boolean aimed) {
        return resolveRanged(dice, world, attacker, target, part, aimed, null);
    }

    public static HitResult resolveRanged(Dice dice, World world, Actor attacker, Entity target, BodyPart part,
                                          boolean aimed, List<String> log) {
        int distance = target.pos().chebyshev(attacker.pos());
        int rangeModifier = distance <= RANGED_POINT_BLANK_DIST ? RANGED_POINT_BLANK_MOD
                : distance <= RANGED_OPTIMAL_DIST ? RANGED_OPTIMAL_MOD
                : distance <= RANGED_MEDIUM_DIST ? RANGED_MEDIUM_MOD : RANGED_LONG_MOD;
        int armPenalty = (attacker.isCrippled(BodyPart.ARM_L) || attacker.isCrippled(BodyPart.ARM_R))
                ? -CRIPPLED_ARM_HIT_PENALTY : 0;
        int blindPenalty = attacker.has(Tag.BLIND) ? -BLIND_HIT_PENALTY : 0;
        int dazedPenalty = attacker.has(Tag.DAZED) ? DAZED_HIT_PENALTY : 0;
        int aimMod = (aimed && part != null) ? part.hitMod : 0;
        int hitChance = Balance.clampChance(BASE_HIT_CHANCE + aimMod + rangeModifier + armPenalty + blindPenalty + dazedPenalty - effectiveEvasion(target));

        Dice.Roll hitRoll = dice.check(hitChance);
        if (!hitRoll.hit()) {
            return new HitResult(false, hitRoll.roll(), hitChance, 0, 0, part, null, false, 0, 0, false, false, false, null, Destruction.Result.EMPTY);
        }

        Item weapon = attacker.mainHand();
        int baseDamage = (weapon != null && weapon.maxDamage() > 0)
                ? dice.between(weapon.minDamage(), weapon.maxDamage())
                : dice.between(RANGED_DMG_MIN, RANGED_DMG_MAX);
        double multiplier = part != null ? part.damageMult : 1.0;
        return finishAttack(dice, world, attacker, target, part, aimed, "normal", false,
                hitRoll, hitChance, weapon, baseDamage, multiplier, log);
    }

    private static HitResult finishAttack(
            Dice dice, World world, Actor attacker, Entity target, BodyPart part,
            boolean aimed, String power, boolean force, Dice.Roll hitRoll, int hitChance,
            Item weapon, int baseDamage, double multiplier, List<String> log) {
        if (isUsingNumbedWeapon(attacker)) {
            multiplier *= NUMB_ARM_DMG_MULT;
        }

        // Fallout 2 Critical Hit Math: Base + Targeted Shot Crit Bonus
        int critBonus = (aimed && part != null) ? part.critBonus : 0;
        int critChance = Balance.clampChance(BASE_CRIT_CHANCE + critBonus);
        Dice.Roll critRoll = dice.check(critChance);
        boolean crit = critRoll.hit();
        if (crit) {
            multiplier *= CRIT_DMG_MULT;
        }

        // Critical hits bypass armor (Fallout 2 "Ignores Armor" rule)
        int rawArmor = effectiveArmor(target);
        int armorBlocked = crit ? 0 : rawArmor;
        int damage = Math.max(MIN_DAMAGE, (int) Math.round(baseDamage * multiplier) - armorBlocked);
        target.takeDamage(damage);

        boolean weaponDegraded = force && weapon != null;
        if (weaponDegraded) {
            weapon.wear(FORCE_WEAPON_WEAR);
        }

        boolean killed = !target.alive();
        boolean crippledPart = !killed && part != null && rollCripple(dice, target, part, power);
        Tag statusApplied = null;
        if (!killed && part != null && rollBlind(dice, target, part)) {
            statusApplied = Tag.BLIND;
        }
        Destruction.Result destruction = Destruction.Result.EMPTY;
        if (killed) {
            destruction = Destruction.onKilled(world, target);
        } else {
            ThermalState.onCreatureDamaged(world, target, damage, false, log);
            killed = !target.alive();
        }
        return new HitResult(true, hitRoll.roll(), hitChance, damage, crit ? rawArmor : armorBlocked,
                part, weapon, crit, critRoll.roll(), critChance, killed, weaponDegraded, crippledPart,
                statusApplied, destruction);
    }

    private static boolean isUsingNumbedWeapon(Actor attacker) {
        if (!attacker.has(Tag.NUMB_ARM) || attacker.numbArmPart == null) {
            return false;
        }
        Item right = attacker.inventory().equipped(EquipmentSlot.HAND_RIGHT);
        if (right != null) {
            return attacker.numbArmPart == BodyPart.ARM_R;
        }
        Item left = attacker.inventory().equipped(EquipmentSlot.HAND_LEFT);
        return left != null && attacker.numbArmPart == BodyPart.ARM_L;
    }

    private static int effectiveArmor(Entity target) {
        int base = target.armor();
        return (target instanceof Actor a && a.isGuarded())
                ? Math.max(base + 1, (int) Math.round(base * (1.0 + GUARD_ARMOR_BONUS_PCT / 100.0)))
                : base;
    }

    public static int effectiveEvasion(Entity target) {
        if (target == null || target.kind() != Entity.Kind.CREATURE) {
            return 0;
        }
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
        if (target.has(Tag.FROZEN)) {
            eva += EVA_FROZEN;
        } else if (target.has(Tag.CHILLED)) {
            eva += EVA_CHILLED;
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