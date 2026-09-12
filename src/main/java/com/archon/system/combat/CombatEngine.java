package com.archon.system.combat;

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
        int hit = 70 + part.hitMod - target.evasion()
                + ("light".equals(power) ? 20 : "heavy".equals(power) ? -20 : 0);
        if (target.guarded && !force) hit -= 25;
        int clampedHit = Math.max(5, Math.min(95, hit));

        if (!dice.chance(clampedHit)) {
            return new MeleeHitResult(false, clampedHit, 0, part, attacker.mainHand(), false, false);
        }

        Item w = attacker.mainHand();
        int base = (w == null ? 3 : w.damage) + attacker.strength();
        double mult = part.damageMult * ("light".equals(power) ? 0.6 : "heavy".equals(power) ? 1.6 : 1.0);
        int dmg = Math.max(1, (int) Math.round(base * mult) - target.armor());
        target.takeDamage(dmg);

        boolean degraded = false;
        if (force && w != null) {
            w.durability--;
            degraded = true;
        }

        boolean killed = !target.alive();
        if (killed && target.held != null) {
            Item dropped = target.disarm();
            if (dropped != null && world != null) {
                world.tile(target.pos()).ground.add(dropped);
            }
        }

        return new MeleeHitResult(true, clampedHit, dmg, part, w, killed, degraded);
    }

    public static DisarmResult attemptDisarm(Dice dice, World world, Entity owner) {
        if (owner == null || owner.held == null) {
            return new DisarmResult(false, null);
        }
        if (dice.chance(45)) {
            Item dropped = owner.disarm();
            if (dropped != null && world != null) {
                world.tile(owner.pos()).ground.add(dropped);
            }
            return new DisarmResult(true, dropped);
        }
        return new DisarmResult(false, owner.held);
    }

    public static RangedHitResult resolveRanged(
            Dice dice,
            World world,
            Thrall attacker,
            Entity target,
            BodyPart part
    ) {
        int dist = target.pos().chebyshev(attacker.pos());
        int band = dist <= 1 ? -20 : dist <= 4 ? 0 : dist <= 8 ? -10 : -25;
        int hit = 70 + part.hitMod + band - target.evasion();
        int clampedHit = Math.max(5, Math.min(95, hit));

        if (!dice.chance(clampedHit)) {
            return new RangedHitResult(false, clampedHit, 0, part, false);
        }

        int dmg = Math.max(1, dice.between(5, 10) - target.armor());
        target.takeDamage(dmg);
        boolean killed = !target.alive();
        if (killed && target.held != null && world != null) {
            Item dropped = target.disarm();
            if (dropped != null) {
                world.tile(target.pos()).ground.add(dropped);
            }
        }
        return new RangedHitResult(true, clampedHit, dmg, part, killed);
    }

    public static RangedHitResult resolveRanged(
            Dice dice,
            Thrall attacker,
            Entity target,
            BodyPart part
    ) {
        return resolveRanged(dice, null, attacker, target, part);
    }
}