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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Gas/floor reactions, Fuel-Air BFS detonation, and LIT torch tile sparks. */
public final class TileReactions {

    private TileReactions() {}

    public static void applyPayloadToTile(
            World world, Actor caster, Vec2 pos, Tag payload, boolean isBlast,
            Set<Entity> burstHitEntities, List<String> log) {
        applyPayloadToTile(world, caster, pos, payload, isBlast, true, burstHitEntities, log);
    }

    public static void applyPayloadToTile(
            World world, Actor caster, Vec2 pos, Tag payload, boolean isBlast, boolean fracturesIce,
            Set<Entity> burstHitEntities, List<String> log) {
        GameMap.Tile tile = world.tile(pos);
        if (tile == null || tile.isWall()) return;

        GameMap map = world.map();
        Entity occ = SpatialService.entityAt(world, pos);

        if (payload == Tag.WATER) {
            boolean dousedLit = false;
            List<Item> ground = tile.ground();
            for (int i = 0; i < ground.size(); i++) {
                Item item = ground.get(i);
                if (item.has(Tag.LIT)) {
                    item.tags().remove(Tag.LIT);
                    ThermalState.spawnGas(world, pos, Tag.STEAM);
                    ThermalState.addLog(log, pos + " " + item.name() + " is doused in steam!");
                    dousedLit = true;
                }
            }
            if (dousedLit) return;
        }

        if (payload != Tag.FORCE && SpatialService.hasGas(tile)) {
            resolveGasHit(world, caster, pos, tile, payload, isBlast, burstHitEntities, occ, log);
            return;
        }
        if (tile.has(Tag.ICE)) { resolveIceHit(world, pos, map, tile, payload, fracturesIce, log); return; }
        if (tile.has(Tag.WATER)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                map.removeTag(pos, Tag.WATER); map.removeTag(pos, Tag.LIQUID); map.removeTag(pos, Tag.CONDUCTIVE);
                tile.temp = Balance.AMBIENT_TEMP;
                ThermalState.spawnGas(world, pos, Tag.STEAM);
                ThermalState.addLog(log, pos + " Water vaporizes into steam!");
                if (occ != null && occ.alive() && (burstHitEntities == null || burstHitEntities.add(occ))) {
                    ThermalState.addLog(log, occ.name() + " suffers " + Balance.SCALD_FLASH_DMG + " scald flash dmg!");
                    ThermalState.damageEntity(world, occ, Balance.SCALD_FLASH_DMG, false, log);
                }
            } else if (payload == Tag.OIL) { ThermalState.setOilSlick(world, pos); ThermalState.addLog(log, pos + " Oil spreads over water."); }
            else if (payload == Tag.ACID) { ThermalState.addLog(log, pos + " Acid is diluted in the water."); }
            return;
        }
        if (tile.has(Tag.BURNING)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) { ThermalState.igniteTile(world, pos); igniteGroundItems(tile, log); }
            else if (payload == Tag.WATER) {
                if (tile.has(Tag.TAR)) {
                    ThermalState.clearTar(world, pos);
                    ThermalState.addLog(log, pos + " Water washes the burning tar clean.");
                } else if (tile.has(Tag.OIL)) {
                    map.removeTag(pos, Tag.BURNING); map.removeTag(pos, Tag.OIL); map.removeTag(pos, Tag.FLAMMABLE); map.removeTag(pos, Tag.LIQUID);
                    tile.oilTurns = 0; tile.burnTurns = 0; tile.temp = Balance.AMBIENT_TEMP;
                    ThermalState.spawnGas(world, pos, Tag.STEAM);
                    ThermalState.addLog(log, pos + " Grease flare-up erupts into steam!");
                    if (occ != null && occ.alive() && (burstHitEntities == null || burstHitEntities.add(occ))) {
                        ThermalState.addLog(log, occ.name() + " takes " + Balance.SCALD_FLASH_DMG + " flare-up dmg!");
                        ThermalState.damageEntity(world, occ, Balance.SCALD_FLASH_DMG, false, log);
                    }
                } else {
                    map.removeTag(pos, Tag.BURNING); tile.burnTurns = 0;
                    ThermalState.setWaterPuddle(world, pos);
                    ThermalState.addLog(log, pos + " Flames are doused.");
                }
            } else if (payload == Tag.OIL && !tile.has(Tag.TAR)) { ThermalState.setOilSlick(world, pos); ThermalState.igniteTile(world, pos); }
            else if (payload == Tag.ACID) { ThermalState.addLog(log, pos + " Acid boils off the flames!"); }
            return;
        }
        if (tile.has(Tag.TAR)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                ThermalState.igniteTile(world, pos);
                ThermalState.addLog(log, pos + " Tar ignites in thick flames!");
                igniteGroundItems(tile, log);
            } else if (payload == Tag.WATER) {
                ThermalState.clearTar(world, pos);
                ThermalState.addLog(log, pos + " Water washes the tar clean.");
            }
            return;
        }
        if (tile.has(Tag.OIL)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) { ThermalState.igniteTile(world, pos); ThermalState.addLog(log, pos + " Oil slick ignites!"); igniteGroundItems(tile, log); }
            else if (payload == Tag.WATER) { ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Oil is rinsed into water."); }
            else if (payload == Tag.OIL) ThermalState.setOilSlick(world, pos);
            else if (payload == Tag.ACID) { ThermalState.setTar(world, pos); ThermalState.addLog(log, pos + " Oil and acid form tar."); }
            return;
        }
        if (tile.has(Tag.ACID)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                map.removeTag(pos, Tag.ACID); map.removeTag(pos, Tag.LIQUID);
                ThermalState.addLog(log, pos + " Acid evaporates in the heat!");
            } else if (payload == Tag.WATER) { ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Acid is diluted into water."); }
            else if (payload == Tag.OIL) { ThermalState.setTar(world, pos); ThermalState.addLog(log, pos + " Acid and oil form tar."); }
            else if (payload == Tag.ACID) ThermalState.setAcidPool(world, pos);
            return;
        }

        if (payload == Tag.WATER) { ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Water pools on the floor."); }
        else if (payload == Tag.OIL) { ThermalState.setOilSlick(world, pos); ThermalState.addLog(log, pos + " Oil slick coats the floor."); }
        else if (payload == Tag.ACID) { ThermalState.setAcidPool(world, pos); ThermalState.addLog(log, pos + " Acid pools on the floor."); }
        else if (payload == Tag.FIRE || payload == Tag.INFERNO) { igniteGroundItems(tile, log); }
    }

    private static void resolveGasHit(
            World world, Actor caster, Vec2 pos, GameMap.Tile tile, Tag payload,
            boolean isBlast, Set<Entity> burstHitEntities, Entity occ, List<String> log) {
        if (tile.has(Tag.STEAM)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                ThermalState.spawnSuperheatedSteam(world, pos, payload == Tag.INFERNO);
                ThermalState.addLog(log, pos + " Steam superheats!");
                if (occ != null && occ.alive() && (burstHitEntities == null || burstHitEntities.add(occ))) {
                    heatEntityFromSuperheatedSteam(world, occ, log);
                }
            } else if (payload == Tag.WATER) { ThermalState.clearGas(world, pos); ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Steam condenses into water."); }
            else if (payload == Tag.OIL) { ThermalState.spawnGas(world, pos, Tag.OIL_MIST); ThermalState.addLog(log, pos + " Steam turns into oil mist!"); }
            else if (payload == Tag.ACID) { ThermalState.clearGas(world, pos); ThermalState.applyAcidToFloorOnly(world, pos); ThermalState.addLog(log, pos + " Acid rain pools below!"); }
        } else if (tile.has(Tag.SUPERHEATED_STEAM)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                int targetTemp = payload == Tag.INFERNO ? Balance.SUPERHEATED_INFERNO_TEMP : Balance.SUPERHEATED_STEAM_TEMP;
                tile.gasTemp = Math.max(tile.gasTemp, targetTemp);
                ThermalState.addLog(log, pos + " Superheated steam surges!");
            } else if (payload == Tag.WATER) { ThermalState.clearGas(world, pos); ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Superheated steam condenses into water."); }
            else if (payload == Tag.OIL) { ThermalState.spawnGas(world, pos, Tag.OIL_MIST); ThermalState.addLog(log, pos + " Superheated steam turns into oil mist!"); }
            else if (payload == Tag.ACID) { ThermalState.addLog(log, pos + " Acid evaporates in the superheated steam!"); }
        } else if (tile.has(Tag.OIL_MIST)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) detonateOilMist(world, caster, pos, payload == Tag.INFERNO, burstHitEntities, log);
            else if (payload == Tag.WATER) { ThermalState.clearGas(world, pos); ThermalState.setOilSlick(world, pos); ThermalState.addLog(log, pos + " Oil mist precipitates into oil slick."); }
            else if (payload == Tag.OIL) { tile.gasTemp = Balance.BOIL_TEMP; ThermalState.setOilSlick(world, pos); ThermalState.addLog(log, pos + " Oil mist refreshes."); }
            else if (payload == Tag.ACID) { ThermalState.clearGas(world, pos); ThermalState.setTar(world, pos); ThermalState.addLog(log, pos + " Oil mist settles as tar."); }
        }
    }

    private static void heatEntityFromSuperheatedSteam(World world, Entity occ, List<String> log) {
        if (occ.kind() == Entity.Kind.CREATURE) {
            ThermalState.applyCreatureTempDelta(world, occ, Balance.SUPERHEATED_STEAM_TEMP_DELTA, false, log);
            if (occ.alive()) {
                ThermalState.addLog(log, occ.name() + " is scorched by superheated steam (" + occ.temp + "C).");
            }
        } else {
            occ.temp += Balance.SUPERHEATED_STEAM_TEMP_DELTA;
        }
    }

    private static void resolveIceHit(
            World world, Vec2 pos, GameMap map, GameMap.Tile tile, Tag payload, boolean fracturesIce, List<String> log) {
        if (payload == Tag.FORCE) {
            if (fracturesIce) {
                map.removeTag(pos, Tag.ICE);
                if (tile.has(Tag.OIL)) {
                    ThermalState.setOilSlick(world, pos);
                    ThermalState.addLog(log, pos + " Ice fractures under the oil.");
                } else if (tile.has(Tag.TAR)) {
                    ThermalState.addLog(log, pos + " Ice fractures under the tar.");
                } else {
                    boolean hadAcid = tile.has(Tag.ACID);
                    ThermalState.setWaterPuddle(world, pos);
                    ThermalState.addLog(log, pos + (hadAcid ? " Ice fractures and dilutes acid into water." : " Ice fractures into water."));
                }
            }
            return;
        }
        if (payload == Tag.FIRE || payload == Tag.INFERNO) {
            boolean oiledIce = tile.has(Tag.OIL);
            boolean tarIce = tile.has(Tag.TAR);
            boolean acidIce = tile.has(Tag.ACID);
            map.removeTag(pos, Tag.ICE);
            if (oiledIce || tarIce) {
                ThermalState.spawnGas(world, pos, Tag.STEAM);
                ThermalState.igniteTile(world, pos);
                igniteGroundItems(tile, log);
                ThermalState.addLog(log, pos + (tarIce ? " Ice sublimates and tar ignites!" : " Ice sublimates and oil ignites!"));
            } else if (acidIce) {
                map.removeTag(pos, Tag.ACID); map.removeTag(pos, Tag.LIQUID);
                tile.temp = Balance.AMBIENT_TEMP;
                if (payload == Tag.INFERNO) {
                    ThermalState.spawnGas(world, pos, Tag.STEAM);
                    ThermalState.addLog(log, pos + " Ice sublimates into steam and acid evaporates!");
                } else {
                    ThermalState.setWaterPuddle(world, pos);
                    ThermalState.addLog(log, pos + " Ice melts and dilutes acid into water.");
                }
            } else {
                tile.temp = Balance.AMBIENT_TEMP;
                if (payload == Tag.INFERNO) {
                    ThermalState.spawnGas(world, pos, Tag.STEAM);
                    ThermalState.addLog(log, pos + " Ice sublimates into steam!");
                } else {
                    ThermalState.setWaterPuddle(world, pos);
                    ThermalState.addLog(log, pos + " Ice melts into water.");
                }
            }
        } else if (payload == Tag.WATER) {
            map.removeTag(pos, Tag.OIL); map.removeTag(pos, Tag.FLAMMABLE); tile.oilTurns = 0;
            map.removeTag(pos, Tag.ACID); map.removeTag(pos, Tag.LIQUID);
            map.removeTag(pos, Tag.TAR);
        } else if (payload == Tag.OIL) {
            if (tile.has(Tag.ACID)) {
                map.removeTag(pos, Tag.ACID); map.removeTag(pos, Tag.LIQUID);
                map.addTag(pos, Tag.TAR);
                ThermalState.addLog(log, pos + " Oil and acid form tar on the ice.");
            } else if (!tile.has(Tag.TAR)) {
                map.addTag(pos, Tag.OIL); map.addTag(pos, Tag.FLAMMABLE); tile.oilTurns = Balance.OIL_TURNS;
                ThermalState.addLog(log, pos + " Oil coats the ice.");
            }
        } else if (payload == Tag.ACID) {
            if (tile.has(Tag.OIL)) {
                map.removeTag(pos, Tag.OIL); map.removeTag(pos, Tag.FLAMMABLE); tile.oilTurns = 0;
                map.addTag(pos, Tag.TAR);
                ThermalState.addLog(log, pos + " Acid and oil form tar on the ice.");
            } else if (!tile.has(Tag.TAR)) {
                map.addTag(pos, Tag.ACID); map.addTag(pos, Tag.LIQUID);
                ThermalState.addLog(log, pos + " Acid pools over the ice.");
            }
        }
    }

    public static void detonateOilMist(
            World world, Actor caster, Vec2 startPos, boolean inferno,
            Set<Entity> burstHitEntities, List<String> log) {
        GameMap.Tile startTile = world.tile(startPos);
        if (startTile == null || !startTile.has(Tag.OIL_MIST)) return;

        Set<Entity> hitSet = burstHitEntities != null ? burstHitEntities : new HashSet<>();
        ArrayDeque<Vec2> queue = new ArrayDeque<>();
        Set<Vec2> visited = new HashSet<>();
        queue.add(startPos);
        visited.add(startPos);
        int dmg = (inferno ? Balance.FUEL_AIR_DMG_INFERNO : Balance.FUEL_AIR_DMG_HEAT) + ThermalState.magicBonus(caster);

        while (!queue.isEmpty()) {
            Vec2 cur = queue.poll();
            ThermalState.clearGas(world, cur);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Vec2 nb = new Vec2(cur.x() + dx, cur.y() + dy);
                    GameMap.Tile nbTile = world.tile(nb);
                    if (nbTile == null || nbTile.isWall()) continue;
                    if (nbTile.has(Tag.OIL_MIST) && visited.add(nb)) queue.add(nb);

                    if ((nbTile.has(Tag.OIL) || nbTile.has(Tag.TAR)) && !nbTile.has(Tag.BURNING)) {
                        if (nbTile.has(Tag.ICE)) {
                            world.map().removeTag(nb, Tag.ICE);
                            ThermalState.spawnGas(world, nb, Tag.STEAM);
                        }
                        ThermalState.igniteTile(world, nb);
                        igniteGroundItems(nbTile, log);
                    } else if (nbTile.has(Tag.WATER)) {
                        world.map().removeTag(nb, Tag.WATER); world.map().removeTag(nb, Tag.LIQUID); world.map().removeTag(nb, Tag.CONDUCTIVE);
                        ThermalState.spawnGas(world, nb, Tag.STEAM);
                    } else {
                        if (nbTile.has(Tag.ICE)) {
                            world.map().removeTag(nb, Tag.ICE);
                            world.map().removeTag(nb, Tag.ACID); world.map().removeTag(nb, Tag.LIQUID);
                            ThermalState.spawnGas(world, nb, Tag.STEAM);
                        }
                        igniteGroundItems(nbTile, log);
                    }

                    Entity occ = SpatialService.entityAt(world, nb);
                    if (occ != null && occ.alive() && hitSet.add(occ)) {
                        ThermalState.addLog(log, "Fuel-air blast hits " + occ.name() + " for " + dmg + " dmg!");
                        ThermalState.damageEntity(world, occ, dmg, false, log);
                        if (occ.alive()) {
                            if (occ.has(Tag.WET)) {
                                occ.removeTag(Tag.WET); occ.wetTurns = 0;
                                occ.temp = Math.min(occ.temp + (inferno ? Balance.TD_INFERNO : Balance.TD_HEAT), Balance.BOIL_TEMP);
                                ThermalState.spawnGas(world, nb, Tag.STEAM);
                                ThermalState.syncThermalState(world, occ, log);
                            } else if ((occ.kind() == Entity.Kind.CREATURE || occ.has(Tag.FLAMMABLE) || occ.has(Tag.OIL))
                                    && !occ.has(Tag.FROZEN)) {
                                ThermalState.igniteEntity(occ);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void applySparkToTile(
            World world, Actor caster, Item torch, Vec2 pos, Set<Entity> burstHitEntities, List<String> log) {
        GameMap.Tile tile = world.tile(pos);
        if (tile == null || tile.isWall()) return;
        if (tile.has(Tag.STEAM) || tile.has(Tag.WATER)) { if (torch != null) torch.tags().remove(Tag.LIT); return; }
        if (tile.has(Tag.OIL_MIST)) { detonateOilMist(world, caster, pos, false, burstHitEntities, log); return; }
        if (tile.has(Tag.OIL) || tile.has(Tag.TAR)) {
            if (tile.has(Tag.ICE)) {
                world.map().removeTag(pos, Tag.ICE);
                ThermalState.spawnGas(world, pos, Tag.STEAM);
            }
            ThermalState.igniteTile(world, pos);
        }
        igniteGroundItems(tile, log);
    }

    public static boolean igniteGroundItems(GameMap.Tile tile, List<String> log) {
        boolean any = false;
        List<Item> ground = tile.ground();
        for (int i = 0; i < ground.size(); i++) {
            Item item = ground.get(i);
            if (!item.has(Tag.LIT) && (item.has(Tag.FLAMMABLE) || item.has(Tag.OIL))) {
                item.tags().add(Tag.LIT);
                ThermalState.addLog(log, item.name() + " catches fire!");
                any = true;
            }
        }
        return any;
    }

    public static void spillSubstance(World world, Vec2 pos, Tag substance, List<String> log) {
        spillSubstance(world, pos, substance, false, log);
    }

    public static void spillSubstance(World world, Vec2 pos, Tag substance, boolean floorOnly, List<String> log) {
        Entity occupant = floorOnly ? null : SpatialService.entityAt(world, pos);
        if (occupant != null && occupant.alive()) {
            int idx = log != null ? log.size() : 0;
            EntityReactions.Hit hit = EntityReactions.applyPayloadToEntity(
                    world, null, occupant, substance, false, false, 1.0, false, null, log);
            if (hit.damage() > 0 && log != null) {
                log.add(idx, occupant.name() + " takes " + hit.damage() + " dmg.");
            }
            return;
        }
        applyPayloadToTile(world, null, pos, substance, false, false, null, log);
    }
}
