package com.archon.system.environment;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.ai.AiSystem;

import java.util.ArrayList;
import java.util.List;

import static com.archon.model.Balance.BLIND_RECOVER_CHANCE;
import static com.archon.model.Balance.BURNING_TICK_DAMAGE;

/** Enemy phase, periodic burning, actor round resets, and fire propagation. */
public final class SimulationSystem {

    private static final Vec2[] CARDINALS = {
            new Vec2(0, -1), new Vec2(0, 1), new Vec2(1, 0), new Vec2(-1, 0)
    };

    private SimulationSystem() {}

    /**
     * Advances the round, runs enemy AI, applies burning, resets the thrall,
     * and spreads fire to adjacent oil tiles using the state before this tick's spread.
     */
    public static void tick(World world, EventBus bus) {
        List<String> log = new ArrayList<>();
        world.advanceRound();

        AiSystem.tick(world, bus);

        for (Entity entity : world.entities()) {
            if (entity.alive() && entity.has(Tag.BURNING)) {
                entity.takeDamage(BURNING_TICK_DAMAGE);
                log.add(entity.name() + " burns for " + BURNING_TICK_DAMAGE + ".");

                if (!entity.alive()) {
                    log.add(entity.name() + " is consumed.");
                    Destruction.Result destruction = Destruction.onKilled(world, entity);
                    log.addAll(destruction.messages());
                }
            }
            if (entity.alive() && entity.has(Tag.BLIND) && world.dice().chance(BLIND_RECOVER_CHANCE)) {
                entity.removeTag(Tag.BLIND);
                log.add(entity.name() + "'s vision clears.");
            }
        }

        Actor thrall = world.thrall();
        if (thrall.alive() && thrall.has(Tag.BURNING)) {
            thrall.takeDamage(BURNING_TICK_DAMAGE);
            log.add("Thrall burns for " + BURNING_TICK_DAMAGE + ".");
        }
        if (thrall.alive() && thrall.has(Tag.BLIND) && world.dice().chance(BLIND_RECOVER_CHANCE)) {
            thrall.removeTag(Tag.BLIND);
            log.add("Thrall's vision clears.");
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

                for (Vec2 direction : CARDINALS) {
                    Vec2 adjacent = position.plus(direction);
                    GameMap.Tile adjacentTile = map.tile(adjacent);

                    if (adjacentTile != null && adjacentTile.has(Tag.OIL)
                            && !adjacentTile.has(Tag.WATER) && !adjacentTile.has(Tag.BURNING)) {
                        ignite.add(adjacent);
                    }
                }
            }
        }

        for (Vec2 position : ignite) {
            map.addTag(position, Tag.BURNING);
            log.add("Fire spreads to " + position + ".");
        }

        if (!log.isEmpty()) {
            bus.post(new GameEvent.EnvBeat(log, ignite));
        }
    }
}