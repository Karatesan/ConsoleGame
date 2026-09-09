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
     * Finds any alive, unspent readied reaction that triggers given world state and movement.
     */
    public static Entity pendingInterrupt(World world, boolean thrallMoved) {
        if (world.thrall == null) return null;
        for (Entity e : world.entities.values()) {
            if (!e.alive() || e.readied == null || e.readiedSpent) continue;
            switch (e.readied.trigger()) {
                case ON_ADJACENCY -> {
                    if (e.pos.chebyshev(world.thrall.pos) <= 1) return e;
                }
                case ON_MOVEMENT_IN_LOS -> {
                    if (thrallMoved && SpatialService.lineOfSight(world.map, e.pos, world.thrall.pos)) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Overload using world's current line movement flag.
     */
    public static Entity pendingInterrupt(World world) {
        return pendingInterrupt(world, world.thrallMovedThisLine);
    }

    /**
     * Resolves a deterministic interrupt against the thrall.
     * Marks reaction as spent, damages the thrall, and returns damage dealt.
     */
    public static int resolveInterrupt(World world, Entity e) {
        e.readiedSpent = true;
        int dmg = e.readied.damage();
        world.thrall.takeDamage(dmg);
        return dmg;
    }
}