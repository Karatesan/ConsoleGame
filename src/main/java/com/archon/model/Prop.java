package com.archon.model;

/** A non-creature world entity with an optional contained item. */
public class Prop extends Entity {
    private Item contents;

    public Prop(String id, String name, char glyph, Vec2 pos, int hp) {
        super(id, name, glyph, Kind.PROP, pos, hp, 0, 0);
    }

    public Item contents() { return contents; }
    public void setContents(Item item) { contents = item; }
    public Item removeContents() {
        Item item = contents;
        contents = null;
        return item;
    }
}
