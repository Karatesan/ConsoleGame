package com.archon.system.physics;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

import java.util.List;

/** Tab 3B / 5C tile-entry reaction routing and gas/liquid Push/Pull displacement with wall splash. */
public final class TileDisplacement {

    private TileDisplacement() {}

    public static boolean onEnterTile(World world, Entity entity, Vec2 dest, boolean canSlip, List<String> log) {
        entity.moveTo(dest);
        GameMap.Tile tile = world.tile(dest);
        if (tile == null || !entity.alive()) return false;

        if (entity.kind() != Entity.Kind.CREATURE) {
            if (tile.has(Tag.WATER)) EntityReactions.applyWaterToEntity(world, entity, null, false, log);
            else if (tile.has(Tag.BURNING) && (entity.has(Tag.FLAMMABLE) || entity.has(Tag.OIL))) ThermalState.igniteEntity(entity);
            return false;
        }

        if (tile.has(Tag.BURNING)) {
            if (entity.has(Tag.FROZEN)) { ThermalState.exitFrozen(entity); ThermalState.spawnGas(world, dest, Tag.STEAM); }
            else if (entity.has(Tag.WET)) { entity.removeTag(Tag.WET); entity.wetTurns = 0; ThermalState.spawnGas(world, dest, Tag.STEAM); }
            else ThermalState.igniteEntity(entity);
        }
        if (tile.has(Tag.WATER) && entity.alive()) {
            EntityReactions.applyWaterToEntity(world, entity, null, false, log);
            if (!entity.alive()) return false;
        }
        if (tile.has(Tag.ACID) && entity.alive() && !entity.has(Tag.FROZEN)) {
            ThermalState.shredArmor(entity, Balance.ACID_ARMOR_SHRED);
            entity.applyTag(Tag.CORRODED);
            ThermalState.damageEntity(world, entity, Balance.CAUSTIC_DMG, false, log);
            if (!entity.alive()) return false;
        }
        if (tile.has(Tag.OIL) && !tile.has(Tag.BURNING) && entity.alive()) {
            if (entity.has(Tag.BURNING)) ThermalState.igniteTile(world, dest);
            EntityReactions.applyOilToEntity(world, entity, log);
        }
        if (SpatialService.hasGas(tile) && entity.alive()) {
            entity.applyTag(Tag.BLIND);
            entity.blindLingerTurns = Balance.BLIND_LINGER_TURNS;
            if (entity instanceof Actor a) a.clearReadied();
        } else if (entity.has(Tag.BLIND) && entity.blindLingerTurns > 0) {
            entity.blindLingerTurns = Balance.BLIND_LINGER_TURNS + 1;
        }

        boolean slippery = tile.has(Tag.ICE) || (tile.has(Tag.OIL) && !tile.has(Tag.BURNING));
        if (canSlip && slippery && entity.alive() && !entity.has(Tag.PRONE) && world.dice().chance(Balance.SLIP_CHANCE)) {
            ThermalState.applyProne(entity);
            ThermalState.addLog(log, entity.name() + " slips and falls prone!");
            return true;
        }
        return false;
    }

    public static void relocateGasOrLiquid(
            World world, Vec2 start, Vec2 dir, int steps, Vec2 stopBefore, List<String> log) {
        relocateGasOrLiquid(world, start, dir, steps, stopBefore, null, log);
    }

    public static void relocateGasOrLiquid(
            World world, Vec2 start, Vec2 dir, int steps, Vec2 stopBefore, String layer, List<String> log) {
        if (steps <= 0 || (dir.x() == 0 && dir.y() == 0)) return;
        GameMap.Tile startTile = world.tile(start);
        if (startTile == null) return;

        boolean floorOnly = "floor".equals(layer) || "water".equals(layer) || "oil".equals(layer) || "acid".equals(layer);
        Tag movingGas = floorOnly ? null : ThermalState.gasTag(startTile);
        int savedGasTemp = startTile.gasTemp;
        boolean hasWater = false, hasOil = false, hasAcid = false, wasBurning = false;

        if (movingGas != null) {
            ThermalState.clearGas(world, start);
        } else {
            if ("gas".equals(layer) || "steam".equals(layer) || "oil_mist".equals(layer) || "caustic".equals(layer)) return;
            hasWater = startTile.has(Tag.WATER); hasOil = startTile.has(Tag.OIL); hasAcid = startTile.has(Tag.ACID);
            if (!hasWater && !hasOil && !hasAcid) return;
            wasBurning = startTile.has(Tag.BURNING);
            GameMap map = world.map();
            map.removeTag(start, Tag.WATER); map.removeTag(start, Tag.OIL); map.removeTag(start, Tag.ACID);
            map.removeTag(start, Tag.LIQUID); map.removeTag(start, Tag.FLAMMABLE); map.removeTag(start, Tag.CONDUCTIVE);
            if (wasBurning) { map.removeTag(start, Tag.BURNING); startTile.burnTurns = 0; }
            startTile.oilTurns = 0;
        }

        Vec2 cur = start;
        for (int i = 0; i < steps; i++) {
            Vec2 next = cur.plus(dir);
            if (stopBefore != null && next.equals(stopBefore)) break;
            GameMap.Tile nextTile = world.tile(next);
            if (nextTile == null || nextTile.isWall()) {
                Vec2 p1 = new Vec2(cur.x() - dir.y(), cur.y() + dir.x());
                Vec2 p2 = new Vec2(cur.x() + dir.y(), cur.y() - dir.x());
                boolean open1 = isOpenFloor(world, p1), open2 = isOpenFloor(world, p2);
                if (!open1 && !open2) depositMoved(world, cur, movingGas, savedGasTemp, hasWater, hasOil, hasAcid, wasBurning, log);
                else {
                    if (open1) depositMoved(world, p1, movingGas, savedGasTemp, hasWater, hasOil, hasAcid, wasBurning, log);
                    if (open2) depositMoved(world, p2, movingGas, savedGasTemp, hasWater, hasOil, hasAcid, wasBurning, log);
                }
                return;
            }
            cur = next;
            if (movingGas != null ? hasGasCollision(nextTile, movingGas) : hasLiquidCollision(nextTile, hasWater, hasOil, hasAcid)) break;
        }
        depositMoved(world, cur, movingGas, savedGasTemp, hasWater, hasOil, hasAcid, wasBurning, log);
    }

    private static void depositMoved(
            World world, Vec2 pos, Tag gas, int gasTemp,
            boolean hasWater, boolean hasOil, boolean hasAcid, boolean wasBurning, List<String> log) {
        if (gas != null) depositOrReactGas(world, pos, gas, gasTemp, log);
        else depositOrReactLiquid(world, pos, hasWater, hasOil, hasAcid, wasBurning, log);
    }

    private static boolean hasGasCollision(GameMap.Tile tile, Tag gas) {
        Tag existing = ThermalState.gasTag(tile);
        return (existing != null && existing != gas) || tile.has(Tag.BURNING) || tile.has(Tag.WATER) || tile.has(Tag.OIL) || tile.has(Tag.ACID);
    }

    private static boolean hasLiquidCollision(GameMap.Tile tile, boolean hasWater, boolean hasOil, boolean hasAcid) {
        if (SpatialService.hasGas(tile) || tile.has(Tag.BURNING) || tile.has(Tag.ICE)) return true;
        if (hasWater && (tile.has(Tag.OIL) || tile.has(Tag.ACID))) return true;
        if (hasOil && (tile.has(Tag.WATER) || tile.has(Tag.ACID))) return true;
        return hasAcid && (tile.has(Tag.WATER) || tile.has(Tag.OIL));
    }

    private static void depositOrReactGas(World world, Vec2 pos, Tag gas, int savedGasTemp, List<String> log) {
        GameMap.Tile tile = world.tile(pos);
        if (tile == null || tile.isWall()) return;
        if (tile.has(Tag.BURNING)) {
            if (gas == Tag.OIL_MIST) { ThermalState.spawnGas(world, pos, Tag.OIL_MIST); TileReactions.detonateOilMist(world, null, pos, false, null, log); return; }
            if (gas == Tag.STEAM) { Entity occ = SpatialService.entityAt(world, pos); if (occ != null && occ.alive()) ThermalState.damageEntity(world, occ, Balance.SCALD_FLASH_DMG, false, log); return; }
        }
        Tag existing = ThermalState.gasTag(tile);
        if (existing != null && existing != gas) {
            ThermalState.clearGas(world, pos);
            if ((existing == Tag.OIL_MIST && gas == Tag.CAUSTIC_GAS) || (existing == Tag.CAUSTIC_GAS && gas == Tag.OIL_MIST)) ThermalState.setToxicTar(world, pos);
            else if ((existing == Tag.STEAM && gas == Tag.OIL_MIST) || (existing == Tag.OIL_MIST && gas == Tag.STEAM)) ThermalState.spawnGas(world, pos, Tag.OIL_MIST);
            else if ((existing == Tag.STEAM && gas == Tag.CAUSTIC_GAS) || (existing == Tag.CAUSTIC_GAS && gas == Tag.STEAM)) ThermalState.setAcidPool(world, pos);
            else ThermalState.setWaterPuddle(world, pos);
            return;
        }
        if (gas == Tag.STEAM) {
            if (tile.has(Tag.OIL)) { ThermalState.spawnGas(world, pos, Tag.OIL_MIST); return; }
            if (tile.has(Tag.ACID)) { ThermalState.setAcidPool(world, pos); return; }
        } else if (gas == Tag.CAUSTIC_GAS) {
            if (tile.has(Tag.OIL)) { ThermalState.setToxicTar(world, pos); return; }
            if (tile.has(Tag.WATER)) { ThermalState.setWaterPuddle(world, pos); return; }
        } else if (gas == Tag.OIL_MIST) {
            if (tile.has(Tag.ACID)) { ThermalState.setToxicTar(world, pos); return; }
            if (tile.has(Tag.WATER)) { ThermalState.setOilSlick(world, pos); return; }
        }
        ThermalState.spawnGas(world, pos, gas);
        tile.gasTemp = savedGasTemp;
    }

    private static void depositOrReactLiquid(
            World world, Vec2 pos, boolean hasWater, boolean hasOil, boolean hasAcid, boolean wasBurning, List<String> log) {
        if (hasAcid && hasOil) {
            TileReactions.applyPayloadToTile(world, null, pos, Tag.ACID, false, false, null, log);
            TileReactions.applyPayloadToTile(world, null, pos, Tag.OIL, false, false, null, log);
        } else if (hasAcid) TileReactions.applyPayloadToTile(world, null, pos, Tag.ACID, false, false, null, log);
        else if (hasOil) {
            TileReactions.applyPayloadToTile(world, null, pos, Tag.OIL, false, false, null, log);
            if (wasBurning && world.tile(pos).has(Tag.OIL)) ThermalState.igniteTile(world, pos);
        } else if (hasWater) TileReactions.applyPayloadToTile(world, null, pos, Tag.WATER, false, false, null, log);
    }

    private static boolean isOpenFloor(World world, Vec2 pos) { GameMap.Tile t = world.tile(pos); return t != null && !t.isWall(); }
}
