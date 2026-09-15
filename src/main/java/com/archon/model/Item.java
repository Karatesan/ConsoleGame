package com.archon.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class Item {
    private final String id;
    private final String name;
    private final Set<Tag> tags;
    private Tag substance;
    private int durability;
    private final int damage;

    public Item(String id, String name, Set<Tag> tags, Tag substance, int durability, int damage) {
        this.id = id;
        this.name = name;

        EnumSet<Tag> copiedTags = EnumSet.noneOf(Tag.class);
        copiedTags.addAll(tags);
        this.tags = copiedTags;

        this.substance = substance;
        this.durability = durability;
        this.damage = damage;
    }

    public static Item weapon(String id, String name, int damage, int durability, Tag... tags) {
        EnumSet<Tag> itemTags = EnumSet.noneOf(Tag.class);
        if (tags != null) {
            Collections.addAll(itemTags, tags);
        }
        return new Item(id, name, itemTags, null, durability, damage);
    }

    public static Item flask(String id, String name, Tag substance) {
        return new Item(id, name, EnumSet.of(Tag.GLASS, Tag.BREAKABLE), substance, 1, 0);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<Tag> tags() {
        return Collections.unmodifiableSet(tags);
    }

    public Tag substance() {
        return substance;
    }

    public int durability() {
        return durability;
    }

    public int damage() {
        return damage;
    }

    public boolean has(Tag tag) {
        return tags.contains(tag);
    }

    public Tag consumeSubstance() {
        Tag value = substance;
        substance = null;
        return value;
    }

    public void wear(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("wear cannot be negative");
        }
        durability -= amount;
    }

    @Override
    public String toString() {
        return id;
    }
}