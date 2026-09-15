package com.archon.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class Entity {
    public enum Kind { CREATURE, PROP, DOOR }
    public enum Trigger { ON_ADJACENCY, ON_MOVEMENT_IN_LOS }
    public record Readied(Trigger trigger, String description, int damage) { }

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
        this.id = id;
        this.name = name;
        this.glyph = glyph;
        this.kind = kind;
        this.pos = pos;
        this.hp = hp;
        this.maxHp = hp;
        this.armor = armor;
        this.evasion = evasion;
    }

    public String id() { return id; }
    public String name() { return name; }
    public char glyph() { return glyph; }
    public Kind kind() { return kind; }
    public Vec2 pos() { return pos; }
    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public int armor() { return armor; }
    public int evasion() { return evasion; }
    public boolean alive() { return hp > 0; }
    public Set<Tag> tags() { return Collections.unmodifiableSet(tags); }
    public Map<BodyPart, Boolean> crippled() { return Collections.unmodifiableMap(crippled); }
    public boolean has(Tag tag) { return tags.contains(tag); }
    public boolean isIdentified() { return identified; }
    public boolean isGuarded() { return guarded; }
    public Readied readied() { return readied; }
    public boolean isReadiedSpent() { return readiedSpent; }

    public Entity with(Tag... values) { Collections.addAll(tags, values); return this; }
    public Entity ready(Trigger trigger, String description, int damage) { readied = new Readied(trigger, description, damage); return this; }
    public void moveTo(Vec2 destination) { pos = destination; }
    public int takeDamage(int amount) { if (amount < 0) throw new IllegalArgumentException("damage cannot be negative"); hp -= amount; return hp; }
    public int heal(int amount) { if (amount < 0) throw new IllegalArgumentException("healing cannot be negative"); hp += amount; return hp; }
    public void setMaxHp(int value) { if (value < 0) throw new IllegalArgumentException("maximum HP cannot be negative"); maxHp = value; if (hp > maxHp) hp = maxHp; }
    public void setArmor(int value) { armor = value; }
    public void setEvasion(int value) { evasion = value; }
    public void setGuarded(boolean value) { guarded = value; }
    public void spendReadied() { readiedSpent = true; }
    public void resetRoundState() { readiedSpent = false; guarded = false; }
    public void identify() { identified = true; }
    public void ignite() { tags.add(Tag.BURNING); }
    public void extinguish() { tags.remove(Tag.BURNING); }
    public void applyTag(Tag tag) { tags.add(tag); }
    public void removeTag(Tag tag) { tags.remove(tag); }

    @Override public String toString() { return id; }
}