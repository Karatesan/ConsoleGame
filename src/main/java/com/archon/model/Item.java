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
    private final int minDamage;
    private final int maxDamage;

    public Item(String id, String name, Set<Tag> tags, Tag substance, int durability, int damage) {
        this(id, name, tags, substance, durability, damage, damage);
    }

    public Item(String id, String name, Set<Tag> tags, Tag substance, int durability, int minDamage, int maxDamage) {
        this.id = id;
        this.name = name;
        this.tags = tags.isEmpty() ? EnumSet.noneOf(Tag.class) : EnumSet.copyOf(tags);
        this.substance = substance;
        this.durability = durability;
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
    }

    public static Item weapon(String id, String name, int minDamage, int maxDamage, int durability, Tag... tags) {
        EnumSet<Tag> itemTags = EnumSet.noneOf(Tag.class);
        if (tags != null) {
            Collections.addAll(itemTags, tags);
        }
        return new Item(id, name, itemTags, null, durability, minDamage, maxDamage);
    }

    public static Item flask(String id, String name, Tag substance) {
        return new Item(id, name, EnumSet.of(Tag.GLASS, Tag.BREAKABLE), substance, 1, 0, 0);
    }

    public String id() { return id; }
    public String name() { return name; }
    public Set<Tag> tags() { return tags; }
    public Tag substance() { return substance; }
    public int durability() { return durability; }
    public int minDamage() { return minDamage; }
    public int maxDamage() { return maxDamage; }
    public int damage() { return maxDamage; }

    public boolean has(Tag tag) {
        return tags.contains(tag);
    }

    public Tag consumeSubstance() {
        Tag value = substance;
        substance = null;
        return value;
    }

    public void wear(int amount) {
        durability = Math.max(0, durability - amount);
    }

    @Override
    public String toString() {
        return id;
    }
}