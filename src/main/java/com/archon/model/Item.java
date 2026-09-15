package com.archon.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class Item {
    private final String id;
    private final String name;
    private final Set<Tag> tags;
    private final int damage;
    private Tag substance;
    private int durability;

    public Item(String id, String name, Set<Tag> tags, Tag substance, int durability, int damage) {
        this.id = id;
        this.name = name;

        EnumSet<Tag> tagCopy = EnumSet.noneOf(Tag.class);
        tagCopy.addAll(Objects.requireNonNull(tags, "tags"));
        this.tags = Collections.unmodifiableSet(tagCopy);

        this.substance = substance;
        this.durability = durability;
        this.damage = damage;
    }

    public static Item weapon(String id, String name, int damage, int durability, Tag... tags) {
        EnumSet<Tag> weaponTags = EnumSet.noneOf(Tag.class);
        if (tags != null) {
            Collections.addAll(weaponTags, tags);
        }
        return new Item(id, name, weaponTags, null, durability, damage);
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
        return tags;
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
        Tag consumedSubstance = substance;
        substance = null;
        return consumedSubstance;
    }

    public void wear(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Wear amount cannot be negative");
        }
        durability -= amount;
    }

    @Override
    public String toString() {
        return id;
    }
}