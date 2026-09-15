package com.archon.model;

/**
 * Structural door entity with armor and open/closed state.
 */
public class Door extends Entity {

    private boolean open;

    public Door(
            String id,
            String name,
            char glyph,
            Vec2 pos,
            int hp,
            int armor
    ) {
        super(id, name, glyph, Kind.DOOR, pos, hp);
        setArmor(armor);
        this.open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        this.open = true;
    }

    public void close() {
        this.open = false;
    }
}
