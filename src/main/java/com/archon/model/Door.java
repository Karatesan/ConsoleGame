package com.archon.model;

/**
 * Structural door entity with no default defenses.
 */
public final class Door extends Entity {

    public Door(
            String id,
            String name,
            char glyph,
            Vec2 pos
    ) {
        super(id, name, glyph, Kind.DOOR, pos, 0, 0, 0);
    }
}