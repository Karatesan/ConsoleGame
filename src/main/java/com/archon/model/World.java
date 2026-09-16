package com.archon.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class World {

    private final GameMap map;
    private final Map<String, Entity> entities = new LinkedHashMap<>();
    private final Dice dice;
    private Thrall thrall;
    private int roundNumber = 1;
    private boolean thrallMovedThisLine;

    public World(int w, int h, Dice dice) {
        this.map = new GameMap(w, h);
        this.dice = Objects.requireNonNull(dice, "dice");
    }

    public int width() {
        return map.width();
    }

    public int height() {
        return map.height();
    }

    public GameMap map() {
        return map;
    }

    public Dice dice() {
        return dice;
    }

    public int roundNumber() {
        return roundNumber;
    }

    public void advanceRound() {
        roundNumber++;
    }

    public Thrall thrall() {
        return thrall;
    }

    public void installThrall(Thrall thrall) {
        Objects.requireNonNull(thrall, "thrall");

        if (this.thrall != null && this.thrall != thrall) {
            throw new IllegalStateException("A different thrall is already installed");
        }

        String id = Objects.requireNonNull(thrall.id(), "thrall.id");
        if (entities.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Thrall ID collides with a registered entity: " + id
            );
        }

        this.thrall = thrall;
    }

    public Collection<Entity> entities() {
        return Collections.unmodifiableCollection(entities.values());
    }

    public boolean inBounds(Vec2 p) {
        return map.inBounds(p);
    }

    public GameMap.Tile tile(Vec2 p) {
        return map.tile(p);
    }

    public void add(Entity entity) {
        Objects.requireNonNull(entity, "entity");

        String id = Objects.requireNonNull(entity.id(), "entity.id");
        if ("self".equals(id)) {
            throw new IllegalArgumentException("Entity ID 'self' is reserved");
        }
        if (thrall != null && id.equals(thrall.id())) {
            throw new IllegalArgumentException(
                    "Entity ID collides with the installed thrall: " + id
            );
        }
        if (entities.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate entity ID: " + id);
        }

        entities.put(id, entity);
    }

    public Entity get(String id) {
        if (id == null) {
            return null;
        }
        return "self".equals(id) ? thrall : entities.get(id);
    }

    public Actor actor(String id) {
        Entity entity = get(id);
        return entity instanceof Actor actor ? actor : null;
    }

    public void markThrallMoved() {
        thrallMovedThisLine = true;
    }

    public void resetThrallMovement() {
        thrallMovedThisLine = false;
    }

    public boolean thrallMovedThisLine() {
        return thrallMovedThisLine;
    }
}