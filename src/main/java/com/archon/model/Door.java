package com.archon.model;

/** A destructible door entity. */
public final class Door extends Entity {
    public Door(String id, String name, char glyph, Vec2 pos, int hp, int armor) {
        super(id, name, glyph, Kind.DOOR, pos, hp, 0, 0);
        setArmor(armor);
    }
}