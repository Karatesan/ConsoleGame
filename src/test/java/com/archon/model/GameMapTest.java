package com.archon.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameMapTest {

    @Test
    void testBoundsChecking() {
        GameMap map = new GameMap(10, 8);
        assertTrue(map.inBounds(new Vec2(0, 0)));
        assertTrue(map.inBounds(new Vec2(9, 7)));
        assertFalse(map.inBounds(new Vec2(-1, 0)));
        assertFalse(map.inBounds(new Vec2(0, -1)));
        assertFalse(map.inBounds(new Vec2(10, 5)));
        assertFalse(map.inBounds(new Vec2(5, 8)));
        assertFalse(map.inBounds(null));
    }

    @Test
    void testTileAccessAndWallPlacement() {
        GameMap map = new GameMap(5, 5);
        World.Tile t = map.tile(new Vec2(2, 2));
        assertNotNull(t);
        assertFalse(t.wall);

        map.wall(2, 2);
        assertTrue(map.tile(new Vec2(2, 2)).wall);

        // Out of bounds tile access returns null
        assertNull(map.tile(new Vec2(-1, 2)));
        assertNull(map.tile(new Vec2(10, 10)));
    }
}
