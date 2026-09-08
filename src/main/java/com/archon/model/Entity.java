package com.archon.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class Entity {
    public enum Kind { CREATURE, PROP, DOOR }
    public enum Trigger { ON_ADJACENCY, ON_MOVEMENT_IN_LOS }

    /** A deterministic, visible reaction. Fires at most once per round. */
    public record Readied(Trigger trigger, String description, int damage) {}

    public final String id;
    public final String name;
    public final char glyph;
    public final Kind kind;
    public Vec2 pos;
    public int hp, maxHp, armor, evasion;
    public final Set<Tag> tags = EnumSet.noneOf(Tag.class);
    public final Map<BodyPart, Boolean> crippled = new EnumMap<>(BodyPart.class);
    public Readied readied;
    public boolean readiedSpent;
    public boolean guarded;
    public Item held;
    public boolean identified;

    public Entity(String id, String name, char glyph, Kind kind, Vec2 pos, int hp) {
        this.id = id; this.name = name; this.glyph = glyph; this.kind = kind;
        this.pos = pos; this.hp = hp; this.maxHp = hp;
    }

    public boolean alive() { return hp > 0; }
    public boolean has(Tag t) { return tags.contains(t); }
    public Entity with(Tag... t) { tags.addAll(Set.of(t)); return this; }
    public Entity ready(Trigger tr, String desc, int dmg) {
        this.readied = new Readied(tr, desc, dmg); return this;
    }

    // ---------- Domain Methods (Phase 5) ----------

    public int applyDamage(int amount) {
        this.hp -= amount;
        return this.hp;
    }

    public int takeDamage(int amount) {
        return applyDamage(amount);
    }

    public void ignite() {
        tags.add(Tag.BURNING);
    }

    public void extinguish() {
        tags.remove(Tag.BURNING);
    }

    public Item disarm() {
        Item prev = this.held;
        this.held = null;
        return prev;
    }

    public void applyTag(Tag t) {
        tags.add(t);
    }

    public void removeTag(Tag t) {
        tags.remove(t);
    }

    @Override public String toString() { return id; }
}