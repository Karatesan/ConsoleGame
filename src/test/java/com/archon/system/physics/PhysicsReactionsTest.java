package com.archon.system.physics;

import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.environment.Ignition;
import com.archon.system.environment.SimulationSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsReactionsTest {

    private static World newWorld(Dice dice) {
        World w = new World(10, 10, dice);
        w.installThrall(new Thrall(new Vec2(1, 1), 40));
        return w;
    }

    @Test
    void thermalMassCryoThresholdAndExitFrozen() {
        World w = newWorld(new Dice.Seeded(1L));
        Actor rat = new Actor("r1", "Rat", 'r', new Vec2(3, 3), new CreatureStats(20, 2, 0, 1, 0, 0.5));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(4, 3), new CreatureStats(30, 2, 0, 3, 0, 1.0));
        Actor boss = new Actor("b1", "Boss", 'B', new Vec2(5, 5), new CreatureStats(100, 4, 0, 6, 4, 3.0));
        w.add(rat);
        w.add(orc);
        w.add(boss);

        List<String> log = new ArrayList<>();
        // Ambient water cools up to water temp (AMBIENT_TEMP = 20) capped by TD_WATER/thermalMass
        EntityReactions.applyPayloadToEntity(w, w.thrall(), orc, Tag.WATER, log);
        assertTrue(orc.has(Tag.WET));
        assertFalse(orc.has(Tag.FROZEN));
        assertEquals(Balance.BODY_TEMP + Balance.TD_WATER, orc.temp);
        EntityReactions.applyPayloadToEntity(w, w.thrall(), orc, Tag.WATER, log);
        assertEquals(Balance.AMBIENT_TEMP, orc.temp, "Second water hit stops at AMBIENT_TEMP (20C)");

        // Even with thermalMass 0.5, ambient water cools 36 -> 20C without freezing
        EntityReactions.applyPayloadToEntity(w, w.thrall(), rat, Tag.WATER, log);
        assertTrue(rat.has(Tag.WET));
        assertFalse(rat.has(Tag.FROZEN));
        assertEquals(Balance.AMBIENT_TEMP, rat.temp);
        EntityReactions.applyPayloadToEntity(w, w.thrall(), rat, Tag.WATER, log);
        assertEquals(Balance.AMBIENT_TEMP, rat.temp);

        // Siphon-range cooling on a WET rat freezes and triggers Deep Cryo Fracture at <= DEEP_FREEZE_TEMP (-20)
        ThermalState.applyCreatureTempDelta(w, rat, Balance.SIPHON_COOL_DELTA, false, log);
        assertTrue(rat.has(Tag.FROZEN));
        assertTrue(rat.has(Tag.STUNNED));
        assertFalse(rat.has(Tag.WET));
        assertEquals(-40, rat.temp);
        assertTrue(rat.deepFreezeTriggered);
        assertEquals(20 - Balance.CRYO_DMG, rat.hp());
        assertEquals(2 - Balance.CRYO_ARMOR_SHRED, rat.armor());

        // Ambient water on an already-chilled creature (temp <= 0) neither cools below freezing nor warms to 20C,
        // and applying WET still snap-freezes it
        orc.removeTag(Tag.WET);
        orc.wetTurns = 0;
        orc.temp = -2;
        EntityReactions.applyPayloadToEntity(w, w.thrall(), orc, Tag.WATER, log);
        assertEquals(-2, orc.temp);
        assertTrue(orc.has(Tag.FROZEN));

        // Ambient water on already-FROZEN or ambient Prop never cools further below freezing / AMBIENT_TEMP
        EntityReactions.applyPayloadToEntity(w, w.thrall(), rat, Tag.WATER, log);
        assertEquals(-40, rat.temp);
        assertEquals(20 - Balance.CRYO_DMG, rat.hp());
        Prop crate = Prop.woodCrate("c1", new Vec2(2, 2));
        w.add(crate);
        EntityReactions.applyPayloadToEntity(w, w.thrall(), crate, Tag.WATER, log);
        assertEquals(Balance.AMBIENT_TEMP, crate.temp);

        rat.applyTag(Tag.OIL);
        rat.oilTurns = Balance.OIL_TURNS;
        ThermalState.exitFrozen(rat);
        assertFalse(rat.has(Tag.FROZEN));
        assertFalse(rat.has(Tag.STUNNED));
        assertFalse(rat.deepFreezeTriggered);
        assertEquals(Balance.THAW_RESET_TEMP, rat.temp);
        assertTrue(rat.has(Tag.OIL));

        ThermalState.applyCreatureTempDelta(w, boss, Balance.SIPHON_COOL_DELTA, false, log);
        assertEquals(26, boss.temp);
        assertFalse(boss.has(Tag.CHILLED));
    }

    @Test
    void multiStatePrecedenceWetBleedingChilledHitByHeat() {
        World w = newWorld(new Dice.Seeded(1L));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(4, 4), new CreatureStats(40, 2, 0));
        w.add(orc);

        orc.temp = 10;
        orc.applyTag(Tag.WET);
        orc.wetTurns = Balance.WET_TURNS;
        orc.applyTag(Tag.BLEEDING);
        orc.applyTag(Tag.BLOOD);
        orc.bleedTurns = Balance.BLEED_TURNS;
        orc.applyTag(Tag.CHILLED);

        List<String> log = new ArrayList<>();
        EntityReactions.applyPayloadToEntity(w, w.thrall(), orc, Tag.FIRE, log);

        int expectedDmg = Balance.DMG_HEAT + (Balance.BASE_MAGIC / 2) + Balance.SCALD_FLASH_DMG + Balance.CAUTERIZE_SCALD_DMG;
        assertEquals(40 - expectedDmg, orc.hp());
        assertEquals(Balance.BOIL_TEMP, orc.temp);
        assertFalse(orc.has(Tag.WET));
        assertFalse(orc.has(Tag.BLEEDING));
        assertFalse(orc.has(Tag.BLOOD));
        assertFalse(orc.has(Tag.CHILLED));
        assertFalse(orc.has(Tag.BURNING), "Steam Flash blocks burn");
        assertTrue(w.tile(orc.pos()).has(Tag.STEAM));
    }

    @Test
    void mechanicalShatterThermalShockOiledFreezeFlareUpAndIceShellBlock() {
        World w = newWorld(new Dice.Seeded(1L));
        List<String> log = new ArrayList<>();

        Actor a = new Actor("a1", "OrcA", 'O', new Vec2(3, 3), new CreatureStats(40, 3, 0));
        w.add(a);
        a.applyTag(Tag.FROZEN);
        a.applyTag(Tag.STUNNED);
        a.temp = -5;
        EntityReactions.applyPayloadToEntity(w, w.thrall(), a, Tag.FORCE, log);
        assertEquals(2, a.armor());
        assertEquals(40 - 7, a.hp());
        assertFalse(a.has(Tag.FROZEN));
        assertEquals(Balance.THAW_RESET_TEMP, a.temp);

        Actor b = new Actor("b1", "OrcB", 'O', new Vec2(4, 4), new CreatureStats(40, 3, 0));
        w.add(b);
        b.applyTag(Tag.FROZEN);
        b.applyTag(Tag.OIL);
        b.oilTurns = Balance.OIL_TURNS;
        b.temp = -10;
        EntityReactions.applyPayloadToEntity(w, w.thrall(), b, Tag.FIRE, log);
        assertEquals(40 - Balance.THERMAL_SHOCK_DMG, b.hp());
        assertEquals(3 - Balance.THERMAL_SHOCK_ARMOR_SHRED, b.armor());
        assertFalse(b.has(Tag.FROZEN));
        assertTrue(b.has(Tag.BURNING));
        assertTrue(b.has(Tag.OIL));
        assertEquals(Balance.BURN_CAP, b.temp);
        assertTrue(w.tile(b.pos()).has(Tag.STEAM));

        EntityReactions.applyPayloadToEntity(w, w.thrall(), b, Tag.WATER, log);
        assertEquals(40 - Balance.THERMAL_SHOCK_DMG - Balance.SCALD_FLASH_DMG, b.hp());
        assertFalse(b.has(Tag.BURNING));
        assertFalse(b.has(Tag.OIL));
        assertEquals(Balance.BODY_TEMP, b.temp);

        Actor c = new Actor("c1", "OrcC", 'O', new Vec2(5, 5), new CreatureStats(40, 3, 0));
        w.add(c);
        c.applyTag(Tag.FROZEN);
        c.temp = -5;
        EntityReactions.applyPayloadToEntity(w, w.thrall(), c, Tag.ACID, log);
        assertEquals(40, c.hp());
        assertEquals(3, c.armor());
        assertFalse(c.has(Tag.CORRODED));
        assertTrue(c.has(Tag.FROZEN));
        assertTrue(w.tile(c.pos()).has(Tag.ACID));
    }

    @Test
    void twoLayerTileGasTempAndIceSublimationAndOiledIce() {
        World w = newWorld(new Dice.Seeded(1L));
        Vec2 p1 = new Vec2(3, 3);
        w.map().addTag(p1, Tag.ICE);
        ThermalState.spawnGas(w, p1, Tag.STEAM);

        assertEquals(Balance.BOIL_TEMP, w.tile(p1).gasTemp);
        assertEquals(Balance.AMBIENT_TEMP, w.tile(p1).temp);
        assertTrue(w.tile(p1).has(Tag.ICE));

        ThermalState.clearGas(w, p1);
        TileReactions.applyPayloadToTile(w, w.thrall(), p1, Tag.FIRE, false, null, null);
        assertFalse(w.tile(p1).has(Tag.ICE));
        assertTrue(w.tile(p1).has(Tag.WATER));

        Vec2 p2 = new Vec2(4, 3);
        w.map().addTag(p2, Tag.ICE);
        TileReactions.applyPayloadToTile(w, w.thrall(), p2, Tag.INFERNO, false, null, null);
        assertFalse(w.tile(p2).has(Tag.ICE));
        assertFalse(w.tile(p2).has(Tag.WATER));
        assertTrue(w.tile(p2).has(Tag.STEAM));

        Vec2 p3 = new Vec2(5, 3);
        w.map().addTag(p3, Tag.ICE);
        TileReactions.applyPayloadToTile(w, w.thrall(), p3, Tag.OIL, false, null, null);
        assertTrue(w.tile(p3).has(Tag.ICE) && w.tile(p3).has(Tag.OIL));
        TileReactions.applyPayloadToTile(w, w.thrall(), p3, Tag.FIRE, false, null, null);
        assertFalse(w.tile(p3).has(Tag.ICE));
        assertFalse(w.tile(p3).has(Tag.OIL));
        assertTrue(w.tile(p3).has(Tag.STEAM));
    }

    @Test
    void causticRuptureAndChainedFuelAirDetonationCapAtOneHitPerCreature() {
        World w = newWorld(new Dice.Seeded(1L));
        Actor occ = new Actor("o1", "Orc", 'O', new Vec2(4, 4), new CreatureStats(40, 2, 0));
        w.add(occ);
        ThermalState.spawnGas(w, new Vec2(4, 4), Tag.CAUSTIC_GAS);
        TileReactions.applyPayloadToTile(w, w.thrall(), new Vec2(4, 4), Tag.FIRE, false, null, null);
        assertFalse(w.tile(new Vec2(4, 4)).has(Tag.CAUSTIC_GAS));
        assertTrue(occ.has(Tag.CORRODED));
        assertEquals(1, occ.armor());
        int expectedRupture = Balance.DMG_HEAT + (Balance.BASE_MAGIC / 2) + Balance.RUPTURE_ACID_DMG;
        assertEquals(40 - expectedRupture, occ.hp());

        Actor victim = new Actor("o2", "Victim", 'O', new Vec2(6, 5), new CreatureStats(50, 5, 0));
        Actor wetBuddy = new Actor("o3", "Wet", 'O', new Vec2(7, 5), new CreatureStats(50, 0, 0));
        wetBuddy.applyTag(Tag.WET);
        wetBuddy.wetTurns = Balance.WET_TURNS;
        w.add(victim);
        w.add(wetBuddy);

        ThermalState.spawnGas(w, new Vec2(6, 4), Tag.OIL_MIST);
        ThermalState.spawnGas(w, new Vec2(7, 4), Tag.OIL_MIST);

        Set<Entity> hitSet = new HashSet<>();
        TileReactions.applyPayloadToTile(w, w.thrall(), new Vec2(6, 4), Tag.FIRE, false, hitSet, null);

        int fuelAirDmg = Balance.FUEL_AIR_DMG_HEAT + (Balance.BASE_MAGIC / 2);
        assertEquals(50 - fuelAirDmg, victim.hp(), "Max 1 Fuel-Air hit per creature across chained mist");
        assertTrue(victim.has(Tag.BURNING));
        assertEquals(50 - fuelAirDmg, wetBuddy.hp());
        assertFalse(wetBuddy.has(Tag.WET), "WET flashes to STEAM instead of burning");
        assertFalse(wetBuddy.has(Tag.BURNING));
        assertTrue(w.tile(wetBuddy.pos()).has(Tag.STEAM));
        assertFalse(w.tile(new Vec2(6, 4)).has(Tag.OIL_MIST));
        assertFalse(w.tile(new Vec2(7, 4)).has(Tag.OIL_MIST));
    }

    @Test
    void onEnterTileAndRelocateGasWallSplash() {
        World w = newWorld(new Dice.Always(true));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(2, 5), new CreatureStats(30, 2, 0));
        w.add(orc);

        Vec2 tarPos = new Vec2(3, 5);
        ThermalState.setToxicTar(w, tarPos);
        boolean slipped = TileDisplacement.onEnterTile(w, orc, tarPos, true, null);
        assertTrue(slipped);
        assertTrue(orc.has(Tag.PRONE));
        assertTrue(orc.has(Tag.CORRODED));
        assertEquals(1, orc.armor());
        assertEquals(30 - Balance.CAUSTIC_DMG, orc.hp());
        assertTrue(orc.has(Tag.OIL));

        orc.ready(Actor.Trigger.ON_ADJACENCY, "strike", 5);
        Vec2 gasPos = new Vec2(4, 5);
        ThermalState.spawnGas(w, gasPos, Tag.STEAM);
        TileDisplacement.onEnterTile(w, orc, gasPos, true, null);
        assertTrue(orc.has(Tag.BLIND));
        assertEquals(Balance.BLIND_LINGER_TURNS, orc.blindLingerTurns);
        assertNull(orc.readied());

        w.map().setWall(new Vec2(5, 0), true);
        ThermalState.spawnGas(w, new Vec2(5, 3), Tag.STEAM);
        TileDisplacement.relocateGasOrLiquid(w, new Vec2(5, 3), new Vec2(0, -1), 4, null, null);
        assertFalse(w.tile(new Vec2(5, 3)).has(Tag.STEAM));
        assertTrue(w.tile(new Vec2(4, 1)).has(Tag.STEAM));
        assertTrue(w.tile(new Vec2(6, 1)).has(Tag.STEAM));
    }

    @Test
    void concentrationDisruptionTriggersBackfireOnlyOnExternalDamage() {
        World w = newWorld(new Dice.Always(false));
        w.thrall().channeledPayload = Tag.FORCE;
        w.thrall().channelTurns = Balance.CHANNEL_MAX_TURNS;

        ThermalState.onCreatureDamaged(w, w.thrall(), 6, true, null);
        assertEquals(Tag.FORCE, w.thrall().channeledPayload);

        ThermalState.onCreatureDamaged(w, w.thrall(), 5, false, null);
        assertNull(w.thrall().channeledPayload);
        assertEquals(0, w.thrall().channelTurns);
        assertEquals(40 - Balance.FORCE_COLLISION_DMG, w.thrall().hp());
        assertTrue(w.thrall().has(Tag.PRONE));

        w.thrall().channeledPayload = Tag.FIRE;
        w.thrall().channelTurns = Balance.CHANNEL_MAX_TURNS;
        int hpBefore = w.thrall().hp();
        ThermalState.onCreatureDamaged(w, w.thrall(), 4, false, null);
        assertNull(w.thrall().channeledPayload);
        int fireDmg = Balance.DMG_HEAT + (Balance.BASE_MAGIC / 2);
        assertEquals(hpBefore - fireDmg, w.thrall().hp());
        assertEquals(Balance.BODY_TEMP + Balance.TD_HEAT, w.thrall().temp);
    }

    @Test
    void oilRefuelClampsBurnCapAndWaterRinsesOiledAndAcidIceKeepingIce() {
        World w = newWorld(new Dice.Seeded(1L));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(3, 3), new CreatureStats(40, 2, 0));
        w.add(orc);

        // G9 / H9: burning orc cooled below BURN_CAP on a Grease Fire tile gets BURN_CAP + tile oilTurns refreshed
        orc.applyTag(Tag.BURNING);
        orc.temp = 210;
        ThermalState.setOilSlick(w, orc.pos());
        ThermalState.igniteTile(w, orc.pos());
        w.tile(orc.pos()).oilTurns = 1;

        EntityReactions.applyPayloadToEntity(w, w.thrall(), orc, Tag.OIL, null);
        assertTrue(orc.has(Tag.OIL));
        assertEquals(Balance.OIL_TURNS, orc.oilTurns);
        assertEquals(Balance.BURN_CAP, orc.temp);
        assertEquals(Balance.OIL_TURNS, w.tile(orc.pos()).oilTurns);

        // N8: Water on Oiled ice and Acid on ice rinses surface OIL/ACID while keeping ICE
        Vec2 icePos = new Vec2(5, 5);
        w.map().addTag(icePos, Tag.ICE);
        TileReactions.applyPayloadToTile(w, w.thrall(), icePos, Tag.OIL, false, null, null);
        TileReactions.applyPayloadToTile(w, w.thrall(), icePos, Tag.ACID, false, null, null);
        assertTrue(w.tile(icePos).has(Tag.ICE) && w.tile(icePos).has(Tag.OIL) && w.tile(icePos).has(Tag.ACID));

        TileReactions.applyPayloadToTile(w, w.thrall(), icePos, Tag.WATER, false, null, null);
        assertTrue(w.tile(icePos).has(Tag.ICE));
        assertFalse(w.tile(icePos).has(Tag.OIL));
        assertFalse(w.tile(icePos).has(Tag.ACID));
        assertFalse(w.tile(icePos).has(Tag.WATER));

        // J7: Steam explosion burst respects burstHitEntities deduplication
        Actor victim = new Actor("o2", "Victim", 'O', new Vec2(7, 7), new CreatureStats(30, 0, 0));
        w.add(victim);
        ThermalState.spawnGas(w, victim.pos(), Tag.STEAM);
        Set<Entity> burstHit = new HashSet<>();
        TileReactions.applyPayloadToTile(w, w.thrall(), victim.pos(), Tag.FIRE, true, burstHit, null);
        assertEquals(30 - Balance.SCALD_FLASH_DMG, victim.hp());
        ThermalState.spawnGas(w, victim.pos(), Tag.STEAM);
        TileReactions.applyPayloadToTile(w, w.thrall(), victim.pos(), Tag.FIRE, true, burstHit, null);
        assertEquals(30 - Balance.SCALD_FLASH_DMG, victim.hp());
    }

    @Test
    void litPropHoldsBurnCapAcrossTicksAndWaterExtinguishesLitPropsAndGroundItems() {
        World w = newWorld(new Dice.Seeded(1L));
        Prop brazier = new Prop("br1", "Brazier", 'i', new Vec2(3, 3), 10);
        brazier.with(Tag.METAL, Tag.LIT);
        w.add(brazier);
        assertEquals(Balance.BURN_CAP, brazier.temp);

        EventBus bus = new EventBus();
        SimulationSystem.tick(w, bus);
        assertEquals(Balance.BURN_CAP, brazier.temp, "LIT prop holds BURN_CAP across ticks");
        assertTrue(brazier.has(Tag.LIT));

        // Water douses LIT prop -> removes LIT, resets temp to AMBIENT_TEMP, spawns STEAM
        List<String> log = new ArrayList<>();
        EntityReactions.applyPayloadToEntity(w, w.thrall(), brazier, Tag.WATER, log);
        assertFalse(brazier.has(Tag.LIT));
        assertEquals(Balance.AMBIENT_TEMP, brazier.temp);
        assertTrue(w.tile(brazier.pos()).has(Tag.STEAM));
        assertTrue(log.stream().anyMatch(s -> s.contains("doused")));

        // Water on ground tile douses LIT ground torch -> removes LIT, spawns STEAM
        Vec2 torchPos = new Vec2(5, 5);
        Item torch = new Item("torch", "torch", Set.of(Tag.WOOD, Tag.LIT), null, 10, 1, 2);
        w.map().placeGroundItem(torchPos, torch);
        List<String> tileLog = new ArrayList<>();
        TileReactions.applyPayloadToTile(w, w.thrall(), torchPos, Tag.WATER, false, null, tileLog);
        assertFalse(torch.has(Tag.LIT));
        assertTrue(w.tile(torchPos).has(Tag.STEAM));
        assertTrue(tileLog.stream().anyMatch(s -> s.contains("doused")));
    }

    @Test
    void waterPuddleEntryWetsAndExtinguishesWithoutChillingAndEnemiesLackFlammable() {
        World w = newWorld(new Dice.Seeded(1L));
        Actor goblin = new Actor("g1", "Goblin", 'g', new Vec2(2, 5), new CreatureStats(14, 0, 10, 2, 1, 0.8));
        w.add(goblin);
        Vec2 puddle = new Vec2(3, 5);
        ThermalState.setWaterPuddle(w, puddle);

        TileDisplacement.onEnterTile(w, goblin, puddle, true, null);
        assertTrue(goblin.has(Tag.WET));
        assertFalse(goblin.has(Tag.CHILLED), "Stepping in water puddle must not chill creature");
        assertEquals(Balance.BODY_TEMP, goblin.temp);

        // Burning creature stepping into water puddle still extinguishes into steam
        Actor burning = new Actor("o1", "Orc", 'O', new Vec2(4, 5), new CreatureStats(24, 2, 0));
        burning.applyTag(Tag.BURNING);
        burning.temp = Balance.BURN_CAP;
        w.add(burning);
        TileDisplacement.onEnterTile(w, burning, puddle, true, null);
        assertFalse(burning.has(Tag.BURNING));
        assertEquals(Balance.BODY_TEMP, burning.temp);
        assertTrue(w.tile(puddle).has(Tag.STEAM));

        // Enemies in scenarios must not carry FLAMMABLE
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        for (String id : List.of("r1", "o1", "og1", "g1")) {
            assertFalse(arena.actor(id).has(Tag.FLAMMABLE), id + " should not have FLAMMABLE");
        }
        assertTrue(arena.get("b1").has(Tag.FLAMMABLE));
        World room = Scenario.testRoom(new Dice.Seeded(1L));
        for (String id : List.of("o1", "g1")) {
            assertFalse(room.actor(id).has(Tag.FLAMMABLE), id + " in testRoom should not have FLAMMABLE");
        }
    }

    @Test
    void brazierAndTorchRelightInOneHitAndDuplicateWetOilLogsAreSuppressed() {
        World w = newWorld(new Dice.Seeded(1L));

        // Brazier factory carries METAL + FLAMMABLE + LIT; extinguished brazier relights in 1 fire hit or Ignition.entity
        Prop brazier = Prop.brazier("br1", new Vec2(3, 3));
        w.add(brazier);
        EntityReactions.applyPayloadToEntity(w, w.thrall(), brazier, Tag.WATER, null);
        assertFalse(brazier.has(Tag.LIT));
        assertEquals(Balance.AMBIENT_TEMP, brazier.temp);

        EntityReactions.applyPayloadToEntity(w, w.thrall(), brazier, Tag.FIRE, null);
        assertTrue(brazier.has(Tag.LIT), "Single fire hit must relight extinguished brazier");
        assertTrue(brazier.temp >= Balance.BURN_CAP);
        SimulationSystem.tick(w, new EventBus());
        assertTrue(brazier.alive() && brazier.has(Tag.LIT), "METAL+FLAMMABLE brazier stays lit without burning to ash");

        brazier.removeTag(Tag.LIT);
        brazier.temp = Balance.AMBIENT_TEMP;
        assertTrue(Ignition.entity(brazier), "ignite must relight brazier in 1 action");
        assertTrue(brazier.has(Tag.LIT));

        // Torch factory carries FLAMMABLE; unlit ground torch relights in 1 fire payload hit
        Item groundTorch = Item.torch("torch", false);
        Vec2 torchPos = new Vec2(4, 3);
        w.map().placeGroundItem(torchPos, groundTorch);
        TileReactions.applyPayloadToTile(w, w.thrall(), torchPos, Tag.FIRE, false, null, null);
        assertTrue(groundTorch.has(Tag.LIT), "Single fire payload must light unlit ground torch");

        // Duplicate WET / OIL logs are skipped when target already has WET / OIL
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(2, 5), new CreatureStats(30, 0, 0));
        w.add(orc);
        ThermalState.setWaterPuddle(w, new Vec2(3, 5));
        ThermalState.setWaterPuddle(w, new Vec2(4, 5));
        List<String> stepLogs = new ArrayList<>();
        TileDisplacement.onEnterTile(w, orc, new Vec2(3, 5), false, stepLogs);
        assertTrue(stepLogs.stream().anyMatch(s -> s.contains("is soaked (WET)")));
        stepLogs.clear();
        TileDisplacement.onEnterTile(w, orc, new Vec2(4, 5), false, stepLogs);
        assertFalse(stepLogs.stream().anyMatch(s -> s.contains("is soaked (WET)")), "Repeat water entry must skip WET log: " + stepLogs);

        ThermalState.setOilSlick(w, new Vec2(5, 5));
        ThermalState.setOilSlick(w, new Vec2(6, 5));
        stepLogs.clear();
        TileDisplacement.onEnterTile(w, orc, new Vec2(5, 5), false, stepLogs);
        assertTrue(stepLogs.stream().anyMatch(s -> s.contains("is coated in oil")));
        stepLogs.clear();
        TileDisplacement.onEnterTile(w, orc, new Vec2(6, 5), false, stepLogs);
        assertFalse(stepLogs.stream().anyMatch(s -> s.contains("is coated in oil")), "Repeat oil entry must skip OIL log: " + stepLogs);
    }
}
