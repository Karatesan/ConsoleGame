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

/** Tab 6 gas/floor reactions (cols J-P, S6-S10), Fuel-Air BFS detonation, and LIT torch tile sparks. */
public final class TileReactions {

    private static final int[][] CARDINALS = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};

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
            else if (payload == Tag.ACID) { ThermalState.setAcidPool(world, pos); ThermalState.addLog(log, pos + " Water turns to acid."); }
            return;
        }
        if (tile.has(Tag.BURNING)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) { ThermalState.igniteTile(world, pos); igniteGroundItems(tile, log); }
            else if (payload == Tag.WATER) {
                if (tile.has(Tag.OIL)) {
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
            } else if (payload == Tag.OIL) { ThermalState.setOilSlick(world, pos); ThermalState.igniteTile(world, pos); }
            else if (payload == Tag.ACID) { ThermalState.spawnGas(world, pos, Tag.CAUSTIC_GAS); ThermalState.addLog(log, pos + " Acid boils into caustic gas!"); }
            return;
        }
        if (tile.has(Tag.ACID) && tile.has(Tag.OIL)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                map.removeTag(pos, Tag.ACID); ThermalState.spawnGas(world, pos, Tag.CAUSTIC_GAS); ThermalState.igniteTile(world, pos);
                ThermalState.addLog(log, pos + " Toxic tar boils into caustic gas and ignites!");
            } else if (payload == Tag.WATER) { ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Toxic tar is diluted into water."); }
            else if (payload == Tag.OIL || payload == Tag.ACID) ThermalState.setToxicTar(world, pos);
            return;
        }
        if (tile.has(Tag.OIL)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) { ThermalState.igniteTile(world, pos); ThermalState.addLog(log, pos + " Oil slick ignites!"); igniteGroundItems(tile, log); }
            else if (payload == Tag.WATER) { ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Oil is rinsed into water."); }
            else if (payload == Tag.OIL) ThermalState.setOilSlick(world, pos);
            else if (payload == Tag.ACID) { ThermalState.setToxicTar(world, pos); ThermalState.addLog(log, pos + " Oil and acid form toxic tar."); }
            return;
        }
        if (tile.has(Tag.ACID)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                map.removeTag(pos, Tag.ACID); map.removeTag(pos, Tag.LIQUID); ThermalState.spawnGas(world, pos, Tag.CAUSTIC_GAS);
                ThermalState.addLog(log, pos + " Acid boils into caustic gas!");
            } else if (payload == Tag.WATER) { ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Acid is diluted into water."); }
            else if (payload == Tag.OIL) { ThermalState.setToxicTar(world, pos); ThermalState.addLog(log, pos + " Acid and oil form toxic tar."); }
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
                ThermalState.clearGas(world, pos);
                ThermalState.addLog(log, pos + " Steam explodes!");
                if (occ != null && occ.alive() && (burstHitEntities == null || burstHitEntities.add(occ))) {
                    ThermalState.addLog(log, occ.name() + " takes " + Balance.SCALD_FLASH_DMG + " steam flash dmg!");
                    ThermalState.damageEntity(world, occ, Balance.SCALD_FLASH_DMG, false, log);
                }
            } else if (payload == Tag.WATER) { ThermalState.clearGas(world, pos); ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Steam condenses into water."); }
            else if (payload == Tag.OIL) { ThermalState.spawnGas(world, pos, Tag.OIL_MIST); ThermalState.addLog(log, pos + " Steam turns into oil mist!"); }
            else if (payload == Tag.ACID) { ThermalState.clearGas(world, pos); ThermalState.applyAcidToFloorOnly(world, pos); ThermalState.addLog(log, pos + " Acid rain pools below!"); }
        } else if (tile.has(Tag.CAUSTIC_GAS)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) {
                ThermalState.clearGas(world, pos);
                ThermalState.addLog(log, pos + " Caustic gas ruptures!");
                if (occ != null && occ.alive() && (burstHitEntities == null || burstHitEntities.add(occ))) {
                    ThermalState.shredArmor(occ, Balance.ACID_ARMOR_SHRED);
                    occ.applyTag(Tag.CORRODED);
                    int directFire = (payload == Tag.INFERNO ? Balance.DMG_INFERNO : Balance.DMG_HEAT) + ThermalState.magicBonus(caster);
                    int firePart = isBlast ? Math.max(1, (int) Math.floor(directFire * Balance.BLAST_DMG_MULT)) : directFire;
                    int total = firePart + Balance.RUPTURE_ACID_DMG;
                    ThermalState.addLog(log, occ.name() + " takes " + total + " rupture dmg (-" + Balance.ACID_ARMOR_SHRED + " armor -> " + occ.armor() + ")!");
                    ThermalState.damageEntity(world, occ, total, false, log);
                }
            } else if (payload == Tag.WATER) { ThermalState.clearGas(world, pos); ThermalState.setWaterPuddle(world, pos); ThermalState.addLog(log, pos + " Caustic gas dilutes into water."); }
            else if (payload == Tag.OIL) { ThermalState.clearGas(world, pos); ThermalState.setToxicTar(world, pos); ThermalState.addLog(log, pos + " Caustic gas drops as toxic tar."); }
            else if (payload == Tag.ACID) { tile.gasTemp = Balance.BOIL_TEMP; ThermalState.addLog(log, pos + " Caustic gas refreshes."); }
        } else if (tile.has(Tag.OIL_MIST)) {
            if (payload == Tag.FIRE || payload == Tag.INFERNO) detonateOilMist(world, caster, pos, payload == Tag.INFERNO, burstHitEntities, log);
            else if (payload == Tag.WATER) { ThermalState.clearGas(world, pos); ThermalState.setOilSlick(world, pos); ThermalState.addLog(log, pos + " Oil mist precipitates into oil slick."); }
            else if (payload == Tag.OIL) { tile.gasTemp = Balance.BOIL_TEMP; ThermalState.setOilSlick(world, pos); ThermalState.addLog(log, pos + " Oil mist refreshes."); }
            else if (payload == Tag.ACID) { ThermalState.clearGas(world, pos); ThermalState.setToxicTar(world, pos); ThermalState.addLog(log, pos + " Oil mist settles as toxic tar."); }
        }
    }

    private static void resolveIceHit(
            World world, Vec2 pos, GameMap map, GameMap.Tile tile, Tag payload, boolean fracturesIce, List<String> log) {
        if (payload == Tag.FORCE) {
            if (fracturesIce) {
                ThermalState.setWaterPuddle(world, pos);
                ThermalState.addLog(log, pos + " Ice fractures into water.");
            }
            return;
        }
        if (payload == Tag.FIRE || payload == Tag.INFERNO) {
            boolean oiledIce = tile.has(Tag.OIL);
            map.removeTag(pos, Tag.ICE); map.removeTag(pos, Tag.OIL); map.removeTag(pos, Tag.FLAMMABLE); map.removeTag(pos, Tag.LIQUID);
            tile.oilTurns = 0; tile.temp = Balance.AMBIENT_TEMP;
            if (payload == Tag.INFERNO || oiledIce) {
                ThermalState.spawnGas(world, pos, Tag.STEAM);
                ThermalState.addLog(log, pos + " Ice sublimates into steam!");
            } else {
                ThermalState.setWaterPuddle(world, pos);
                ThermalState.addLog(log, pos + " Ice melts into water.");
            }
        } else if (payload == Tag.WATER) {
            map.removeTag(pos, Tag.OIL); map.removeTag(pos, Tag.FLAMMABLE); tile.oilTurns = 0;
            map.removeTag(pos, Tag.ACID); map.removeTag(pos, Tag.LIQUID);
        } else if (payload == Tag.OIL) {
            map.addTag(pos, Tag.OIL); map.addTag(pos, Tag.FLAMMABLE); tile.oilTurns = Balance.OIL_TURNS;
            ThermalState.addLog(log, pos + " Oil coats the ice.");
        } else if (payload == Tag.ACID) {
            map.addTag(pos, Tag.ACID); map.addTag(pos, Tag.LIQUID);
            ThermalState.addLog(log, pos + " Acid pools over the ice.");
        }
    }

    public static void expandCausticGasOutward(World world, Vec2 center) {
        for (int i = 0; i < CARDINALS.length; i++) {
            Vec2 adj = new Vec2(center.x() + CARDINALS[i][0], center.y() + CARDINALS[i][1]);
            GameMap.Tile adjTile = world.tile(adj);
            if (adjTile != null && !adjTile.isWall() && !SpatialService.hasGas(adjTile)) ThermalState.spawnGas(world, adj, Tag.CAUSTIC_GAS);
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

                    if (nbTile.has(Tag.OIL) && !nbTile.has(Tag.BURNING)) {
                        if (nbTile.has(Tag.ICE)) {
                            world.map().removeTag(nb, Tag.ICE); world.map().removeTag(nb, Tag.OIL); world.map().removeTag(nb, Tag.FLAMMABLE);
                            nbTile.oilTurns = 0;
                            ThermalState.spawnGas(world, nb, Tag.STEAM);
                        } else ThermalState.igniteTile(world, nb);
                    } else if (nbTile.has(Tag.WATER)) {
                        world.map().removeTag(nb, Tag.WATER); world.map().removeTag(nb, Tag.LIQUID); world.map().removeTag(nb, Tag.CONDUCTIVE);
                        ThermalState.spawnGas(world, nb, Tag.STEAM);
                    } else {
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
        if (tile.has(Tag.OIL)) {
            if (tile.has(Tag.ICE)) {
                world.map().removeTag(pos, Tag.ICE); world.map().removeTag(pos, Tag.OIL); world.map().removeTag(pos, Tag.FLAMMABLE);
                tile.oilTurns = 0; tile.temp = Balance.AMBIENT_TEMP;
                ThermalState.spawnGas(world, pos, Tag.STEAM);
                return;
            } else ThermalState.igniteTile(world, pos);
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
