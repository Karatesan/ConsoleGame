package com.archon.model;

/**
 * Structural door entity with configurable health and no default defenses.
 */
public final class Door extends Entity {

    public Door(
            String id,
            String name,
            char glyph,
            Vec2 pos,
            int hp
    ) {
        super(id, name, glyph, Kind.DOOR, pos, hp, 0, 0);
    }
}