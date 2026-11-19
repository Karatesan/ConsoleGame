package com.archon.model;

import java.util.Set;

/** A non-creature world entity with an optional contained item. */
public class Prop extends Entity {
    private Item contents;

    public Prop(String id, String name, char glyph, Vec2 pos, int hp) {
        super(id, name, glyph, Kind.PROP, pos, hp, 0, 0);
    }

    public static Prop brazier(String id, Vec2 pos) {
        Prop p = new Prop(id, "Brazier", 'i', pos, 10);
        p.with(Tag.METAL, Tag.FLAMMABLE, Tag.LIT);
        return p;
    }

    public static Prop oilBarrel(String id, Vec2 pos) {
        Prop p = new Prop(id, "Oil Barrel", 'B', pos, 8);
        p.with(Tag.WOOD, Tag.CONTAINER, Tag.FLAMMABLE, Tag.BREAKABLE);
        p.setContents(new Item("oil", "oil", Set.of(Tag.LIQUID, Tag.OIL, Tag.FLAMMABLE), Tag.OIL, 1, 0));
        return p;
    }

    public static Prop woodCrate(String id, Vec2 pos) {
        Prop p = new Prop(id, "Wood Crate", 'c', pos, 8);
        p.with(Tag.WOOD, Tag.BREAKABLE, Tag.FLAMMABLE);
        return p;
    }

    public static Prop stonePillar(String id, Vec2 pos) {
        Prop p = new Prop(id, "Stone Pillar", 'S', pos, 25);
        p.with(Tag.STONE, Tag.HEAVY);
        return p;
    }

    public Item contents() {
        return contents;
    }

    public void setContents(Item item) {
        contents = item;
    }

    public Item removeContents() {
        Item item = contents;
        contents = null;
        return item;
    }
}