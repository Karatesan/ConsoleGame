package com.archon.system.environment;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain service encapsulating environmental physics and simulation:
 * liquid spills, fire propagation across oil tiles, and periodic burn ticks.
 */
public final class SimulationSystem {

    private SimulationSystem() {}

    /**
     * Spills a liquid substance onto a tile, updating tile tags and any occupant.
     */
    public static void spill(World world, Vec2 at, Tag substance) {
        GameMap.Tile tile = world.map().tile(at);
        if (tile == null) return;

        world.map().addTag(at, Tag.LIQUID);
        world.map().addTag(at, substance);
        if (substance == Tag.OIL) world.map().addTag(at, Tag.FLAMMABLE);
        if (substance == Tag.WATER) world.map().addTag(at, Tag.CONDUCTIVE);

        Entity occupant = SpatialService.entityAt(world, at);
        if (occupant != null && substance == Tag.OIL) {
            occupant.applyTag(Tag.FLAMMABLE);
        }
    }

    /**
     * Advances world simulation by one tick:
     * - Increments round number
     * - Burns entities with BURNING tag (and thrall)
     * - Resets per-round Actor state
     * - Propagates fire to adjacent oil tiles
     */
    public static List<String> tick(World world) {
        List<String> log = new ArrayList<>();
        world.advanceRound();

        for (Entity entity : new ArrayList<>(world.entities())) {
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

        // Fire spreads across contiguous oil.
        List<Vec2> ignite = new ArrayList<>();
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                Vec2 position = new Vec2(x, y);
                GameMap.Tile tile = world.map().tile(position);
                if (tile.has(Tag.BURNING)) {
                    for (Vec2 direction : List.of(
                            Vec2.dir("n"),
                            Vec2.dir("s"),
                            Vec2.dir("e"),
                            Vec2.dir("w"))) {
                        Vec2 adjacent = position.plus(direction);
                        GameMap.Tile adjacentTile = world.map().tile(adjacent);
                        if (adjacentTile != null
                                && adjacentTile.has(Tag.OIL)
                                && !adjacentTile.has(Tag.BURNING)) {
                            ignite.add(adjacent);
                        }
                    }
                }
            }
        }

        for (Vec2 position : ignite) {
            world.map().addTag(position, Tag.BURNING);
            log.add("Fire spreads to " + position + ".");
        }

        return log;
    }
}
