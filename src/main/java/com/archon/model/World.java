package com.archon.model;

import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class World {

    public static final class Tile {
        public boolean wall;
        public final Set<Tag> tags = EnumSet.noneOf(Tag.class);
        public final Set<Tag> ceiling = EnumSet.noneOf(Tag.class);
        public final List<Item> ground = new ArrayList<>();

        public boolean has(Tag t) {return tags.contains(t);}
    }

    public final int w, h;
    public final GameMap map;
    public final Map<String, Entity> entities = new LinkedHashMap<>();
    public Thrall thrall;
    public Dice dice;
    public int roundNumber = 1;

    /**
     * Set while a line is executing; consumed by ON_MOVEMENT_IN_LOS triggers.
     */
    public boolean thrallMovedThisLine;

    public World(int w, int h, Dice dice) {
        this.w = w;
        this.h = h;
        this.dice = dice;
        this.map = new GameMap(w, h);
    }

    public boolean inBounds(Vec2 p) { return map.inBounds(p); }

    public Tile tile(Vec2 p) { return map.tile(p); }

    public void wall(int x, int y) { map.wall(x, y); }

    public void add(Entity e) { entities.put(e.id, e); }

    public Entity get(String id) { return id.equals("self") ? thrall : entities.get(id); }

    public Entity entityAt(Vec2 p) {
        return SpatialService.entityAt(this, p);
    }

    public boolean passable(Vec2 p) {
        return SpatialService.passable(this, p);
    }

    public List<Entity> hostilesAdjacentTo(Vec2 p) {
        return SpatialService.hostilesAdjacentTo(this, p);
    }

    public boolean lineOfSight(Vec2 a, Vec2 b) {
        return SpatialService.lineOfSight(map, a, b);
    }

    // ---------- Deterministic interrupts ----------

    public Entity pendingInterrupt() {
        return ReactionSystem.pendingInterrupt(this);
    }

    public int resolveInterrupt(Entity e) {
        return ReactionSystem.resolveInterrupt(this, e);
    }

    // ---------- World tick ----------

    public List<String> tick() {
        return SimulationSystem.tick(this);
    }
}