package com.archon.model;

/**
 * Static or interactive inanimate props (e.g. barrels, braziers, chests).
 */
public class Prop extends Entity {

    private Item contents;

    public Prop(String id, String name, char glyph, Vec2 pos, int hp) {
        super(id, name, glyph, Kind.PROP, pos, hp, 0, 0);
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
        return contents;
    }

    public void setContents(Item contents) {
        this.contents = contents;
    }

    public Item removeContents() {
        Item removedContents = contents;
        contents = null;
        return removedContents;
    }
}