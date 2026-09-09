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
        World.Tile t = world.tile(at);
        if (t == null) return;
        t.tags.add(Tag.LIQUID);
        t.tags.add(substance);
        if (substance == Tag.OIL) t.tags.add(Tag.FLAMMABLE);
        if (substance == Tag.WATER) t.tags.add(Tag.CONDUCTIVE);
        Entity occ = world.entityAt(at);
        if (occ != null && substance == Tag.OIL) occ.applyTag(Tag.FLAMMABLE);
    }

    /**
     * Advances world simulation by one tick:
     * - Increments round number
     * - Burns entities with BURNING tag (and thrall)
     * - Resets per-round creature state (readiedSpent, guarded)
     * - Propagates fire to adjacent oil tiles
     */
    public static List<String> tick(World world) {
        List<String> log = new ArrayList<>();
        world.roundNumber++;

        for (Entity e : new ArrayList<>(world.entities.values())) {
            if (e.alive() && e.has(Tag.BURNING)) {
                e.takeDamage(3);
                log.add(e.name + " burns for 3.");
                if (!e.alive()) log.add(e.name + " is consumed.");
            }
            e.readiedSpent = false;
            e.guarded = false;
        }
        if (world.thrall.has(Tag.BURNING)) {
            world.thrall.takeDamage(3);
            log.add("Thrall burns for 3.");
        }

        // Fire spreads across contiguous oil.
        List<Vec2> ignite = new ArrayList<>();
        for (int y = 0; y < world.h; y++) {
            for (int x = 0; x < world.w; x++) {
                Vec2 p = new Vec2(x, y);
                World.Tile t = world.tile(p);
                if (t.has(Tag.BURNING)) {
                    for (Vec2 d : List.of(Vec2.dir("n"), Vec2.dir("s"), Vec2.dir("e"), Vec2.dir("w"))) {
                        Vec2 q = p.plus(d);
                        World.Tile u = world.tile(q);
                        if (u != null && u.has(Tag.OIL) && !u.has(Tag.BURNING)) ignite.add(q);
                    }
                }
            }
        }
        for (Vec2 p : ignite) {
            world.tile(p).tags.add(Tag.BURNING);
            log.add("Fire spreads to " + p + ".");
        }

        return log;
    }
}