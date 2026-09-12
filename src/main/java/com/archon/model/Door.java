package com.archon.model;

/**
 * Structural door entity with armor and open/closed state.
 */
public class Door extends Entity {
    private int armor;
    private boolean open;

    public Door(String id, String name, char glyph, Vec2 pos, int hp, int armor) {
        super(id, name, glyph, pos, hp, hp);
        this.armor = armor;
        this.open = false;
    }

    @Override
    public int armor() {
        return armor;
    }

    public void setArmor(int armor) {
        this.armor = armor;
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
