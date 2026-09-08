package com.archon.system;

import com.archon.app.Scenario;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialServiceTest {

    @Test
    void testEntityAtFindsThrallAndCreatures() {
        World world = Scenario.testRoom(new Dice.Always(true));
        assertEquals(world.thrall, SpatialService.entityAt(world, new Vec2(3, 4)));

        Entity orc = world.get("o1");
        assertEquals(orc, SpatialService.entityAt(world, new Vec2(3, 5)));

        assertNull(SpatialService.entityAt(world, new Vec2(0, 0)));
    }

    @Test
    void testPassableChecksWallsAndOccupants() {
        World world = Scenario.testRoom(new Dice.Always(true));

        // (3,3) is a pillar wall
        assertFalse(SpatialService.passable(world, new Vec2(3, 3)));

        // (3,5) is occupied by Orc Guard
        assertFalse(SpatialService.passable(world, new Vec2(3, 5)));

        // (4,4) is empty floor inside room
        assertTrue(SpatialService.passable(world, new Vec2(4, 4)));
    }

    @Test
    void testHostilesAdjacentTo() {
        World world = Scenario.testRoom(new Dice.Always(true));
        // Thrall at (3,4); Orc at (3,5) is adjacent
        List<Entity> adj = SpatialService.hostilesAdjacentTo(world, world.thrall.pos);
        assertEquals(1, adj.size());
        assertEquals("o1", adj.get(0).id);
    }

    @Test
    void testLineOfSightClearAndBlocked() {
        World world = Scenario.testRoom(new Dice.Always(true));

        // Line of sight between thrall (3,4) and archer (8,4) is clear horizontally
        assertTrue(SpatialService.lineOfSight(world.map, new Vec2(3, 4), new Vec2(8, 4)));

        // Line of sight blocked by wall at (3,3)
        assertFalse(SpatialService.lineOfSight(world.map, new Vec2(3, 4), new Vec2(3, 2)));
    }
}
