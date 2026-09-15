package com.archon.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public abstract class Entity {

    public enum Kind {
        CREATURE, PROP, DOOR
    }

    public enum Trigger {
        ON_ADJACENCY, ON_MOVEMENT_IN_LOS
    }

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
    private final Map<BodyPart, Boolean> crippled =
            new EnumMap<>(BodyPart.class);

    private Readied readied;
    private boolean readiedSpent;
    private boolean guarded;
    private Item held;
    private boolean identified;

    protected Entity(
            String id,
            String name,
            char glyph,
            Kind kind,
            Vec2 pos,
            int hp
    ) {
        this.id = id;
        this.name = name;
        this.glyph = glyph;
        this.kind = kind;
        this.pos = pos;
        this.hp = hp;
        this.maxHp = hp;
    }

    // ---------- Domain Methods ----------

    public boolean alive() {
        return hp > 0;
    }

    public boolean has(Tag tag) {
        return tags.contains(tag);
    }

    public Entity with(Tag... tags) {
        Collections.addAll(this.tags, tags);
        return this;
    }

    public Entity ready(Trigger trigger, String description, int damage) {
        this.readied = new Readied(trigger, description, damage);
        this.readiedSpent = false;
        return this;
    }

    public void clearReadied() {
        this.readied = null;
        this.readiedSpent = false;
    }

    public boolean canReact() {
        return alive() && readied != null && !readiedSpent;
    }

    public void spendReaction() {
        this.readiedSpent = true;
    }

    public void resetReaction() {
        this.readiedSpent = false;
    }

    public void resetRoundState() {
        this.guarded = false;
        this.readiedSpent = false;
    }

    public int takeDamage(int amount) {
        this.hp -= amount;
        return this.hp;
    }

    public int heal(int amount) {
        this.hp = Math.min(this.maxHp, this.hp + amount);
        return this.hp;
    }

    public void ignite() {
        tags.add(Tag.BURNING);
    }

    public void extinguish() {
        tags.remove(Tag.BURNING);
    }

    public Item disarm() {
        Item previous = this.held;
        this.held = null;
        return previous;
    }

    public void applyTag(Tag tag) {
        tags.add(tag);
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
    }

    public boolean isCrippled(BodyPart part) {
        return Boolean.TRUE.equals(crippled.get(part));
    }

    public void setCrippled(BodyPart part, boolean value) {
        crippled.put(part, value);
    }

    public void cripple(BodyPart part) {
        setCrippled(part, true);
    }

    public void restoreBodyPart(BodyPart part) {
        setCrippled(part, false);
    }

    // ---------- Actor-style Accessors ----------

    public int armor() {
        return armor;
    }

    public int evasion() {
        return evasion;
    }

    public Readied readied() {
        return readied;
    }

    public Map<BodyPart, Boolean> crippled() {
        return Collections.unmodifiableMap(crippled);
    }

    // ---------- Getters and Setters ----------

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public char getGlyph() {
        return glyph;
    }

    public Kind getKind() {
        return kind;
    }

    public Vec2 getPos() {
        return pos;
    }

    public void setPos(Vec2 pos) {
        this.pos = pos;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public int getArmor() {
        // Dispatches to Actor.armor() when this entity is an Actor.
        return armor();
    }

    public void setArmor(int armor) {
        this.armor = armor;
    }

    public void setEvasion(int evasion) {
        this.evasion = evasion;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public Map<BodyPart, Boolean> getCrippled() {
        return crippled;
    }

    public Readied getReadied() {
        return readied();
    }

    public void setReadied(Readied readied) {
        this.readied = readied;
    }

    public boolean isReadiedSpent() {
        return readiedSpent;
    }

    public void setReadiedSpent(boolean readiedSpent) {
        this.readiedSpent = readiedSpent;
    }

    public boolean isGuarded() {
        return guarded;
    }

    public void setGuarded(boolean guarded) {
        this.guarded = guarded;
    }

    public Item getHeld() {
        return held;
    }

    public void setHeld(Item held) {
        this.held = held;
    }

    public boolean isIdentified() {
        return identified;
    }

    public void setIdentified(boolean identified) {
        this.identified = identified;
    }

    @Override
    public String toString() {
        return id;
    }
}