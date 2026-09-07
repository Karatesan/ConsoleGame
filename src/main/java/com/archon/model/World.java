package com.archon.model;

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
    private final Tile[][] tiles;
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
        tiles = new Tile[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) tiles[y][x] = new Tile();
    }

    public boolean inBounds(Vec2 p) {return p.x() >= 0 && p.y() >= 0 && p.x() < w && p.y() < h;}

    public Tile tile(Vec2 p) {return inBounds(p) ? tiles[p.y()][p.x()] : null;}

    public void wall(int x, int y) {tiles[y][x].wall = true;}

    public void add(Entity e) {entities.put(e.id, e);}

    public Entity get(String id) {return id.equals("self") ? thrall : entities.get(id);}

    public Entity entityAt(Vec2 p) {
        if (thrall != null && thrall.pos.equals(p)) return thrall;
        return entities.values().stream().filter(e -> e.alive() && e.pos.equals(p)).findFirst().orElse(null);
    }

    public boolean passable(Vec2 p) {
        Tile t = tile(p);
        return t != null && !t.wall && entityAt(p) == null;
    }

    public List<Entity> hostilesAdjacentTo(Vec2 p) {
        return entities.values().stream().filter(e -> e.alive() && e.kind == Entity.Kind.CREATURE).filter(
                e -> e.pos.chebyshev(p) <= 1).toList();
    }

    public boolean lineOfSight(Vec2 a, Vec2 b) {
        int dx = Math.abs(b.x() - a.x()), dy = Math.abs(b.y() - a.y());
        int sx = a.x() < b.x() ? 1 : -1, sy = a.y() < b.y() ? 1 : -1;
        int err = dx - dy, x = a.x(), y = a.y();
        while (x != b.x() || y != b.y()) {
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
            if (x == b.x() && y == b.y()) break;
            Tile t = tile(new Vec2(x, y));
            if (t == null || t.wall) return false;
        }
        return true;
    }

    // ---------- Deterministic interrupts ----------

    public Entity pendingInterrupt() {
        for (Entity e : entities.values()) {
            if (!e.alive() || e.readied == null || e.readiedSpent) continue;
            switch (e.readied.trigger()) {
                case ON_ADJACENCY -> {
                    if (e.pos.chebyshev(thrall.pos) <= 1) return e;
                }
                case ON_MOVEMENT_IN_LOS -> {
                    if (thrallMovedThisLine && lineOfSight(e.pos, thrall.pos)) return e;
                }
            }
        }
        return null;
    }

    public int resolveInterrupt(Entity e) {
        e.readiedSpent = true;
        int dmg = e.readied.damage();
        thrall.hp -= dmg;
        return dmg;
    }

    // ---------- World tick ----------

    public List<String> tick() {
        List<String> log = new ArrayList<>();
        roundNumber++;

        for (Entity e : new ArrayList<>(entities.values())) {
            if (e.alive() && e.has(Tag.BURNING)) {
                e.hp -= 3;
                log.add(e.name + " burns for 3.");
                if (!e.alive()) log.add(e.name + " is consumed.");
            }
            e.readiedSpent = false;
            e.guarded = false;
        }
        if (thrall.has(Tag.BURNING)) {
            thrall.hp -= 3;
            log.add("Thrall burns for 3.");
        }

        // Fire spreads across contiguous oil.
        List<Vec2> ignite = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                Vec2 p = new Vec2(x, y);
                Tile t = tile(p);
                if (t.has(Tag.BURNING)) {
                    for (Vec2 d : List.of(Vec2.dir("n"), Vec2.dir("s"), Vec2.dir("e"), Vec2.dir("w"))) {
                        Vec2 q = p.plus(d);
                        Tile u = tile(q);
                        if (u != null && u.has(Tag.OIL) && !u.has(Tag.BURNING)) ignite.add(q);
                    }
                }
            }
        for (Vec2 p : ignite) {
            tile(p).tags.add(Tag.BURNING);
            log.add("Fire spreads to " + p + ".");
        }

        return log;
    }
}