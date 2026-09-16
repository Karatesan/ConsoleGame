package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

/**
 * Domain service managing deterministic reactions and interrupt economy triggers.
 */
public final class ReactionSystem {

    private ReactionSystem() {}

    /**
     * Finds the first alive actor with an unspent readied reaction that is triggered by the
     * current world state and thrall movement.
     *
     * @param world current world state
     * @param thrallMoved whether the thrall moved during the current line
     * @return the actor whose reaction is triggered, or {@code null} if none is pending
     */
    public static Actor pendingInterrupt(World world, boolean thrallMoved) {
        if (world.thrall() == null) {
            return null;
        }

        for (Entity entity : world.entities()) {
            if (!(entity instanceof Actor actor)) {
                continue;
            }

            if (!actor.alive() || actor.readied() == null || actor.isReadiedSpent()) {
                continue;
            }

            switch (actor.readied().trigger()) {
                case ON_ADJACENCY -> {
                    if (actor.pos().chebyshev(world.thrall().pos()) <= 1) {
                        return actor;
                    }
                }
                case ON_MOVEMENT_IN_LOS -> {
                    if (thrallMoved
                            && SpatialService.lineOfSight(
                                    world.map(), actor.pos(), world.thrall().pos())) {
                        return actor;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Finds a pending interrupt using the world's current movement flag.
     *
     * @param world current world state
     * @return the actor whose reaction is triggered, or {@code null} if none is pending
     */
    public static Actor pendingInterrupt(World world) {
        return pendingInterrupt(world, world.thrallMovedThisLine());
    }

    /**
     * Resolves an actor's readied reaction against the thrall.
     *
     * @param world current world state
     * @param actor actor whose reaction is being resolved
     * @return damage dealt to the thrall
     */
    public static int resolveInterrupt(World world, Actor actor) {
        int damage = actor.readied().damage();
        actor.spendReadied();
        world.thrall().takeDamage(damage);
        return damage;
    }
}