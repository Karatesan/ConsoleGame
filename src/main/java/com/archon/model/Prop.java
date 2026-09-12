package com.archon.model;

/**
 * Static or interactive inanimate props (e.g. barrels, braziers, chests).
 */
public class Prop extends Entity {
    private Item contents;

    public Prop(String id, String name, char glyph, Vec2 pos, int hp) {
        super(id, name, glyph, pos, hp, hp);
    }

    public Prop(String id, String name, char glyph, Vec2 pos, int hp, Item contents) {
        super(id, name, glyph, pos, hp, hp);
        this.contents = contents;
    }

    public Item contents() {
        return contents;
    }

    public void setContents(Item contents) {
        this.contents = contents;
    }

    public Item extractContents() {
        Item prev = this.contents;
        this.contents = null;
        return prev;
    }
}
