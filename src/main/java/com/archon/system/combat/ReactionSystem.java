package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

/**
 * Domain service managing deterministic reactions and interrupt economy triggers.
 */
public final class ReactionSystem {

    private ReactionSystem() {}

    /**
     * Finds any alive, unspent readied reaction that triggers given world state and movement.
     */
    public static Actor pendingInterrupt(World world, boolean thrallMoved) {
        if (world.thrall == null) return null;

        for (var entity : world.entities.values()) {
            if (!(entity instanceof Actor actor)
                    || !actor.alive()
                    || actor.readied() == null
                    || actor.isReadiedSpent()) {
                continue;
            }

            switch (actor.readied().trigger()) {
                case ON_ADJACENCY -> {
                    if (actor.pos().chebyshev(world.thrall.pos()) <= 1) {
                        return actor;
                    }
                }
                case ON_MOVEMENT_IN_LOS -> {
                    if (thrallMoved
                            && SpatialService.lineOfSight(
                                    world.map, actor.pos(), world.thrall.pos())) {
                        return actor;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Overload using world's current line movement flag.
     */
    public static Actor pendingInterrupt(World world) {
        return pendingInterrupt(world, world.thrallMovedThisLine);
    }

    /**
     * Resolves a deterministic interrupt against the thrall.
     * Marks reaction as spent, damages the thrall, and returns damage dealt.
     */
    public static int resolveInterrupt(World world, Actor actor) {
        actor.spendReadied();
        int damage = actor.readied().damage();
        world.thrall.takeDamage(damage);
        return damage;
    }
}