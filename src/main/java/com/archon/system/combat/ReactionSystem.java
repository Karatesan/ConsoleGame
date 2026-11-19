package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

import java.util.Optional;

/** Selects and resolves the first triggered, unspent readied reaction. */
public final class ReactionSystem {

    private ReactionSystem() {}

    public static Optional<Reaction> fire(World world) {
        if (!world.thrallMovedThisLine()) {
            return Optional.empty();
        }
        Actor target = world.thrall();

        for (Entity entity : world.entities()) {
            if (!(entity instanceof Actor actor) || !actor.alive()) {
                continue;
            }

            Actor.Readied readied = actor.readied();
            if (readied == null || actor.isReadiedSpent()) {
                continue;
            }

            boolean triggered = switch (readied.trigger()) {
                case ON_ADJACENCY -> actor.pos().manhattan(target.pos()) == 1;
                case ON_MOVEMENT_IN_LOS -> !actor.has( com.archon.model.Tag.BLIND)
                        && SpatialService.lineOfFire(world, actor.pos(), target.pos());
            };
            if (!triggered) {
                continue;
            }

            Reaction reaction = new Reaction(actor.id(), actor.name(), readied.description(), readied.damage());
            actor.spendReadied();
            if (readied.trigger() == Actor.Trigger.ON_MOVEMENT_IN_LOS) {
                actor.fireNocked();
            }
            world.resetThrallMovement();
            target.takeDamage(reaction.damage());
            return Optional.of(reaction);
        }

        return Optional.empty();
    }
}