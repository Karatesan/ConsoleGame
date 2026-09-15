package com.archon.system.combat;

import static com.archon.model.Actor.HAND_LEFT;
import static com.archon.model.Actor.HAND_RIGHT;

import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.Thrall;
import com.archon.model.World;

/**
 * Domain service encapsulating combat calculations and state mutations
 * for melee strikes, ranged attacks, and disarm maneuvers.
 */
public final class CombatEngine {

    private CombatEngine() {}

    public record MeleeHitResult(
            boolean hit,
            int hitChance,
            int damage,
            BodyPart part,
            Item weapon,
            boolean killed,
            boolean weaponDegraded
    ) {}

    public record DisarmResult(
            boolean success,
            Item weapon
    ) {}

    public record RangedHitResult(
            boolean hit,
            int hitChance,
            int damage,
            BodyPart part,
            boolean killed
    ) {}

    public static MeleeHitResult resolveMelee(
            Dice dice,
            World world,
            Thrall attacker,
            Entity target,
            BodyPart part,
            String power,
            boolean force
    ) {
        int hit = 70 + part.hitMod() - target.evasion()
                + ("light".equals(power) ? 20 : "heavy".equals(power) ? -20 : 0);
        if (target.isGuarded() && !force) {
            hit -= 25;
        }
        int clampedHit = Math.max(5, Math.min(95, hit));

        if (!dice.chance(clampedHit)) {
            return new MeleeHitResult(false, clampedHit, 0, part, attacker.mainHand(), false, false);
        }

        Item weapon = attacker.mainHand();
        int base = (weapon == null ? 3 : weapon.damage()) + attacker.strength();
        double multiplier = part.damageMult()
                * ("light".equals(power) ? 0.6 : "heavy".equals(power) ? 1.6 : 1.0);
        int damage = Math.max(1, (int) Math.round(base * multiplier) - target.armor());
        target.takeDamage(damage);

        boolean degraded = false;
        if (force && weapon != null) {
            weapon.wear(1);
            degraded = true;
        }

        boolean killed = !target.alive();
        if (killed) {
            dropMainHand(world, target);
        }

        return new MeleeHitResult(true, clampedHit, damage, part, weapon, killed, degraded);
    }

    public static DisarmResult attemptDisarm(Dice dice, World world, Actor owner) {
        if (owner == null || owner.mainHand() == null) {
            return new DisarmResult(false, null);
        }

        if (!dice.chance(45)) {
            return new DisarmResult(false, owner.mainHand());
        }

        Item dropped = disarmMainHand(owner);
        if (dropped != null && world != null) {
            world.tile(owner.pos()).ground().add(dropped);
        }
        return new DisarmResult(dropped != null, dropped);
    }

    public static RangedHitResult resolveRanged(
            Dice dice,
            World world,
            Thrall attacker,
            Entity target,
            BodyPart part
    ) {
        int distance = target.pos().chebyshev(attacker.pos());
        int band = distance <= 1 ? -20 : distance <= 4 ? 0 : distance <= 8 ? -10 : -25;
        int hit = 70 + part.hitMod() + band - target.evasion();
        int clampedHit = Math.max(5, Math.min(95, hit));

        if (!dice.chance(clampedHit)) {
            return new RangedHitResult(false, clampedHit, 0, part, false);
        }

        int damage = Math.max(1, dice.between(5, 10) - target.armor());
        target.takeDamage(damage);

        boolean killed = !target.alive();
        if (killed) {
            dropMainHand(world, target);
        }

        return new RangedHitResult(true, clampedHit, damage, part, killed);
    }

    public static RangedHitResult resolveRanged(
            Dice dice,
            Thrall attacker,
            Entity target,
            BodyPart part
    ) {
        return resolveRanged(dice, null, attacker, target, part);
    }

    private static void dropMainHand(World world, Entity target) {
        if (world == null || !(target instanceof Actor actor)) {
            return;
        }

        Item dropped = disarmMainHand(actor);
        if (dropped != null) {
            world.tile(target.pos()).ground().add(dropped);
        }
    }

    private static Item disarmMainHand(Actor actor) {
        Item dropped = actor.disarm(HAND_RIGHT);
        return dropped != null ? dropped : actor.disarm(HAND_LEFT);
    }
}