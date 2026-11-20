package com.archon.system.physics;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
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
            else if (tile.has(Tag.BURNING) && (entity.has(Tag.FLAMMABLE) || entity.has(Tag.OIL) || entity.has(Tag.TAR))) {
                boolean wasLitOrBurning = entity.has(Tag.BURNING) || entity.has(Tag.LIT);
                ThermalState.igniteEntity(entity);
                if (!wasLitOrBurning) ThermalState.addLog(log, entity.name() + " catches fire from the burning floor!");
            }
            if (tile.has(Tag.SUPERHEATED_STEAM) && entity.alive()) entity.temp += Balance.SUPERHEATED_STEAM_TEMP_DELTA;
            return false;
        }

        if (tile.has(Tag.BURNING)) {
            ThermalState.applyBurningFloorContact(world, entity, dest, log);
        }
        if (tile.has(Tag.WATER) && entity.alive()) {
            EntityReactions.applyWaterToEntity(world, entity, null, false, log);
            if (!entity.alive()) return false;
        }
        if (tile.has(Tag.ACID) && entity.alive() && !entity.has(Tag.FROZEN)) {
            ThermalState.applyAcidHazard(world, entity, Balance.CAUSTIC_DMG, log);
            if (!entity.alive()) return false;
        }
        if (tile.has(Tag.TAR) && entity.alive() && !entity.has(Tag.FROZEN)) {
            ThermalState.applyTarHazard(world, entity, log);
            if (!entity.alive()) return false;
        }
        if (tile.has(Tag.OIL) && !tile.has(Tag.BURNING) && entity.alive()) {
            if (entity.has(Tag.BURNING)) ThermalState.igniteTile(world, dest);
            EntityReactions.applyOilToEntity(world, entity, log);
        }
        if (SpatialService.isOpaqueGas(tile) && entity.alive()) {
            boolean wasBlind = entity.has(Tag.BLIND);
            entity.applyTag(Tag.BLIND);
            entity.blindLingerTurns = Balance.BLIND_LINGER_TURNS;
            if (entity instanceof Actor a) a.clearReadied();
            if (!wasBlind) {
                Tag gas = ThermalState.gasTag(tile);
                ThermalState.addLog(log, entity.name() + " is blinded by " + ThermalState.payloadLabel(gas).replace('_', ' ') + "!");
            }
        } else {
            if (tile.has(Tag.SUPERHEATED_STEAM) && entity.alive()) {
                ThermalState.applyCreatureTempDelta(world, entity, Balance.SUPERHEATED_STEAM_TEMP_DELTA, false, log);
                if (!entity.alive()) return false;
                ThermalState.addLog(log, entity.name() + " heats up in superheated steam (" + entity.temp + "C).");
            }
            if (entity.has(Tag.BLIND) && entity.blindLingerTurns > 0) {
                entity.blindLingerTurns = Balance.BLIND_LINGER_TURNS + 1;
            }
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
            if ("gas".equals(layer) || "steam".equals(layer) || "superheated_steam".equals(layer) || "oil_mist".equals(layer)) return;
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
            if (movingGas != null) {
                if (hasGasCollision(nextTile, movingGas)) break;
            } else if (hasLiquidCollision(nextTile, hasWater, hasOil, hasAcid)) {
                break;
            }
            if (i < steps - 1) {
                Vec2 lookPos = cur.plus(dir);
                GameMap.Tile lookahead = world.tile(lookPos);
                if (lookahead != null && !lookahead.isWall() && !lookPos.equals(stopBefore)) {
                    if (movingGas != null) exposeCreatureToGas(world, cur, movingGas, log);
                    else exposeCreatureToLiquid(world, cur, hasWater, hasOil, hasAcid, false, wasBurning, log);
                }
            }
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
        if (SpatialService.hasGas(tile) || tile.has(Tag.BURNING) || tile.has(Tag.ICE) || tile.has(Tag.TAR)) return true;
        if (hasWater && (tile.has(Tag.OIL) || tile.has(Tag.ACID))) return true;
        if (hasOil && (tile.has(Tag.WATER) || tile.has(Tag.ACID))) return true;
        return hasAcid && (tile.has(Tag.WATER) || tile.has(Tag.OIL));
    }

    private static void depositOrReactGas(World world, Vec2 pos, Tag gas, int savedGasTemp, List<String> log) {
        GameMap.Tile tile = world.tile(pos);
        if (tile == null || tile.isWall()) return;
        if (tile.has(Tag.BURNING)) {
            if (gas == Tag.OIL_MIST) { ThermalState.spawnGas(world, pos, Tag.OIL_MIST); TileReactions.detonateOilMist(world, null, pos, false, null, log); return; }
            if (gas == Tag.STEAM || gas == Tag.SUPERHEATED_STEAM) {
                ThermalState.spawnSuperheatedSteam(world, pos, false);
                if (gas == Tag.SUPERHEATED_STEAM) tile.gasTemp = Math.max(tile.gasTemp, savedGasTemp);
                exposeCreatureToGas(world, pos, Tag.SUPERHEATED_STEAM, log);
                return;
            }
        }
        Tag existing = ThermalState.gasTag(tile);
        if (existing != null && existing != gas) {
            ThermalState.clearGas(world, pos);
            if ((existing == Tag.OIL_MIST && (gas == Tag.STEAM || gas == Tag.SUPERHEATED_STEAM))
                    || (gas == Tag.OIL_MIST && (existing == Tag.STEAM || existing == Tag.SUPERHEATED_STEAM))) {
                exposeCreatureToGas(world, pos, Tag.OIL_MIST, log);
                ThermalState.spawnGas(world, pos, Tag.OIL_MIST);
            } else if ((existing == Tag.STEAM && gas == Tag.SUPERHEATED_STEAM)
                    || (existing == Tag.SUPERHEATED_STEAM && gas == Tag.STEAM)) {
                ThermalState.spawnSuperheatedSteam(world, pos, false);
                tile.gasTemp = Math.max(tile.gasTemp, savedGasTemp);
                exposeCreatureToGas(world, pos, Tag.SUPERHEATED_STEAM, log);
            } else {
                ThermalState.setWaterPuddle(world, pos);
            }
            return;
        }
        if (gas == Tag.STEAM) {
            if (tile.has(Tag.OIL)) { exposeCreatureToGas(world, pos, Tag.OIL_MIST, log); ThermalState.spawnGas(world, pos, Tag.OIL_MIST); return; }
            if (tile.has(Tag.ACID)) { ThermalState.setAcidPool(world, pos); applyAcidIfOccupied(world, pos, log); return; }
            if (tile.has(Tag.WATER)) { ThermalState.setWaterPuddle(world, pos); return; }
        } else if (gas == Tag.SUPERHEATED_STEAM) {
            if (tile.has(Tag.OIL)) { exposeCreatureToGas(world, pos, Tag.OIL_MIST, log); ThermalState.spawnGas(world, pos, Tag.OIL_MIST); return; }
            if (tile.has(Tag.ACID)) {
                world.map().removeTag(pos, Tag.ACID);
                world.map().removeTag(pos, Tag.LIQUID);
                ThermalState.addLog(log, pos + " Acid evaporates in the superheated steam!");
            }
            if (tile.has(Tag.WATER)) { ThermalState.setWaterPuddle(world, pos); return; }
        } else if (gas == Tag.OIL_MIST) {
            if (tile.has(Tag.ACID)) { ThermalState.setTar(world, pos); applyTarIfOccupied(world, pos, log); return; }
            if (tile.has(Tag.WATER)) { ThermalState.setOilSlick(world, pos); return; }
        }
        exposeCreatureToGas(world, pos, gas, log);
        ThermalState.spawnGas(world, pos, gas);
        tile.gasTemp = savedGasTemp;
    }

    private static void exposeCreatureToGas(World world, Vec2 pos, Tag gas, List<String> log) {
        Entity occ = SpatialService.entityAt(world, pos);
        if (occ == null || occ.kind() != Entity.Kind.CREATURE || !occ.alive()) return;
        if (gas == Tag.SUPERHEATED_STEAM) {
            ThermalState.applyCreatureTempDelta(world, occ, Balance.SUPERHEATED_STEAM_TEMP_DELTA, false, log);
            if (occ.alive()) {
                ThermalState.addLog(log, occ.name() + " heats up in superheated steam (" + occ.temp + "C).");
            }
            return;
        }
        boolean wasBlind = occ.has(Tag.BLIND);
        occ.applyTag(Tag.BLIND);
        occ.blindLingerTurns = Balance.BLIND_LINGER_TURNS;
        if (occ instanceof Actor a) a.clearReadied();
        if (!wasBlind) {
            ThermalState.addLog(log, occ.name() + " is blinded by " + ThermalState.payloadLabel(gas).replace('_', ' ') + "!");
        }
    }

    private static void applyAcidIfOccupied(World world, Vec2 pos, List<String> log) {
        Entity occ = SpatialService.entityAt(world, pos);
        if (occ != null && occ.kind() == Entity.Kind.CREATURE) {
            ThermalState.applyAcidHazard(world, occ, Balance.CAUSTIC_DMG, log);
        }
    }

    private static void applyTarIfOccupied(World world, Vec2 pos, List<String> log) {
        Entity occ = SpatialService.entityAt(world, pos);
        if (occ != null && occ.kind() == Entity.Kind.CREATURE) {
            ThermalState.applyTarHazard(world, occ, log);
        }
    }

    private static void depositOrReactLiquid(
            World world, Vec2 pos, boolean hasWater, boolean hasOil, boolean hasAcid, boolean wasBurning, List<String> log) {
        GameMap.Tile tile = world.tile(pos);
        if (tile == null || tile.isWall()) return;

        boolean reactive = SpatialService.hasGas(tile) || tile.has(Tag.ICE) || tile.has(Tag.WATER)
                || tile.has(Tag.BURNING) || tile.has(Tag.OIL) || tile.has(Tag.ACID) || tile.has(Tag.TAR);
        if (reactive || (hasWater && hasLitGroundItem(tile))) {
            Tag payload = hasAcid ? Tag.ACID : hasOil ? Tag.OIL : Tag.WATER;
            TileReactions.applyPayloadToTile(world, null, pos, payload, false, false, null, log);
            if (hasOil && wasBurning && (tile.has(Tag.OIL) || tile.has(Tag.TAR)) && !tile.has(Tag.ICE)) {
                ThermalState.igniteTile(world, pos);
            }
        } else if (hasAcid) {
            ThermalState.setAcidPool(world, pos);
        } else if (hasOil) {
            ThermalState.setOilSlick(world, pos);
            if (wasBurning) { ThermalState.igniteTile(world, pos); TileReactions.igniteGroundItems(tile, log); }
        } else if (hasWater) {
            ThermalState.setWaterPuddle(world, pos);
        }
        exposeCreatureToLiquid(world, pos, tile.has(Tag.WATER), tile.has(Tag.OIL), tile.has(Tag.ACID), tile.has(Tag.TAR), tile.has(Tag.BURNING), log);
    }

    private static void exposeCreatureToLiquid(
            World world, Vec2 pos, boolean water, boolean oil, boolean acid, boolean tar, boolean burning, List<String> log) {
        Entity occ = SpatialService.entityAt(world, pos);
        if (occ == null || !occ.alive()) return;
        if (water) {
            EntityReactions.applyWaterToEntity(world, occ, null, false, log);
            if (!occ.alive()) return;
        }
        if (acid && occ.kind() == Entity.Kind.CREATURE) {
            ThermalState.applyAcidHazard(world, occ, Balance.CAUSTIC_DMG, log);
            if (!occ.alive()) return;
        }
        if (tar && occ.kind() == Entity.Kind.CREATURE) {
            ThermalState.applyTarHazard(world, occ, log);
            if (!occ.alive()) return;
        }
        if (oil) EntityReactions.applyOilToEntity(world, occ, log);
        if (burning && !occ.has(Tag.FROZEN) && !occ.has(Tag.WET)
                && (occ.kind() == Entity.Kind.CREATURE || occ.has(Tag.FLAMMABLE) || occ.has(Tag.OIL) || occ.has(Tag.TAR))) {
            ThermalState.igniteEntity(occ);
        }
    }

    private static boolean hasLitGroundItem(GameMap.Tile tile) {
        List<Item> ground = tile.ground();
        for (int i = 0; i < ground.size(); i++) if (ground.get(i).has(Tag.LIT)) return true;
        return false;
    }

    private static boolean isOpenFloor(World world, Vec2 pos) { GameMap.Tile t = world.tile(pos); return t != null && !t.isWall(); }
}
