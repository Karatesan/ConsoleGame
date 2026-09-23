package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.EquipmentSlot;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.model.Thrall;
import com.archon.model.World;
import com.archon.system.item.ItemMoves;

import java.util.Objects;

/**
 * Domain service encapsulating combat calculations and state mutations
 * for melee strikes, ranged attacks, and disarm maneuvers.
 */
public final class CombatEngine {
    private CombatEngine() {}

    public record MeleeHitResult(boolean hit, int hitChance, int damage, BodyPart part, Item weapon, boolean killed,
                                 boolean weaponDegraded) {}

    public record DisarmResult(boolean success, Item weapon) {}

    public record RangedHitResult(boolean hit, int hitChance, int damage, BodyPart part, boolean killed) {}

    public static MeleeHitResult resolveMelee(Dice dice, World world, Thrall attacker, Entity target, BodyPart part,
                                              String power, boolean force) {
        Objects.requireNonNull(world, "world");
        int hitChance = 70 + part.hitMod - target.evasion() + ("light".equals(power) ? 20 : "heavy".equals(
                power) ? -20 : 0);
        if (target instanceof Actor actor && actor.isGuarded() && !force) {
            hitChance -= 25;
        }

        hitChance = clampHitChance(hitChance);
        Item weapon = attacker.mainHand();
        if (!dice.chance(hitChance)) {
            return new MeleeHitResult(false, hitChance, 0, part, weapon, false, false);
        }

        int baseDamage = (weapon == null ? 3 : weapon.damage()) + attacker.strength();
        double multiplier = part.damageMult * ("light".equals(power) ? 0.6 : "heavy".equals(power) ? 1.6 : 1.0);
        int damage = Math.max(1, (int) Math.round(baseDamage * multiplier) - target.armor());
        target.takeDamage(damage);

        boolean weaponDegraded = force && weapon != null;
        if (weaponDegraded) {
            weapon.wear(1);
        }

        boolean killed = !target.alive();
        if (killed) {
            dropMainHand(world, target);
        }
        return new MeleeHitResult(true, hitChance, damage, part, weapon, killed, weaponDegraded);
    }

    public static DisarmResult attemptDisarm(Dice dice, World world, Actor owner, EquipmentSlot slot) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(owner, "owner");
        if (slot != EquipmentSlot.HAND_LEFT && slot != EquipmentSlot.HAND_RIGHT) {
            throw new IllegalArgumentException("disarm requires a hand slot");
        }

        Item weapon = owner.inventory().equipped(slot);
        if (weapon == null) {
            return new DisarmResult(false, null);
        }
        if (!dice.chance(45)) {
            return new DisarmResult(false, weapon);
        }

        ItemMoves.drop(world.map(), weapon, new ItemLocation.Equipment(owner, slot)).requireSuccess();
        return new DisarmResult(true, weapon);
    }

    public static RangedHitResult resolveRanged(Dice dice, World world, Thrall attacker, Entity target, BodyPart part) {
        Objects.requireNonNull(world, "world");
        int distance = target.pos().chebyshev(attacker.pos());
        int rangeModifier = distance <= 1 ? -20 : distance <= 4 ? 0 : distance <= 8 ? -10 : -25;
        int hitChance = clampHitChance(70 + part.hitMod + rangeModifier - target.evasion());

        if (!dice.chance(hitChance)) {
            return new RangedHitResult(false, hitChance, 0, part, false);
        }

        int damage = Math.max(1, dice.between(5, 10) - target.armor());
        target.takeDamage(damage);

        boolean killed = !target.alive();
        if (killed) {
            dropMainHand(world, target);
        }
        return new RangedHitResult(true, hitChance, damage, part, killed);
    }

    private static int clampHitChance(int hitChance) {
        return Math.max(5, Math.min(95, hitChance));
    }

    private static void dropMainHand(World world, Entity target) {
        if (!(target instanceof Actor actor)) {
            return;
        }

        Item item = actor.mainHand();
        if (item != null) {
            EquipmentSlot slot = actor.inventory().equipped(
                    EquipmentSlot.HAND_RIGHT) == item ? EquipmentSlot.HAND_RIGHT : EquipmentSlot.HAND_LEFT;
            ItemMoves.drop(world.map(), item, new ItemLocation.Equipment(actor, slot)).requireSuccess();
        }
    }
}