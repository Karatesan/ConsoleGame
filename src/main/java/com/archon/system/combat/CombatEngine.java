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
        int hit = 70 + part.hitMod - target.evasion()
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

        int base = (weapon == null ? 3 : weapon.damage)
                + attacker.strength();

        double multiplier = part.damageMult
                * ("light".equals(power) ? 0.6
                : "heavy".equals(power) ? 1.6 : 1.0);

        int damage = Math.max(
                1,
                (int) Math.round(base * multiplier) - target.armor()
        );

        target.takeDamage(damage);

        boolean degraded = false;
        if (force && weapon != null) {
            weapon.durability--;
            degraded = true;
        }

        boolean killed = !target.alive();
        if (killed) {
            disarmAndDrop(world, target);
        }

        return new MeleeHitResult(
                true, clampedHit, damage, part, weapon, killed, degraded
        );
    }

    public static DisarmResult attemptDisarm(
            Dice dice,
            World world,
            Entity owner
    ) {
        if (owner == null) {
            return new DisarmResult(false, null);
        }

        Item weapon = activeItem(owner);
        if (weapon == null) {
            return new DisarmResult(false, null);
        }

        if (!dice.chance(45)) {
            return new DisarmResult(false, weapon);
        }

        Item dropped = disarmAndDrop(world, owner);
        return new DisarmResult(dropped != null, dropped);
    }

    public static RangedHitResult resolveRanged(
            Dice dice,
            World world,
            Thrall attacker,
            Entity target,
            BodyPart part
    ) {
        int distance = target.getPos().chebyshev(attacker.getPos());

        int band = distance <= 1 ? -20
                : distance <= 4 ? 0
                : distance <= 8 ? -10 : -25;

        int hit = 70 + part.hitMod + band - target.evasion();
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

        // Preserve the original ranged behavior:
        // only disarm/drop on death when a world is supplied.
        if (killed && world != null) {
            disarmAndDrop(world, target);
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

    /**
     * Actors use equipped hand items; other entities use their held item.
     */
    private static Item activeItem(Entity entity) {
        if (entity instanceof Actor actor) {
            return actor.mainHand();
        }
        return entity.getHeld();
    }

    /**
     * Uses the entity's polymorphic disarm implementation, then places
     * the returned item on the ground if a world is available.
     */
    private static Item disarmAndDrop(World world, Entity entity) {
        Item dropped = entity.disarm();

        if (dropped != null && world != null) {
            world.tile(entity.getPos()).ground.add(dropped);
        }

        return dropped;
    }
}