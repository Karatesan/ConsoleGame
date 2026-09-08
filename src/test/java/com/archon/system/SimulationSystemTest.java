package com.archon.system;

import com.archon.app.Scenario;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.environment.SimulationSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationSystemTest {

    @Test
    void testSpillOilSetsTileAndOccupantTags() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Vec2 pos = new Vec2(3, 3);
        Entity occupant = world.entityAt(pos);

        SimulationSystem.spill(world, pos, Tag.OIL);

        World.Tile tile = world.tile(pos);
        assertTrue(tile.has(Tag.LIQUID));
        assertTrue(tile.has(Tag.OIL));
        assertTrue(tile.has(Tag.FLAMMABLE));

        if (occupant != null) {
            assertTrue(occupant.has(Tag.FLAMMABLE));
        }
    }

    @Test
    void testSpillWaterSetsConductive() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Vec2 pos = new Vec2(1, 1);

        SimulationSystem.spill(world, pos, Tag.WATER);

        World.Tile tile = world.tile(pos);
        assertTrue(tile.has(Tag.LIQUID));
        assertTrue(tile.has(Tag.WATER));
        assertTrue(tile.has(Tag.CONDUCTIVE));
    }

    @Test
    void testTickAppliesBurnDamageAndAdvancesRound() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Entity target = world.get("o1");
        target.tags.add(Tag.BURNING);
        int initialHp = target.hp;
        int initialRound = world.roundNumber;

        List<String> log = SimulationSystem.tick(world);

        assertEquals(initialRound + 1, world.roundNumber);
        assertEquals(initialHp - 3, target.hp);
        assertTrue(log.stream().anyMatch(msg -> msg.contains("burns for 3")));
    }

    @Test
    void testFireSpreadsAcrossOilTiles() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Vec2 firePos = new Vec2(2, 2);
        Vec2 oilAdjacent = new Vec2(2, 3);

        world.tile(firePos).tags.add(Tag.BURNING);
        world.tile(oilAdjacent).tags.add(Tag.OIL);

        assertFalse(world.tile(oilAdjacent).has(Tag.BURNING));

        SimulationSystem.tick(world);

        assertTrue(world.tile(oilAdjacent).has(Tag.BURNING));
    }
}
