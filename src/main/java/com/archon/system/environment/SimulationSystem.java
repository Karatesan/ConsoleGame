package com.archon.system.environment;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Periodic burning, actor round resets, and fire propagation. */
public final class SimulationSystem {

    private SimulationSystem() {}

    /**
     * Advances the round, applies burning, resets actors, and spreads fire
     * to adjacent oil tiles using the state before this tick's spread.
     */
    public static List<String> tick(World world) {
        List<String> log = new ArrayList<>();
        world.advanceRound();

        Collection<Entity> entitySnapshot = new ArrayList<>(world.entities());
        for (Entity entity : entitySnapshot) {
            if (entity.alive() && entity.has(Tag.BURNING)) {
                entity.takeDamage(3);
                log.add(entity.name() + " burns for 3.");

                if (!entity.alive()) {
                    log.add(entity.name() + " is consumed.");
                }
            }

            if (entity instanceof Actor actor) {
                actor.resetRoundState();
            }
        }

        Actor thrall = world.thrall();
        if (thrall.has(Tag.BURNING)) {
            thrall.takeDamage(3);
            log.add("Thrall burns for 3.");
        }
        thrall.resetRoundState();

        GameMap map = world.map();
        List<Vec2> ignite = new ArrayList<>();

        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                Vec2 position = new Vec2(x, y);
                GameMap.Tile tile = map.tile(position);
                if (!tile.has(Tag.BURNING)) {
                    continue;
                }

                for (Vec2 direction : List.of(Vec2.dir("n"), Vec2.dir("s"), Vec2.dir("e"), Vec2.dir("w"))) {
                    Vec2 adjacent = position.plus(direction);
                    GameMap.Tile adjacentTile = map.tile(adjacent);

                    if (adjacentTile != null && adjacentTile.has(Tag.OIL) && !adjacentTile.has(Tag.BURNING)) {
                        ignite.add(adjacent);
                    }
                }
            }
        }

        for (Vec2 position : ignite) {
            map.addTag(position, Tag.BURNING);
            log.add("Fire spreads to " + position + ".");
        }

        return log;
    }
}