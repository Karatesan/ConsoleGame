package com.archon.system.combat;

import com.archon.model.*;

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
                + ("light".equals(power) ? 20
                : "heavy".equals(power) ? -20 : 0);

        if (target.isGuarded() && !force) {
            hit -= 25;
        }

        int clampedHit = Math.max(5, Math.min(95, hit));
        Item weapon = attacker.mainHand();

        if (!dice.chance(clampedHit)) {
            return new MeleeHitResult(
                    false, clampedHit, 0, part, weapon, false, false
            );
        }

        int base = (weapon == null ? 3 : weapon.damage())
                + attacker.strength();

        double multiplier = part.damageMult()
                * ("light".equals(power) ? 0.6
                : "heavy".equals(power) ? 1.6 : 1.0);

        int damage = Math.max(
                1,
                (int) Math.round(base * multiplier) - target.armor()
        );

        target.takeDamage(damage);

        boolean degraded = false;
        if (force && weapon != null) {
            weapon.wear(1);
            degraded = true;
        }

        boolean killed = !target.alive();
        if (killed) {
            dropEquipment(world, target);
        }

        return new MeleeHitResult(
                true, clampedHit, damage, part, weapon, killed, degraded
        );
    }

    public static DisarmResult attemptDisarm(
            Dice dice,
            World world,
            Actor owner
    ) {
        if (owner == null) {
            return new DisarmResult(false, null);
        }

        Item weapon = owner.mainHand();
        if (weapon == null) {
            return new DisarmResult(false, null);
        }

        if (!dice.chance(45)) {
            return new DisarmResult(false, weapon);
        }

        Item dropped = disarmMainHandAndDrop(world, owner);
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

        int band = distance <= 1 ? -20
                : distance <= 4 ? 0
                : distance <= 8 ? -10 : -25;

        int hit = 70 + part.hitMod() + band - target.evasion();
        int clampedHit = Math.max(5, Math.min(95, hit));

        if (!dice.chance(clampedHit)) {
            return new RangedHitResult(
                    false, clampedHit, 0, part, false
            );
        }

        int damage = Math.max(
                1,
                dice.between(5, 10) - target.armor()
        );

        target.takeDamage(damage);

        boolean killed = !target.alive();
        if (killed) {
            dropEquipment(world, target);
        }

        return new RangedHitResult(
                true, clampedHit, damage, part, killed
        );
    }

    public static RangedHitResult resolveRanged(
            Dice dice,
            Thrall attacker,
            Entity target,
            BodyPart part
    ) {
        return resolveRanged(dice, null, attacker, target, part);
    }

    private static Item disarmMainHandAndDrop(World world, Actor actor) {
        Item dropped = actor.disarm(EquipmentSlot.RIGHT_HAND);
        if (dropped == null) {
            dropped = actor.disarm(EquipmentSlot.LEFT_HAND);
        }

        drop(world, actor, dropped);
        return dropped;
    }

    private static void dropEquipment(World world, Entity entity) {
        if (!(entity instanceof Actor actor)) {
            return;
        }

        drop(world, actor, actor.disarm(EquipmentSlot.RIGHT_HAND));
        drop(world, actor, actor.disarm(EquipmentSlot.LEFT_HAND));
    }

    private static void drop(World world, Actor actor, Item item) {
        if (world != null && item != null) {
            world.tile(actor.pos()).ground().add(item);
        }
    }
}