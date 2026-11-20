package com.archon.system.ai;

import com.archon.app.DefaultVerbs;
import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.Interrupts;
import com.archon.exec.RoundState;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.EquipmentSlot;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.CombatEngine;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.Ignition;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.environment.Substances;
import com.archon.system.physics.ThermalState;
import com.archon.system.spatial.SpatialService;
import com.archon.verb.VerbCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiSystemTest {

    private static Executor executor(World world) {
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        VerbCatalog catalog = DefaultVerbs.create(round.rules());
        Interrupts interrupts = new Interrupts(() -> ReactionSystem.fire(world), bus);
        return new Executor(world, bus, round, catalog, interrupts, () -> SimulationSystem.tick(world, bus));
    }

    @Test
    void enemyPhaseShootsNocksAndDoesNotDoubleTapOverwatch() {
        World world = Scenario.testRoom(new Dice.Seeded(42L));
        Executor exec = executor(world);

        int initialHp = world.thrall().hp();
        exec.submit("pass");

        Actor orc = world.actor("o1");
        Actor archer = world.actor("g1");

        assertTrue(world.thrall().hp() < initialHp, "Thrall should take damage from enemy phase");
        assertTrue(orc.isGuarded(), "Orc Guard should raise guard with its second action when adjacent");
        assertTrue(archer.isNocked(), "Goblin Archer should spend second action nocking after shooting");
        assertNull(archer.readied(), "Goblin Archer must not both shoot and set overwatch on the same turn");
    }

    @Test
    void cardinalLineOfFireBlocksDiagonalAndReadiesWhenOutOfLane() {
        World world = Scenario.testRoom(new Dice.Seeded(42L));
        Actor archer = world.actor("g1");
        // Move Thrall off the y=4 cardinal lane and cripple archer legs so it cannot step into lane
        world.thrall().moveTo(new Vec2(5, 2));
        archer.cripple(BodyPart.LEGS);
        archer.fireNocked(); // start unnocked

        assertFalse(SpatialService.lineOfFire(world, archer.pos(), world.thrall().pos()),
                "Diagonal should not have 4-way cardinal line of fire");

        AiSystem.tick(world, new EventBus());
        assertTrue(archer.isNocked(), "Archer should nock with first action");
        assertNotNull(archer.readied(), "Archer without LoF should ready overwatch with second action");
    }

    @Test
    void bfsPathfindingRoutesAroundWallObstacle() {
        World world = Scenario.testRoom(new Dice.Seeded(1L));
        Actor orc = world.actor("o1");
        // Place Thrall at (3,2) north of the wall at (3,3), and Orc at (3,4) south of the wall
        world.thrall().moveTo(new Vec2(3, 2));
        orc.moveTo(new Vec2(3, 4));

        AiSystem.tick(world, new EventBus());
        assertEquals(2, Math.abs(orc.pos().x() - world.thrall().pos().x()) + Math.abs(orc.pos().y() - world.thrall().pos().y()),
                "Orc must route cardinally around (3,3) wall in 2 steps without diagonal corner-cutting");
        AiSystem.tick(world, new EventBus());
        assertEquals(1, orc.pos().manhattan(world.thrall().pos()),
                "Orc should reach cardinal melee adjacency on round 2");
    }

    @Test
    void disarmedEnemyRecoversWeaponOnItsTurn() {
        World world = Scenario.testRoom(new Dice.Seeded(1L));
        Actor orc = world.actor("o1");

        var dropped = orc.inventory().removeEquipped(EquipmentSlot.HAND_RIGHT);
        assertNotNull(dropped);
        world.map().placeGroundItem(orc.pos(), dropped);
        assertNull(orc.mainHand());

        List<String> log = new java.util.ArrayList<>();
        EventBus bus = new EventBus();
        bus.subscribe(e -> { if (e instanceof GameEvent.ActorBeat b) log.addAll(b.lines()); });
        AiSystem.tick(world, bus);
        assertEquals(dropped, orc.mainHand(), "Orc should pick up and re-equip its dropped sword");
        assertTrue(log.stream().anyMatch(s -> s.contains("recovers iron sword")));
    }

    @Test
    void crippledLegsPreventEnemyMovement() {
        World world = Scenario.testRoom(new Dice.Seeded(7L));
        Actor orc = world.actor("o1");
        orc.moveTo(new Vec2(3, 6));
        orc.cripple(BodyPart.LEGS);

        AiSystem.tick(world, new EventBus());
        assertEquals(new Vec2(3, 6), orc.pos(), "Orc with crippled legs should not be able to advance");
        assertNotNull(orc.readied(), "Orc at distance 2 should ready an adjacency strike instead");
    }

    @Test
    void carefulStepCanEvadeReadiedOverwatch() {
        World world = Scenario.testRoom(new Dice.Seeded(10L));
        Executor exec = executor(world);

        Actor archer = world.actor("g1");
        exec.submit("step e -c");

        assertEquals(new Vec2(4, 4), world.thrall().pos());
        assertFalse(archer.isReadiedSpent(), "Careful step should evade triggering the archer's overwatch");
        assertEquals(40, world.thrall().hp());
    }

    @Test
    void oilSlipOnMovementAndStandingUp() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Executor exec = executor(world);

        // Pour oil on Thrall's current floor tile (3,4) via @self/floor, then step out of it
        exec.submit("pour flask_oil @self/floor");
        assertFalse(world.thrall().has(Tag.OIL), "@self/floor should oil the floor only, not Thrall");
        assertFalse(world.thrall().has(Tag.PRONE), "Standing still on poured oil does not slip");

        exec.submit("step e");
        assertEquals(new Vec2(4, 4), world.thrall().pos());
        assertTrue(world.thrall().has(Tag.PRONE), "Stepping out of oiled tile should trigger slip");

        exec.submit("step e");
        assertEquals(new Vec2(4, 4), world.thrall().pos(), "Next step while prone stands up instead of moving");
        assertFalse(world.thrall().has(Tag.PRONE));
    }

    @Test
    void headStrikeCritAndBlindness() {
        Dice alwaysMaxCrit = new Dice.Always(true);
        World world = Scenario.testRoom(alwaysMaxCrit);
        Actor orc = world.actor("o1");

        // Cleaver max 8 + str 3 = 11; head 2.5x = 27.5; crit 2x = 55; critical hit ignores armor = 55 dmg
        var res = CombatEngine.resolveMelee(alwaysMaxCrit, world, world.thrall(), orc, BodyPart.HEAD, true, "normal", false);
        assertTrue(res.hit());
        assertTrue(res.crit(), "Should trigger critical hit");
        assertEquals(55, res.damage(), "Critical hits bypass armor and apply multipliers");
    }

    @Test
    void waterExtinguishesFireAndWetPreventsIgnition() {
        World world = Scenario.testRoom(new Dice.Seeded(1L));
        Actor orc = world.actor("o1");
        orc.ignite();
        world.map().addTag(orc.pos(), Tag.BURNING);

        // G8: Water spill on occupied tile extinguishes burning occupant only; floorOnly spill extinguishes burning tile
        Substances.spill(world, orc.pos(), Tag.WATER);
        assertFalse(orc.has(Tag.BURNING), "Water should extinguish burning occupant");
        assertTrue(world.tile(orc.pos()).has(Tag.BURNING), "Occupant-targeted spill leaves floor untouched");
        assertTrue(world.tile(orc.pos()).has(Tag.STEAM), "Extinguishing fire should produce STEAM");
        assertFalse(orc.has(Tag.WET), "G8 extinguish leaves creature Dry at BODY_TEMP");

        Substances.spill(world, orc.pos(), Tag.WATER, true, null);
        assertFalse(world.tile(orc.pos()).has(Tag.BURNING), "Floor-targeted spill should extinguish burning tile");

        // Second Water spill on Dry creature applies WET (which blocks ignition)
        Substances.spill(world, orc.pos(), Tag.WATER);
        assertTrue(orc.has(Tag.WET), "Water on Dry creature should apply WET tag");

        assertFalse(Ignition.entity(orc), "WET entity should resist ignition");
        assertFalse(orc.has(Tag.BURNING));
        assertFalse(orc.has(Tag.WET), "Resisting ignition should evaporate WET");
    }

    @Test
    void greaseFireHoldsThreeRoundsBeforeCoolingOnCreatureAndTile() {
        World w = new World(10, 10, new Dice.Always(false));
        w.installThrall(new Thrall(new Vec2(1, 1), 40));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(8, 8), new CreatureStats(80, 0, 0));
        orc.cripple(BodyPart.LEGS);
        w.add(orc);

        orc.applyTag(Tag.OIL);
        orc.applyTag(Tag.BURNING);
        orc.oilTurns = Balance.OIL_TURNS;
        orc.temp = Balance.BURN_CAP;

        Vec2 tilePos = new Vec2(5, 5);
        ThermalState.setOilSlick(w, tilePos);
        ThermalState.igniteTile(w, tilePos);
        EventBus bus = new EventBus();

        for (int r = 1; r <= 3; r++) {
            SimulationSystem.tick(w, bus);
            assertTrue(orc.has(Tag.BURNING), "Creature grease fire must still burn on round " + r);
            assertEquals(Balance.BURN_CAP, orc.temp, "Creature grease fire must hold BURN_CAP on round " + r);
            assertEquals(Balance.OIL_TURNS - r, orc.oilTurns);
            assertTrue(w.tile(tilePos).has(Tag.BURNING), "Tile grease fire must still burn on round " + r);
            assertEquals(Balance.BURN_CAP, w.tile(tilePos).temp, "Tile grease fire must hold BURN_CAP on round " + r);
            assertEquals(Balance.OIL_TURNS - r, w.tile(tilePos).oilTurns);
        }

        SimulationSystem.tick(w, bus);
        assertTrue(orc.has(Tag.BURNING), "Round 4 cools 250 -> 220 >= IGNITE_TEMP");
        assertEquals(Balance.BURN_CAP - Balance.CREATURE_HOT_COOL_STEP, orc.temp);
        assertFalse(w.tile(tilePos).has(Tag.BURNING), "Round 4 cools tile 250 -> 190 < IGNITE_TEMP");
        assertFalse(w.tile(tilePos).has(Tag.OIL));

        SimulationSystem.tick(w, bus);
        assertFalse(orc.has(Tag.BURNING), "Round 5 cools 220 -> 190 < IGNITE_TEMP and extinguishes");
        assertFalse(orc.has(Tag.OIL));
        assertEquals(Balance.BODY_TEMP, orc.temp);
    }

    @Test
    void blindLingerFrozenCausticBlockAndAiSnapFreezeHaltsTurn() {
        World w = new World(10, 10, new Dice.Always(false));
        w.installThrall(new Thrall(new Vec2(2, 2), 40));
        Executor exec = executor(w);

        // Blind lingers 1 full round after leaving gas
        ThermalState.spawnGas(w, new Vec2(3, 2), Tag.STEAM);
        exec.submit("step e");
        assertTrue(w.thrall().has(Tag.BLIND));
        exec.submit("step e");
        assertEquals(new Vec2(4, 2), w.thrall().pos());
        assertTrue(w.thrall().has(Tag.BLIND));

        exec.submit("pass");
        assertTrue(w.thrall().has(Tag.BLIND), "BLIND must linger for 1 full round after leaving gas");

        exec.submit("pass");
        assertFalse(w.thrall().has(Tag.BLIND), "BLIND should clear after the linger round ends");

        // FROZEN ice shell blocks ACID round-start damage and armor shred
        Actor frozen = new Actor("f1", "Frozen", 'O', new Vec2(2, 7), new CreatureStats(30, 3, 0));
        frozen.applyTag(Tag.FROZEN);
        frozen.applyTag(Tag.STUNNED);
        frozen.temp = -25;
        w.add(frozen);
        ThermalState.setAcidPool(w, new Vec2(2, 7));
        SimulationSystem.tick(w, new EventBus());
        assertEquals(30, frozen.hp());
        assertEquals(3, frozen.armor());
        assertFalse(frozen.has(Tag.CORRODED));

        // AI snap-freezing mid-turn on WATER tile entry halts remaining actions
        World aiWorld = new World(10, 10, new Dice.Seeded(1L));
        aiWorld.installThrall(new Thrall(new Vec2(2, 2), 40));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(4, 2), new CreatureStats(24, 2, 0));
        orc.temp = 0;
        orc.applyTag(Tag.CHILLED);
        aiWorld.add(orc);
        ThermalState.setWaterPuddle(aiWorld, new Vec2(3, 2));

        AiSystem.tick(aiWorld, new EventBus());
        assertEquals(new Vec2(3, 2), orc.pos());
        assertTrue(orc.has(Tag.FROZEN));
        assertTrue(orc.has(Tag.STUNNED));
        assertEquals(40, aiWorld.thrall().hp(), "Snap-frozen AI must not strike after freezing mid-turn");
        assertFalse(orc.isGuarded(), "Snap-frozen AI must not raise guard after freezing mid-turn");
    }

    @Test
    void dazedEnemySuffersHitPenaltyForTwoRoundsThenRecovers() {
        World w = new World(10, 10, new Dice.Always(false));
        w.installThrall(new Thrall(new Vec2(2, 2), 40));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(3, 2), new CreatureStats(30, 0, 0));
        orc.applyTag(Tag.DAZED);
        orc.dazedTurns = Balance.DAZED_TURNS;
        w.add(orc);

        List<String> lines = new java.util.ArrayList<>();
        EventBus bus = new EventBus();
        bus.subscribe(ev -> { if (ev instanceof GameEvent.ActorBeat b) lines.addAll(b.lines()); });

        SimulationSystem.tick(w, bus);
        assertTrue(lines.stream().anyMatch(s -> s.contains("[50% |")), "Round 1 dazed orc has 50% hit chance: " + lines);
        assertTrue(orc.has(Tag.DAZED));
        assertEquals(1, orc.dazedTurns);

        lines.clear();
        SimulationSystem.tick(w, bus);
        assertTrue(lines.stream().anyMatch(s -> s.contains("[50% |")), "Round 2 dazed orc still has 50% hit chance: " + lines);
        assertFalse(orc.has(Tag.DAZED));
        assertEquals(0, orc.dazedTurns);

        lines.clear();
        SimulationSystem.tick(w, bus);
        assertTrue(lines.stream().anyMatch(s -> s.contains("[70% |")), "Round 3 recovered orc returns to 70% hit chance: " + lines);
    }

    @Test
    void burnAndBleedDotsTickAtStartOfEntityTurnBeforeAttacking() {
        World w = new World(10, 10, new Dice.Always(true));
        w.installThrall(new Thrall(new Vec2(2, 2), 40));

        Actor burningOrc = new Actor("o1", "BurnOrc", 'O', new Vec2(3, 2), new CreatureStats(Balance.BURN_TICK_DMG, 0, 0));
        burningOrc.applyTag(Tag.BURNING);
        burningOrc.temp = Balance.BURN_CAP;
        w.add(burningOrc);

        Actor bleedingRat = new Actor("r1", "BleedRat", 'r', new Vec2(1, 2), new CreatureStats(Balance.BLEED_DMG, 0, 0));
        bleedingRat.applyTag(Tag.BLEEDING);
        bleedingRat.bleedTurns = Balance.BLEED_TURNS;
        w.add(bleedingRat);

        SimulationSystem.tick(w, new EventBus());

        assertFalse(burningOrc.alive(), "Burning orc should die at start of its turn before attacking");
        assertFalse(bleedingRat.alive(), "Bleeding rat should die at start of its turn before attacking");
        assertEquals(40, w.thrall().hp(), "Entities killed by start-of-turn DoTs must not hit Thrall first");
    }

    @Test
    void acidFloorAndTarSlowTickAtStartOfEntityTurn() {
        World w = new World(10, 10, new Dice.Always(true));
        w.installThrall(new Thrall(new Vec2(2, 2), 40));

        // Acid floor kills low-HP adjacent orc at start of its turn before it can attack or escape
        Actor acidOrc = new Actor("o1", "AcidOrc", 'O', new Vec2(3, 2), new CreatureStats(Balance.CAUSTIC_DMG, 2, 0));
        w.add(acidOrc);
        ThermalState.setAcidPool(w, new Vec2(3, 2));

        // Tar slows orc (+1 action to step out -> spends all 2 actions escaping, cannot guard/attack)
        Actor tarOrc = new Actor("o2", "TarOrc", 'O', new Vec2(2, 4), new CreatureStats(20, 2, 0));
        w.add(tarOrc);
        ThermalState.setTar(w, new Vec2(2, 4));

        AiSystem.tick(w, new EventBus());
        assertFalse(acidOrc.alive(), "Acid floor should kill orc at start of turn before attacking");
        assertEquals(40, w.thrall().hp());
        assertEquals(20 - Balance.TAR_DMG, tarOrc.hp());
        assertEquals(2, tarOrc.armor(), "Tar does not corrode armor");
        assertFalse(tarOrc.has(Tag.CORRODED));
        assertEquals(new Vec2(2, 3), tarOrc.pos(), "Tar orc spends 2 actions stepping out");
        assertFalse(tarOrc.isGuarded(), "Escaping tar leaves 0 actions for guard or attack");
    }
}
