package com.archon.system.spatial;

import com.archon.model.Actor;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpatialServiceTest {

    @Test
    void allThreeGasesBlockNormalLosUnlessIgnoreGasIsTrue() {
        Tag[] gases = {Tag.STEAM, Tag.CAUSTIC_GAS, Tag.OIL_MIST};
        for (Tag gas : gases) {
            World world = new World(8, 8, new Dice.Seeded(1L));
            Vec2 a = new Vec2(1, 2);
            Vec2 b = new Vec2(5, 2);
            Vec2 mid = new Vec2(3, 2);
            world.map().addTag(mid, gas);

            assertTrue(SpatialService.hasGas(world.tile(mid)));
            assertFalse(SpatialService.lineOfSight(world, a, b), gas + " should block normal LoS");
            assertTrue(SpatialService.lineOfSight(world, a, b, true), gas + " should not block ignoreGas LoS");
            assertTrue(SpatialService.lineOfSight(world, a, mid), "Gas on target endpoint itself should not block LoS");
        }
    }

    @Test
    void firstGasOnLineStopsAtWallAndFindsNearestGasTile() {
        World world = new World(10, 5, new Dice.Seeded(1L));
        Vec2 origin = new Vec2(1, 2);
        Vec2 target = new Vec2(7, 2);
        world.map().addTag(new Vec2(3, 2), Tag.STEAM);
        world.map().addTag(new Vec2(5, 2), Tag.OIL_MIST);

        assertEquals(new Vec2(3, 2), SpatialService.firstGasOnLine(world, origin, target));

        world.map().setWall(new Vec2(2, 2), true);
        assertNull(SpatialService.firstGasOnLine(world, origin, target), "Wall before gas stops ray");
    }

    @Test
    void siphonLineOfFireSupportsAdjacentDiagonalsAndCardinalWithGasEndpoints() {
        World world = new World(10, 10, new Dice.Seeded(1L));
        world.installThrall(new Thrall(new Vec2(2, 2), 40));

        // Chebyshev 1 diagonal
        assertTrue(SpatialService.siphonLineOfFire(world, new Vec2(2, 2), new Vec2(3, 3)));

        // Cardinal Manhattan 4 with gas endpoint allowed
        Vec2 gasEnd = new Vec2(6, 2);
        world.map().addTag(gasEnd, Tag.STEAM);
        assertTrue(SpatialService.siphonLineOfFire(world, new Vec2(2, 2), gasEnd));

        // Beyond SIPHON_REACH_MANHATTAN (5 tiles) fails
        assertFalse(SpatialService.siphonLineOfFire(world, new Vec2(2, 2), new Vec2(7, 2)));

        // Intervening gas blocks cardinal siphon
        world.map().addTag(new Vec2(4, 2), Tag.CAUSTIC_GAS);
        assertFalse(SpatialService.siphonLineOfFire(world, new Vec2(2, 2), gasEnd));

        // Intervening solid entity blocks cardinal siphon
        world.map().removeTag(new Vec2(4, 2), Tag.CAUSTIC_GAS);
        world.add(new Actor("o1", "Orc", 'O', new Vec2(4, 2), new CreatureStats(20, 1, 0)));
        assertFalse(SpatialService.siphonLineOfFire(world, new Vec2(2, 2), gasEnd));
    }
}
