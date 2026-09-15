package com.archon.model;

/**
 * Static or interactive inanimate props (e.g. barrels, braziers, chests).
 */
public class Prop extends Entity {

    public Prop(String id, String name, char glyph, Vec2 pos, int hp) {
        super(id, name, glyph, Kind.PROP, pos, hp);
    }

    public Prop(
            String id,
            String name,
            char glyph,
            Vec2 pos,
            int hp,
            Item contents
    ) {
        this(id, name, glyph, pos, hp);
        setContents(contents);
    }

    public Item contents() {
        return getHeld();
    }

    public void setContents(Item contents) {
        setHeld(contents);
    }

    public Item extractContents() {
        return disarm();
    }
}
