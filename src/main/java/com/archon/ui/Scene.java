package com.archon.ui;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.GameMap;
import com.archon.model.Inventory;
import com.archon.model.Item;
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
    public static final byte LOOT_NONE = 0, LOOT_BLADE = 1, LOOT_FLASK_OIL = 2,
            LOOT_FLASK_WATER = 3, LOOT_FLASK_ACID = 4, LOOT_STONE = 5;

    private static final Tag[] COND_TAGS = {
            Tag.BURNING, Tag.WET, Tag.OIL, Tag.BLEEDING, Tag.CHILLED, Tag.FROZEN,
            Tag.CORRODED, Tag.DAZED, Tag.NUMB_ARM, Tag.CRIPPLED, Tag.STUNNED, Tag.BLIND, Tag.PRONE
    };

    public final int w, h;
    public final boolean[] wall, burning, oil, loot;
    public final boolean[] water, ice, acid, tar, steam, superheatedSteam, oilMist;
    public final byte[] lootKind;
    public final float[] light;

    public final int n;
    public final String[] id, name;
    public final char[] glyph;
    public final Entity.Kind[] kind;
    public final int[] x, y, hp, maxHp, entityTemp;
    public final boolean[] burningEntity, frozenEntity, stunnedEntity, bleedingEntity;
    public final boolean[] proneEntity, wetEntity, oiledEntity, litEntity, overwatchEntity;

    public final int magic, concentration, temp;
    public final String channel, shortConds;
    public final boolean thrallStunned, thrallFrozen;
    public final String rightHand, leftHand;
    public final int packCount, packMax;
    public final String[] packItems;

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
        tar = new boolean[size];
        steam = new boolean[size];
        superheatedSteam = new boolean[size];
        oilMist = new boolean[size];
        lootKind = new byte[size];
        light = new float[size];
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                GameMap.Tile t = world.tile(new Vec2(tx, ty));
                int i = ty * w + tx;
                wall[i] = t.isWall();
                burning[i] = t.has(Tag.BURNING);
                oil[i] = t.has(Tag.OIL);
                List<Item> ground = t.ground();
                loot[i] = !ground.isEmpty();
                lootKind[i] = classifyLoot(ground);
                water[i] = t.has(Tag.WATER);
                ice[i] = t.has(Tag.ICE);
                acid[i] = t.has(Tag.ACID);
                tar[i] = t.has(Tag.TAR);
                steam[i] = t.has(Tag.STEAM);
                superheatedSteam[i] = t.has(Tag.SUPERHEATED_STEAM);
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
        rightHand = formatItem(thrall.inventory().equipped(EquipmentSlot.HAND_RIGHT));
        leftHand = formatItem(thrall.inventory().equipped(EquipmentSlot.HAND_LEFT));
        List<Item> pack = thrall.inventory().pack();
        packCount = pack.size();
        packMax = Inventory.PACK_MAX;
        packItems = new String[packCount];
        for (int i = 0; i < packCount; i++) packItems[i] = formatItem(pack.get(i));

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
        proneEntity = new boolean[n];
        wetEntity = new boolean[n];
        oiledEntity = new boolean[n];
        litEntity = new boolean[n];
        overwatchEntity = new boolean[n];
        put(0, thrall);
        for (int e = 0; e < entities.size(); e++) put(e + 1, entities.get(e));

        computeLighting();
    }

    private static String formatItem(Item item) {
        if (item == null) return "empty";
        if (item.has(Tag.LIT)) return item.name() + " [LIT]";
        if (item.has(Tag.BURNING)) return item.name() + " [BURN]";
        return item.name();
    }

    private static byte classifyLoot(List<Item> ground) {
        if (ground.isEmpty()) return LOOT_NONE;
        Item top = ground.get(0);
        if (top.substance() == Tag.OIL) return LOOT_FLASK_OIL;
        if (top.substance() == Tag.WATER) return LOOT_FLASK_WATER;
        if (top.substance() == Tag.ACID) return LOOT_FLASK_ACID;
        if (top.has(Tag.METAL) || top.maxDamage() >= 3) return LOOT_BLADE;
        return LOOT_STONE;
    }

    private void computeLighting() {
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                float lum = 0.34f;
                for (int eIdx = 0; eIdx < n; eIdx++) {
                    if (hp[eIdx] <= 0) continue;
                    boolean emit = litEntity[eIdx] || (eIdx == 0 && concentration > 0);
                    if (!emit) continue;
                    float power = litEntity[eIdx] ? 0.85f : 0.55f;
                    int dx = tx - x[eIdx], dy = ty - y[eIdx];
                    lum += power / (1f + 0.28f * (dx * dx + dy * dy));
                }
                for (int by = 0; by < h; by++) {
                    for (int bx = 0; bx < w; bx++) {
                        if (!burning[by * w + bx]) continue;
                        int dx = tx - bx, dy = ty - by;
                        lum += 0.75f / (1f + 0.32f * (dx * dx + dy * dy));
                    }
                }
                light[ty * w + tx] = Math.min(1.15f, Math.max(0.34f, lum));
            }
        }
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
        proneEntity[i] = e.has(Tag.PRONE);
        wetEntity[i] = e.has(Tag.WET);
        oiledEntity[i] = e.has(Tag.OIL);
        litEntity[i] = e.has(Tag.LIT) || e.has(Tag.BURNING) || hasLitEquipment(e);
        overwatchEntity[i] = e instanceof Actor a && a.readied() != null && !a.isReadiedSpent();
    }

    private static boolean hasLitEquipment(Entity e) {
        if (!(e instanceof Actor a)) return false;
        Item right = a.inventory().equipped(EquipmentSlot.HAND_RIGHT);
        if (right != null && (right.has(Tag.LIT) || right.has(Tag.BURNING))) return true;
        Item left = a.inventory().equipped(EquipmentSlot.HAND_LEFT);
        return left != null && (left.has(Tag.LIT) || left.has(Tag.BURNING));
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
