package com.archon.ui;

import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;

import java.util.List;

/**
 * Flat snapshot of everything a renderer draws. Capture once per beat; diff two scenes
 * with {@link #indexOf} + {@link #moved} to get trails, damage flashes and new fire.
 * Entity 0 is always the Thrall.
 */
public final class Scene {
    private static final Tag[] COND_TAGS = {
            Tag.BURNING, Tag.WET, Tag.OIL, Tag.BLEEDING, Tag.CHILLED, Tag.FROZEN,
            Tag.CORRODED, Tag.DAZED, Tag.NUMB_ARM, Tag.CRIPPLED, Tag.STUNNED, Tag.BLIND, Tag.PRONE
    };

    public final int w, h;
    public final boolean[] wall, burning, oil, loot;
    public final boolean[] water, ice, acid, toxicTar, steam, causticGas, oilMist;

    public final int n;
    public final String[] id, name;
    public final char[] glyph;
    public final Entity.Kind[] kind;
    public final int[] x, y, hp, maxHp, entityTemp;
    public final boolean[] burningEntity, frozenEntity, stunnedEntity, bleedingEntity;

    public final int magic, concentration, temp;
    public final String channel, shortConds;
    public final boolean thrallStunned, thrallFrozen;

    public Scene(World world) {
        w = world.width();
        h = world.height();
        int size = w * h;
        wall = new boolean[size];
        burning = new boolean[size];
        oil = new boolean[size];
        loot = new boolean[size];
        water = new boolean[size];
        ice = new boolean[size];
        acid = new boolean[size];
        toxicTar = new boolean[size];
        steam = new boolean[size];
        causticGas = new boolean[size];
        oilMist = new boolean[size];
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                GameMap.Tile t = world.tile(new Vec2(tx, ty));
                int i = ty * w + tx;
                wall[i] = t.isWall();
                burning[i] = t.has(Tag.BURNING);
                oil[i] = t.has(Tag.OIL);
                loot[i] = !t.ground().isEmpty();
                water[i] = t.has(Tag.WATER);
                ice[i] = t.has(Tag.ICE);
                acid[i] = t.has(Tag.ACID);
                toxicTar[i] = t.has(Tag.ACID) && t.has(Tag.OIL);
                steam[i] = t.has(Tag.STEAM);
                causticGas[i] = t.has(Tag.CAUSTIC_GAS);
                oilMist[i] = t.has(Tag.OIL_MIST);
            }
        }

        Thrall thrall = world.thrall();
        magic = thrall.magic;
        concentration = thrall.channeledPayload != null ? Balance.CONC_BASE : 0;
        channel = thrall.channeledPayload == null
                ? "none"
                : thrall.channeledPayload.name() + " (" + thrall.channelTurns + "t)";
        temp = thrall.temp;
        thrallStunned = thrall.has(Tag.STUNNED);
        thrallFrozen = thrall.has(Tag.FROZEN);
        shortConds = formatShortConds(thrall);

        List<Entity> entities = world.entities();
        n = 1 + entities.size();
        id = new String[n];
        name = new String[n];
        glyph = new char[n];
        kind = new Entity.Kind[n];
        x = new int[n];
        y = new int[n];
        hp = new int[n];
        maxHp = new int[n];
        entityTemp = new int[n];
        burningEntity = new boolean[n];
        frozenEntity = new boolean[n];
        stunnedEntity = new boolean[n];
        bleedingEntity = new boolean[n];
        put(0, thrall);
        for (int e = 0; e < entities.size(); e++) put(e + 1, entities.get(e));
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
        entityTemp[i] = e.temp;
        burningEntity[i] = e.has(Tag.BURNING);
        frozenEntity[i] = e.has(Tag.FROZEN);
        stunnedEntity[i] = e.has(Tag.STUNNED);
        bleedingEntity[i] = e.has(Tag.BLEEDING);
    }

    static String formatShortConds(Thrall thrall) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COND_TAGS.length; i++) {
            if (thrall.has(COND_TAGS[i])) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(COND_TAGS[i].name());
            }
        }
        BodyPart[] parts = BodyPart.values();
        for (int i = 0; i < parts.length; i++) {
            if (thrall.isCrippled(parts[i])) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append("CRIPPLED(").append(parts[i].path).append(')');
            }
        }
        return sb.isEmpty() ? "none" : sb.toString();
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
