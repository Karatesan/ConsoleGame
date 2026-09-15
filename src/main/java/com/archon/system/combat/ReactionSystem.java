package com.archon.system.combat;

import com.archon.model.Entity;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

/**
 * Domain service managing deterministic reactions and interrupt economy triggers.
 */
public final class ReactionSystem {

    private ReactionSystem() {}

    /**
     * Finds an alive entity with an unspent readied reaction whose
     * trigger matches the current world state and movement.
     */
    public static Entity pendingInterrupt(World world, boolean thrallMoved) {
        if (world.thrall == null) {
            return null;
        }

        for (Entity entity : world.entities.values()) {
            if (!entity.canReact() || entity.isReadiedSpent()) {
                continue;
            }

            Entity.Readied reaction = entity.readied();

            switch (reaction.trigger()) {
                case ON_ADJACENCY -> {
                    if (entity.pos().chebyshev(world.thrall.pos()) <= 1) {
                        return entity;
                    }
                }

                case ON_MOVEMENT_IN_LOS -> {
                    if (thrallMoved && SpatialService.lineOfSight(world.map, entity.pos(), world.thrall.pos())) {
                        return entity;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Uses the world's current line movement flag.
     */
    public static Entity pendingInterrupt(World world) {
        return pendingInterrupt(world, world.thrallMovedThisLine);
    }

    /**
     * Resolves an interrupt returned by pendingInterrupt.
     * Marks the reaction as spent and applies its damage to the thrall.
     */
    public static int resolveInterrupt(World world, Entity entity) {
        int damage = entity.readied().damage();
        entity.spendReadied();
        world.thrall.takeDamage(damage);
        return damage;
    }
}