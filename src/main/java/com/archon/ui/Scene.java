package com.archon.ui;

import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;

/**
 * Flat snapshot of everything a renderer draws. Capture once per beat; diff two scenes
 * with {@link #indexOf} + {@link #moved} to get trails, damage flashes and new fire.
 * Entity 0 is always the Thrall.
 */
public final class Scene {
    public final int w, h;
    public final boolean[] wall, burning, oil, loot;

    public final int n;
    public final String[] id, name;
    public final char[] glyph;
    public final Entity.Kind[] kind;
    public final int[] x, y, hp, maxHp;
    public final boolean[] burningEntity;

    public Scene(World world) {
        w = world.width();
        h = world.height();
        wall = new boolean[w * h];
        burning = new boolean[w * h];
        oil = new boolean[w * h];
        loot = new boolean[w * h];
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                GameMap.Tile t = world.tile(new Vec2(tx, ty));
                int i = ty * w + tx;
                wall[i] = t.isWall();
                burning[i] = t.has(Tag.BURNING);
                oil[i] = t.has(Tag.OIL);
                loot[i] = !t.ground().isEmpty();
            }
        }

        n = 1 + world.entities().size();
        id = new String[n];
        name = new String[n];
        glyph = new char[n];
        kind = new Entity.Kind[n];
        x = new int[n];
        y = new int[n];
        hp = new int[n];
        maxHp = new int[n];
        burningEntity = new boolean[n];
        int i = 0;
        put(i++, world.thrall());
        for (Entity e : world.entities()) put(i++, e);
    }

    private void put(int i, Entity e) {
        id[i] = e.id();
        name[i] = e.name();
        glyph[i] = e.glyph();
        kind[i] = e.kind();
        x[i] = e.pos().x();
        y[i] = e.pos().y();
        hp[i] = e.hp();
        maxHp[i] = e.maxHp();
        burningEntity[i] = e.has(Tag.BURNING);
    }

    /** Index of the entity with this id, or -1. */
    public int indexOf(String entityId) {
        for (int i = 0; i < n; i++) if (id[i].equals(entityId)) return i;
        return -1;
    }

    /** True if entity i of this scene stands elsewhere than in prev (false if absent from prev). */
    public boolean moved(int i, Scene prev) {
        int p = prev.indexOf(id[i]);
        return p >= 0 && (prev.x[p] != x[i] || prev.y[p] != y[i]);
    }

    /** HP change since prev (negative = damage); 0 if absent from prev. */
    public int hpDelta(int i, Scene prev) {
        int p = prev.indexOf(id[i]);
        return p < 0 ? 0 : hp[i] - prev.hp[p];
    }

    public boolean newlyBurning(int tx, int ty, Scene prev) {
        return burning[ty * w + tx] && !prev.burning[ty * w + tx];
    }
}
