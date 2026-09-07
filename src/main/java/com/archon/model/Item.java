package com.archon.model;

import java.util.EnumSet;
import java.util.Set;

public final class Item {
    public final String id;
    public final String name;
    public final Set<Tag> tags;
    /** Substance held by a container item, e.g. OIL inside a flask. Nullable. */
    public Tag substance;
    public int durability;
    public int damage;

    public Item(String id, String name, Set<Tag> tags, Tag substance, int durability, int damage) {
        this.id = id; this.name = name;
        this.tags = EnumSet.copyOf(tags);
        this.substance = substance; this.durability = durability; this.damage = damage;
    }

    public static Item weapon(String id, String name, int damage, int durability, Tag... tags) {
        return new Item(id, name, EnumSet.copyOf(Set.of(tags)), null, durability, damage);
    }

    public static Item flask(String id, String name, Tag substance) {
        return new Item(id, name, EnumSet.of(Tag.GLASS, Tag.BREAKABLE), substance, 1, 0);
    }

    public boolean has(Tag t) { return tags.contains(t); }
    @Override public String toString() { return id; }
}