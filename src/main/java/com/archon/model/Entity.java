package com.archon.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Entity {
    public enum Kind {CREATURE, PROP, DOOR}

    public enum Trigger {ON_ADJACENCY, ON_MOVEMENT_IN_LOS}

    /**
     * A deterministic, visible reaction. Fires at most once per round.
     */
    public record Readied(Trigger trigger, String description, int damage) {}

    private final String id;
    private final String name;
    private final char glyph;
    private final Kind kind;
    private Vec2 pos;
    private int hp;
    private int maxHp;
    private int armor;
    private int evasion;
    private final Set<Tag> tags = EnumSet.noneOf(Tag.class);
    private final Map<BodyPart, Boolean> crippled = new EnumMap<>(BodyPart.class);
    private Readied readied;
    private boolean readiedSpent;
    private boolean guarded;
    private boolean identified;

    public Entity(String id, String name, char glyph, Kind kind, Vec2 pos, int hp, int armor, int evasion) {
        if (hp < 0) {
            throw new IllegalArgumentException("hp must not be negative");
        }

        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.glyph = glyph;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.pos = Objects.requireNonNull(pos, "pos");
        this.hp = hp;
        this.maxHp = hp;
        this.armor = armor;
        this.evasion = evasion;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public char glyph() {
        return glyph;
    }

    public Kind kind() {
        return kind;
    }

    public Vec2 pos() {
        return pos;
    }

    public int hp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public int armor() {
        return armor;
    }

    public int evasion() {
        return evasion;
    }

    public boolean alive() {
        return hp > 0;
    }

    public Set<Tag> tags() {
        return Collections.unmodifiableSet(tags);
    }

    public Map<BodyPart, Boolean> crippled() {
        return Collections.unmodifiableMap(crippled);
    }

    public boolean has(Tag tag) {
        return tags.contains(tag);
    }

    public Entity with(Tag... tags) {
        Objects.requireNonNull(tags, "tags");
        for (Tag tag : tags) {
            this.tags.add(Objects.requireNonNull(tag, "tag"));
        }
        return this;
    }

    public void applyTag(Tag tag) {
        tags.add(Objects.requireNonNull(tag, "tag"));
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
    }

    public void ignite() {
        tags.add(Tag.BURNING);
    }

    public void extinguish() {
        tags.remove(Tag.BURNING);
    }

    public void moveTo(Vec2 pos) {
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    public int takeDamage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("damage amount must not be negative");
        }
        hp -= amount;
        return hp;
    }

    public int heal(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("healing amount must not be negative");
        }
        hp = Math.min(maxHp, hp + amount);
        return hp;
    }

    public void setMaxHp(int maxHp) {
        if (maxHp < 0) {
            throw new IllegalArgumentException("maxHp must not be negative");
        }
        this.maxHp = maxHp;
        if (hp > maxHp) {
            hp = maxHp;
        }
    }

    public void setArmor(int armor) {
        this.armor = armor;
    }

    public void setEvasion(int evasion) {
        this.evasion = evasion;
    }

    public Entity ready(Trigger trigger, String description, int damage) {
        this.readied = new Readied(
                Objects.requireNonNull(trigger, "trigger"),
                Objects.requireNonNull(description, "description"),
                damage
        );
        return this;
    }

    public Readied readied() {
        return readied;
    }

    public boolean isReadiedSpent() {
        return readiedSpent;
    }

    public void spendReadied() {
        readiedSpent = true;
    }

    public void resetRoundState() {
        readiedSpent = false;
        guarded = false;
    }

    public boolean isGuarded() {
        return guarded;
    }

    public void setGuarded(boolean guarded) {
        this.guarded = guarded;
    }

    public boolean isIdentified() {
        return identified;
    }

    public void identify() {
        identified = true;
    }

    @Override
    public String toString() {
        return id;
    }
}