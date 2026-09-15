package com.archon.system.environment;

import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;

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
        World.Tile tile = world.tile(at);
        if (tile == null) {
            return;
        }

        tile.tags.add(Tag.LIQUID);
        tile.tags.add(substance);

        if (substance == Tag.OIL) {
            tile.tags.add(Tag.FLAMMABLE);
        }
        if (substance == Tag.WATER) {
            tile.tags.add(Tag.CONDUCTIVE);
        }

        Entity occupant = world.entityAt(at);
        if (occupant != null && substance == Tag.OIL) {
            occupant.applyTag(Tag.FLAMMABLE);
        }
    }

    /**
     * Advances world simulation by one tick:
     * - Increments round number
     * - Burns entities with BURNING tag (and thrall)
     * - Resets per-round creature state
     * - Propagates fire to adjacent oil tiles
     */
    public static List<String> tick(World world) {
        List<String> log = new ArrayList<>();
        world.roundNumber++;

        for (Entity e : new ArrayList<>(world.entities.values())) {
            if (e.alive() && e.has(Tag.BURNING)) {
                e.takeDamage(3);
                log.add(e.name() + " burns for 3.");
                if (!e.alive()) {
                    log.add(e.name() + " is consumed.");
                }
            }
            e.resetRoundState();
        }

        Entity thrall = world.thrall;
        if (thrall.has(Tag.BURNING)) {
            thrall.takeDamage(3);
            log.add("Thrall burns for 3.");
        }

        List<Vec2> ignite = new ArrayList<>();
        for (int y = 0; y < world.h; y++) {
            for (int x = 0; x < world.w; x++) {
                Vec2 position = new Vec2(x, y);
                World.Tile tile = world.tile(position);
                if (!tile.has(Tag.BURNING)) {
                    continue;
                }

                for (Vec2 direction : List.of(
                        Vec2.dir("n"),
                        Vec2.dir("s"),
                        Vec2.dir("e"),
                        Vec2.dir("w"))) {
                    Vec2 adjacent = position.plus(direction);
                    World.Tile adjacentTile = world.tile(adjacent);
                    if (adjacentTile != null
                            && adjacentTile.has(Tag.OIL)
                            && !adjacentTile.has(Tag.BURNING)) {
                        ignite.add(adjacent);
                    }
                }
            }
        }

        for (Vec2 position : ignite) {
            world.tile(position).tags.add(Tag.BURNING);
            log.add("Fire spreads to " + position + ".");
        }

        return log;
    }
}