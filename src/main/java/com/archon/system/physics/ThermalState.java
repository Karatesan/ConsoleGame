package com.archon.system.physics;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.CombatNarrative;
import com.archon.system.environment.Destruction;
import com.archon.system.spatial.SpatialService;

import java.util.List;
import java.util.Locale;

/** Tab 5 thermal/state transitions, two-layer tile helpers, damage, and Tab 4B concentration. */
public final class ThermalState {

    private static final Tag[] GASES = {Tag.STEAM, Tag.CAUSTIC_GAS, Tag.OIL_MIST};
    private static final Tag[] WATER_CLEAR = {Tag.ICE, Tag.OIL, Tag.ACID, Tag.FLAMMABLE, Tag.BURNING};
    private static final Tag[] WATER_ADD = {Tag.WATER, Tag.LIQUID, Tag.CONDUCTIVE};
    private static final Tag[] WATER_LIQ_CLEAR = {Tag.WATER, Tag.CONDUCTIVE};
    private static final Tag[] OIL_ADD = {Tag.OIL, Tag.LIQUID, Tag.FLAMMABLE};
    private static final Tag[] ACID_ADD = {Tag.ACID, Tag.LIQUID};

    private ThermalState() {}

    public static void applyCreatureTempDelta(
            World world, Entity entity, int delta, boolean ignoreThermalMass, List<String> log) {
        if (entity.kind() != Entity.Kind.CREATURE || !entity.alive()) return;
        int scaled = ignoreThermalMass ? delta : (int) Math.floor(delta / entity.thermalMass);
        if (scaled < 0 && entity.has(Tag.FROZEN)) scaled = (int) Math.floor(scaled * Balance.FROZEN_DELTA_MULT);
        entity.temp = Math.max(Balance.ABS_ZERO, entity.temp + scaled);
        syncThermalState(world, entity, log);
    }

    public static void syncThermalState(World world, Entity entity, List<String> log) {
        if (entity.kind() != Entity.Kind.CREATURE || !entity.alive()) return;
        if (entity.has(Tag.WET) && entity.temp <= Balance.FREEZE_TEMP) {
            entity.applyTag(Tag.FROZEN);
            entity.applyTag(Tag.STUNNED);
            entity.removeTag(Tag.CHILLED);
            entity.removeTag(Tag.WET);
            entity.wetTurns = 0;
            clearActiveDefense(entity);
            addLog(log, entity.name() + " is frozen solid!");
        }
        if (entity.has(Tag.FROZEN) && entity.temp <= Balance.DEEP_FREEZE_TEMP && !entity.deepFreezeTriggered) {
            entity.deepFreezeTriggered = true;
            shredArmor(entity, Balance.CRYO_ARMOR_SHRED);
            addLog(log, entity.name() + " suffers deep cryo fracture (" + Balance.CRYO_DMG + " dmg, -" + Balance.CRYO_ARMOR_SHRED + " armor).");
            damageEntity(world, entity, Balance.CRYO_DMG, false, log);
            if (!entity.alive()) return;
        }
        if (entity.has(Tag.FROZEN)) {
            entity.removeTag(Tag.CHILLED);
        } else if (entity.temp <= Balance.CHILLED_MAX_TEMP) {
            boolean wasChilled = entity.has(Tag.CHILLED);
            entity.applyTag(Tag.CHILLED);
            if (!wasChilled) {
                addLog(log, entity.name() + " is chilled (" + entity.temp + "C).");
            }
        } else {
            entity.removeTag(Tag.CHILLED);
        }

        if (entity.has(Tag.BLEEDING) && (entity.temp <= Balance.FREEZE_TEMP || entity.temp >= Balance.BOIL_TEMP)) clearBleeding(entity);
        if (!entity.has(Tag.WET) && !entity.has(Tag.FROZEN) && entity.temp >= Balance.IGNITE_TEMP && !entity.has(Tag.BURNING)) {
            igniteEntity(entity);
            addLog(log, entity.name() + " bursts into flames!");
        }
    }

    public static void exitFrozen(Entity entity) {
        entity.removeTag(Tag.FROZEN);
        entity.removeTag(Tag.STUNNED);
        entity.removeTag(Tag.CHILLED);
        entity.removeTag(Tag.WET);
        entity.wetTurns = 0;
        entity.stunTurns = 0;
        entity.deepFreezeTriggered = false;
        entity.temp = Balance.THAW_RESET_TEMP;
    }

    public static void igniteEntity(Entity entity) {
        if (entity.kind() != Entity.Kind.CREATURE && entity.has(Tag.METAL) && entity.has(Tag.FLAMMABLE)) {
            entity.applyTag(Tag.LIT);
            return;
        }
        entity.applyTag(Tag.BURNING);
        entity.temp = Math.max(entity.temp, Balance.BURN_CAP);
        if (entity.has(Tag.OIL)) entity.oilTurns = Balance.OIL_TURNS;
        else if (entity.kind() != Entity.Kind.CREATURE) entity.propBurnTurns = Balance.PROP_BURN_TURNS;
    }

    public static void clearBleeding(Entity entity) {
        entity.removeTag(Tag.BLEEDING);
        entity.removeTag(Tag.BLOOD);
        entity.bleedTurns = 0;
    }

    public static void applyProne(Entity entity) {
        entity.applyTag(Tag.PRONE);
        clearActiveDefense(entity);
    }

    public static void clearActiveDefense(Entity entity) {
        if (entity instanceof Actor a) { a.clearReadied(); a.setGuarded(false); }
    }

    public static void shredArmor(Entity entity, int amount) { entity.setArmor(Math.max(0, entity.armor() - amount)); }

    public static boolean isLightOrMedium(Entity entity) {
        if (entity instanceof Actor a) return (a.strength() / 2) <= Balance.RES_MEDIUM;
        return entity.kind() == Entity.Kind.PROP && !entity.has(Tag.STONE) && !entity.has(Tag.METAL);
    }

    public static int magicBonus(Actor caster) { return Math.floorDiv(caster != null ? caster.magic : Balance.BASE_MAGIC, 2); }
    public static void addLog(List<String> log, String msg) { if (log != null) log.add(msg); }

    // --- Two-Layer Tile Helpers (5A / 5C) ---

    public static void spawnGas(World world, Vec2 pos, Tag gasTag) {
        GameMap map = world.map();
        GameMap.Tile tile = map.tile(pos);
        if (tile == null || tile.isWall()) return;
        swapTags(map, pos, GASES, null);
        map.addTag(pos, gasTag);
        tile.gasTemp = Balance.BOIL_TEMP;
        Entity occ = SpatialService.entityAt(world, pos);
        if (occ != null && occ.kind() == Entity.Kind.CREATURE && occ.alive()) {
            occ.applyTag(Tag.BLIND);
            occ.blindLingerTurns = Balance.BLIND_LINGER_TURNS;
            if (occ instanceof Actor a) a.clearReadied();
        }
    }

    public static void clearGas(World world, Vec2 pos) {
        swapTags(world.map(), pos, GASES, null);
        world.tile(pos).gasTemp = Balance.AMBIENT_TEMP;
    }

    public static Tag gasTag(GameMap.Tile tile) {
        for (int i = 0; i < GASES.length; i++) if (tile.has(GASES[i])) return GASES[i];
        return null;
    }

    public static void setWaterPuddle(World world, Vec2 pos) {
        GameMap.Tile tile = world.tile(pos);
        swapTags(world.map(), pos, WATER_CLEAR, WATER_ADD);
        tile.oilTurns = 0;
        tile.burnTurns = 0;
        tile.temp = Balance.AMBIENT_TEMP;
    }

    public static void setOilSlick(World world, Vec2 pos) {
        swapTags(world.map(), pos, WATER_LIQ_CLEAR, OIL_ADD);
        world.tile(pos).oilTurns = Balance.OIL_TURNS;
    }

    public static void setAcidPool(World world, Vec2 pos) { swapTags(world.map(), pos, WATER_LIQ_CLEAR, ACID_ADD); }
    public static void setToxicTar(World world, Vec2 pos) { setAcidPool(world, pos); setOilSlick(world, pos); }

    public static void applyAcidToFloorOnly(World world, Vec2 pos) {
        GameMap.Tile tile = world.tile(pos);
        if (tile.has(Tag.ICE)) swapTags(world.map(), pos, null, ACID_ADD);
        else if (tile.has(Tag.OIL)) setToxicTar(world, pos);
        else setAcidPool(world, pos);
    }

    public static void igniteTile(World world, Vec2 pos) {
        GameMap.Tile tile = world.tile(pos);
        world.map().addTag(pos, Tag.BURNING);
        tile.temp = Math.max(tile.temp, Balance.BURN_CAP);
        if (tile.has(Tag.OIL)) tile.oilTurns = Balance.OIL_TURNS;
        else tile.burnTurns = Balance.PROP_BURN_TURNS;
    }

    private static void swapTags(GameMap map, Vec2 pos, Tag[] clear, Tag[] add) {
        if (clear != null) for (int i = 0; i < clear.length; i++) map.removeTag(pos, clear[i]);
        if (add != null) for (int i = 0; i < add.length; i++) map.addTag(pos, add[i]);
    }

    // --- Damage, Concentration & Channel Backfire (4B) ---

    public static void damageEntity(World world, Entity victim, int dmg, boolean intentionalSelfLoss, List<String> log) {
        if (dmg <= 0 || !victim.alive()) return;
        victim.takeDamage(dmg);
        if (!victim.alive()) {
            addLog(log, victim.name() + (victim.kind() == Entity.Kind.CREATURE ? " falls." : " is destroyed."));
            Destruction.Result res = Destruction.onKilled(world, victim);
            if (log != null) log.addAll(res.messages());
            return;
        }
        onCreatureDamaged(world, victim, dmg, intentionalSelfLoss, log);
    }

    public static void onCreatureDamaged(World world, Entity victim, int dmg, boolean intentionalSelfLoss, List<String> log) {
        Actor thrall = world.thrall();
        if (thrall == null || victim != thrall || !thrall.alive() || thrall.channeledPayload == null || intentionalSelfLoss || dmg <= 0) return;
        if (!world.dice().chance(Math.clamp(Balance.CONC_BASE - dmg * Balance.CONC_PER_DMG, 0, 100))) {
            triggerChannelBackfire(world, thrall, log);
        }
    }

    public static String payloadLabel(Tag payload) {
        return payload == Tag.FIRE ? "heat" : payload.name().toLowerCase(Locale.ROOT);
    }

    public static void triggerChannelBackfire(World world, Actor caster, List<String> log) {
        Tag payload = caster.channeledPayload;
        if (payload == null) return;
        caster.channeledPayload = null;
        caster.channelTurns = 0;
        String label = payloadLabel(payload);
        addLog(log, caster.name() + " loses concentration — " + label + " backfires!");
        if (payload == Tag.FORCE && caster.alive()) {
            damageEntity(world, caster, Balance.FORCE_COLLISION_DMG, true, log);
            if (caster.alive()) applyProne(caster);
        } else if (payload == Tag.FIRE || payload == Tag.INFERNO) {
            String source = Character.toUpperCase(label.charAt(0)) + label.substring(1) + " backfire";
            detonateThermalOnCaster(world, caster, payload, source, log);
        }
    }

    public static void detonateUnstableSiphon(World world, Actor caster, Tag payload, List<String> log) {
        if (caster == null || !caster.alive() || (payload != Tag.FIRE && payload != Tag.INFERNO)) return;
        String label = payloadLabel(payload);
        addLog(log, "Uncontained " + label + " goes unstable and blasts " + caster.name() + "!");
        detonateThermalOnCaster(world, caster, payload, "Unstable " + label, log);
    }

    private static void detonateThermalOnCaster(World world, Actor caster, Tag payload, String source, List<String> log) {
        TileReactions.applyPayloadToTile(world, caster, caster.pos(), payload, false, false, null, log);
        if (!caster.alive()) return;
        int hitLogIdx = log != null ? log.size() : 0;
        EntityReactions.Hit hit = EntityReactions.applyPayloadToEntity(
                world, caster, caster, payload, false, false, 1.0, false, null, log);
        if (log != null && hit.damage() > 0) {
            log.add(hitLogIdx, CombatNarrative.formatAutoHit(
                    source, caster.name(), hit.damage(), hit.armorBlocked(), hit.armorBypassed()));
        }
    }
}
