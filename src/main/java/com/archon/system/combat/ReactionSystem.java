package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

import java.util.Objects;
import java.util.Optional;

/** Selects and resolves the first triggered, unspent readied reaction. */
public final class ReactionSystem {

    private ReactionSystem() {}

    public static Optional<Reaction> fire(World world) {
        Objects.requireNonNull(world, "world");
        Actor target = world.thrall();
        if (target == null) {
            return Optional.empty();
        }

        for (Entity entity : world.entities()) {
            if (!(entity instanceof Actor actor)) {
                continue;
            }

            Actor.Readied readied = actor.readied();
            if (!actor.alive() || readied == null || actor.isReadiedSpent()) {
                continue;
            }

            boolean triggered = switch (readied.trigger()) {
                case ON_ADJACENCY -> actor.pos().chebyshev(target.pos()) <= 1;
                case ON_MOVEMENT_IN_LOS ->
                        world.thrallMovedThisLine() && SpatialService.lineOfSight(world.map(), actor.pos(),
                                target.pos());
            };
            if (!triggered) {
                continue;
            }

            Reaction reaction = new Reaction(actor.id(), actor.name(), readied.description(), readied.damage());
            actor.spendReadied();
            target.takeDamage(reaction.damage());
            return Optional.of(reaction);
        }

        return Optional.empty();
    }
}