package com.archon.system.environment;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.ai.AiSystem;
import com.archon.system.physics.ThermalState;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.List;

import static com.archon.model.Balance.BLIND_RECOVER_CHANCE;

/** Enemy phase and Tab 5A 4-step round-end simulation loop. */
public final class SimulationSystem {

    private SimulationSystem() {}

    public static void tick(World world, EventBus bus) {
        List<String> log = new ArrayList<>();
        world.advanceRound();

        AiSystem.tick(world, bus);

        List<Entity> entities = world.entities();
        Actor thrall = world.thrall();

        runDotStep(world, entities, thrall, log);
        runDurationStep(world, entities, thrall, log);
        runThermalDriftStep(world, entities, thrall, log);
        runTileAndPropDecayStep(world, entities);

        if (!log.isEmpty()) {
            bus.post(new GameEvent.EnvBeat(log, List.of()));
        }
    }

    private static void runDotStep(World world, List<Entity> entities, Actor thrall, List<String> log) {
        if (thrall != null && thrall.alive()) {
            int thrallDotDamage = applyCreatureStatusDots(world, thrall, log);
            if (thrall.alive()) {
                thrallDotDamage += applyTileHazardDots(world, thrall, log);
            }
            if (thrall.alive() && thrallDotDamage > 0) {
                ThermalState.onCreatureDamaged(world, thrall, thrallDotDamage, false, log);
            }
        }
    }

    private static void runDurationStep(World world, List<Entity> entities, Actor thrall, List<String> log) {
        for (int i = 0; i < entities.size(); i++) {
            decrementEntityDurations(world, entities.get(i), log);
        }
        if (thrall != null) {
            decrementEntityDurations(world, thrall, log);
            thrall.resetRoundState();
            if (thrall.alive() && thrall.channeledPayload != null && --thrall.channelTurns <= 0) {
                ThermalState.triggerChannelBackfire(world, thrall, log);
            }
        }
    }

    private static void runThermalDriftStep(World world, List<Entity> entities, Actor thrall, List<String> log) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (entity.kind() == Entity.Kind.CREATURE && entity.alive()) {
                driftCreatureTemp(world, entity, log);
            }
        }
        if (thrall != null && thrall.alive()) {
            driftCreatureTemp(world, thrall, log);
        }
    }

    private static void runTileAndPropDecayStep(World world, List<Entity> entities) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (entity.kind() != Entity.Kind.CREATURE && entity.alive()) {
                decayPropOrDoorTemp(entity);
            }
        }
        int w = world.width();
        int h = world.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                decayTile(world, new Vec2(x, y));
            }
        }
    }

    public static int applyCreatureStatusDots(World world, Entity creature, List<String> log) {
        boolean wasBurning = creature.has(Tag.BURNING);
        int totalDmg = 0;

        if (creature.has(Tag.BLEEDING)) {
            totalDmg += applyRawDot(world, creature, Balance.BLEED_DMG,
                    creature.name() + " bleeds for " + Balance.BLEED_DMG + ".", log);
            if (!creature.alive()) return totalDmg;
        }

        if (!wasBurning && !creature.has(Tag.WET) && !creature.has(Tag.FROZEN)
                && creature.temp >= Balance.BOIL_TEMP && creature.temp < Balance.IGNITE_TEMP) {
            totalDmg += applyRawDot(world, creature, Balance.SCALD_DOT_DMG,
                    creature.name() + " is scalded for " + Balance.SCALD_DOT_DMG + ".", log);
            if (!creature.alive()) return totalDmg;
        }

        if (wasBurning) {
            int burnDmg = Balance.BURN_TICK_BASE
                    + Math.max(0, creature.temp - Balance.IGNITE_TEMP) / Balance.BURN_TEMP_STEP;
            totalDmg += applyRawDot(world, creature, burnDmg,
                    creature.name() + " burns for " + burnDmg + ".", log);
        }
        return totalDmg;
    }

    public static int applyTileHazardDots(World world, Entity creature, List<String> log) {
        GameMap.Tile tile = world.tile(creature.pos());
        if (tile == null) return 0;
        int totalDmg = 0;

        if (!creature.has(Tag.FROZEN) && tile.has(Tag.ACID)) {
            ThermalState.shredArmor(creature, Balance.ACID_ARMOR_SHRED);
            creature.applyTag(Tag.CORRODED);
            totalDmg += applyRawDot(world, creature, Balance.CAUSTIC_DMG,
                    creature.name() + " suffers " + Balance.CAUSTIC_DMG + " acid damage.", log);
            if (!creature.alive()) return totalDmg;
        }

        if (!creature.has(Tag.FROZEN) && tile.has(Tag.TAR)) {
            totalDmg += applyRawDot(world, creature, Balance.TAR_DMG,
                    creature.name() + " suffers " + Balance.TAR_DMG + " tar damage.", log);
            if (!creature.alive()) return totalDmg;
        }

        if (!creature.has(Tag.BURNING) && tile.has(Tag.BURNING)) {
            totalDmg += applyRawDot(world, creature, Balance.BURN_TICK_DMG,
                    creature.name() + " is seared by the burning floor for " + Balance.BURN_TICK_DMG + ".", log);
            if (!creature.alive()) return totalDmg;
            ThermalState.applyBurningFloorContact(world, creature, creature.pos(), log);
        }

        if (tile.has(Tag.SUPERHEATED_STEAM) && creature.alive()) {
            ThermalState.applyCreatureTempDelta(world, creature, Balance.SUPERHEATED_STEAM_TEMP_DELTA, false, log);
            if (creature.alive()) {
                log.add(creature.name() + " heats up in superheated steam (" + creature.temp + "C).");
            }
        }
        return totalDmg;
    }

    private static int applyRawDot(World world, Entity creature, int dmg, String message, List<String> log) {
        creature.takeDamage(dmg);
        log.add(message);
        if (!creature.alive()) {
            log.add(creature.name() + " is consumed.");
            Destruction.Result destruction = Destruction.onKilled(world, creature);
            log.addAll(destruction.messages());
        }
        return dmg;
    }

    private static void decrementEntityDurations(World world, Entity entity, List<String> log) {
        if (!entity.alive()) return;
        if (entity.kind() != Entity.Kind.CREATURE) {
            tickPropDurations(world, entity, log);
            return;
        }

        GameMap.Tile tile = world.tile(entity.pos());
        if (entity.has(Tag.WET)) {
            if (tile != null && tile.has(Tag.WATER)) {
                entity.wetTurns = Balance.WET_TURNS;
            } else if (--entity.wetTurns <= 0) {
                entity.wetTurns = 0;
                entity.removeTag(Tag.WET);
            }
        }

        if (!entity.has(Tag.BURNING) && entity.has(Tag.OIL) && --entity.oilTurns <= 0) {
            entity.oilTurns = 0;
            entity.removeTag(Tag.OIL);
        }

        if (entity.has(Tag.BLEEDING) && --entity.bleedTurns <= 0) {
            ThermalState.clearBleeding(entity);
        }

        if (entity.has(Tag.DAZED) && --entity.dazedTurns <= 0) {
            entity.dazedTurns = 0;
            entity.removeTag(Tag.DAZED);
        }
        if (entity.has(Tag.NUMB_ARM) && --entity.numbArmTurns <= 0) {
            entity.numbArmTurns = 0;
            entity.numbArmPart = null;
            entity.removeTag(Tag.NUMB_ARM);
        }
        if (entity.has(Tag.CRIPPLED) && --entity.crippledTurns <= 0) {
            entity.crippledTurns = 0;
            entity.removeTag(Tag.CRIPPLED);
        }
        if (entity.has(Tag.STUNNED) && !entity.has(Tag.FROZEN) && --entity.stunTurns <= 0) {
            entity.stunTurns = 0;
            entity.removeTag(Tag.STUNNED);
        }

        boolean inGas = tile != null && SpatialService.isOpaqueGas(tile);
        if (inGas) {
            entity.applyTag(Tag.BLIND);
            entity.blindLingerTurns = Balance.BLIND_LINGER_TURNS;
        } else if (entity.has(Tag.BLIND)) {
            if (entity.blindLingerTurns > 0) {
                entity.blindLingerTurns--;
                if (entity.blindLingerTurns == 0) {
                    entity.removeTag(Tag.BLIND);
                    log.add(entity.name() + "'s vision clears.");
                }
            } else if (world.dice().chance(BLIND_RECOVER_CHANCE)) {
                entity.removeTag(Tag.BLIND);
                log.add(entity.name() + "'s vision clears.");
            }
        }
    }

    private static void tickPropDurations(World world, Entity entity, List<String> log) {
        if (!entity.has(Tag.BURNING) && entity.has(Tag.OIL) && --entity.oilTurns <= 0) {
            entity.oilTurns = 0;
            entity.removeTag(Tag.OIL);
        }
        if (entity.has(Tag.BURNING) && !entity.has(Tag.STONE) && !entity.has(Tag.METAL)
                && entity.oilTurns == 0 && --entity.propBurnTurns <= 0) {
            entity.propBurnTurns = 0;
            entity.takeDamage(entity.hp());
            log.add(entity.name() + " burns to ash.");
            Destruction.Result destruction = Destruction.onKilled(world, entity);
            log.addAll(destruction.messages());
        }
    }

    private static void driftCreatureTemp(World world, Entity creature, List<String> log) {
        if (creature.temp < Balance.BODY_TEMP) {
            int warm = (int) Math.floor(Balance.CHILL_WARM_STEP * creature.thermalMass);
            creature.temp = Math.min(Balance.BODY_TEMP, creature.temp + warm);
        } else if (creature.temp > Balance.BODY_TEMP) {
            if (creature.has(Tag.BURNING) && creature.oilTurns > 0) {
                creature.oilTurns--;
                creature.temp = Math.max(creature.temp - Balance.CREATURE_HOT_COOL_STEP, Balance.BURN_CAP);
            } else {
                creature.temp = Math.max(Balance.BODY_TEMP, creature.temp - Balance.CREATURE_HOT_COOL_STEP);
            }
        }

        if (creature.has(Tag.FROZEN) && creature.temp > Balance.FREEZE_TEMP) {
            ThermalState.exitFrozen(creature);
            log.add(creature.name() + " thaws out!");
        }
        if (creature.has(Tag.BURNING) && creature.temp < Balance.IGNITE_TEMP) {
            creature.extinguish();
            creature.removeTag(Tag.OIL);
            creature.oilTurns = 0;
            creature.temp = Balance.BODY_TEMP;
            log.add("The flames on " + creature.name() + " die out.");
        }
        ThermalState.syncThermalState(world, creature, log);
    }

    private static void decayPropOrDoorTemp(Entity entity) {
        if (entity.has(Tag.LIT)) {
            entity.temp = Math.max(entity.temp, Balance.BURN_CAP);
            return;
        }
        if (entity.has(Tag.BURNING) && entity.oilTurns > 0) {
            entity.oilTurns--;
            entity.temp = Balance.BURN_CAP;
            return;
        }
        if (entity.has(Tag.BURNING) && entity.propBurnTurns > 0) {
            entity.temp = Balance.BURN_CAP;
            return;
        }
        entity.temp = driftTowardAmbient(entity.temp);
        if (entity.has(Tag.BURNING) && entity.temp < Balance.IGNITE_TEMP) {
            entity.extinguish();
            entity.removeTag(Tag.OIL);
            entity.oilTurns = 0;
        }
    }

    private static int driftTowardAmbient(int temp) {
        if (temp > Balance.BOIL_TEMP) {
            return Math.max(Balance.AMBIENT_TEMP, temp - Balance.TILE_HOT_COOL_STEP);
        }
        if (temp > Balance.AMBIENT_TEMP) {
            return Math.max(Balance.AMBIENT_TEMP, temp - Balance.TILE_DECAY_STEP);
        }
        if (temp < Balance.AMBIENT_TEMP) {
            return Math.min(Balance.AMBIENT_TEMP, temp + Balance.TILE_DECAY_STEP);
        }
        return temp;
    }

    private static void decayTile(World world, Vec2 pos) {
        GameMap map = world.map();
        GameMap.Tile tile = map.tile(pos);
        if (tile == null || tile.isWall()) return;

        // Gas thermal layer
        if (SpatialService.hasGas(tile)) {
            tile.gasTemp = Math.max(Balance.AMBIENT_TEMP, tile.gasTemp - Balance.TILE_DECAY_STEP);
            if ((tile.has(Tag.STEAM) || tile.has(Tag.SUPERHEATED_STEAM)) && tile.gasTemp < Balance.STEAM_CLEAR_TEMP) {
                ThermalState.clearGas(world, pos);
            } else if (tile.has(Tag.OIL_MIST) && tile.gasTemp < Balance.STEAM_CLEAR_TEMP) {
                ThermalState.clearGas(world, pos);
                ThermalState.setOilSlick(world, pos);
            }
        }

        // Floor thermal layer
        if (tile.has(Tag.BURNING) && (tile.oilTurns > 0 || tile.burnTurns > 0)) {
            if (tile.oilTurns > 0) tile.oilTurns--;
            if (tile.burnTurns > 0) tile.burnTurns--;
            tile.temp = Math.max(tile.temp, Balance.BURN_CAP);
            return;
        }

        tile.temp = driftTowardAmbient(tile.temp);

        if (tile.has(Tag.BURNING) && tile.temp < Balance.IGNITE_TEMP) {
            map.removeTag(pos, Tag.BURNING);
            map.removeTag(pos, Tag.OIL);
            map.removeTag(pos, Tag.TAR);
            map.removeTag(pos, Tag.FLAMMABLE);
            if (!tile.has(Tag.ACID) && !tile.has(Tag.WATER)) {
                map.removeTag(pos, Tag.LIQUID);
            }
        }

        if (tile.has(Tag.ICE) && tile.temp >= Balance.WARM_MELT_TEMP) {
            map.removeTag(pos, Tag.ICE);
            map.addTag(pos, Tag.WATER);
            map.addTag(pos, Tag.LIQUID);
            map.addTag(pos, Tag.CONDUCTIVE);
        } else if (tile.has(Tag.WATER)) {
            if (tile.temp <= Balance.FREEZE_TEMP) {
                map.removeTag(pos, Tag.WATER);
                map.removeTag(pos, Tag.LIQUID);
                map.removeTag(pos, Tag.CONDUCTIVE);
                map.addTag(pos, Tag.ICE);
            } else if (tile.temp >= Balance.BOIL_TEMP) {
                map.removeTag(pos, Tag.WATER);
                map.removeTag(pos, Tag.LIQUID);
                map.removeTag(pos, Tag.CONDUCTIVE);
                ThermalState.spawnGas(world, pos, Tag.STEAM);
            }
        }
    }
}