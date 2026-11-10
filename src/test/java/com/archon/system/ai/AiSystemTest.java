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
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.EquipmentSlot;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.CombatEngine;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.Ignition;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.environment.Substances;
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
        assertEquals(1, orc.pos().chebyshev(world.thrall().pos()),
                "Orc should route around the (3,3) wall in 2 steps to reach adjacency with (3,2)");
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

        // Pour oil on Thrall's current tile (3,4), then step out of it
        Substances.spill(world, new Vec2(3, 4), Tag.OIL);
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

        // Cleaver max 8 + str 3 = 11; head 2.5x = 27.5; crit 2x = 55; minus orc armor 3 = 52 dmg
        var res = CombatEngine.resolveMelee(alwaysMaxCrit, world, world.thrall(), orc, BodyPart.HEAD, "normal", false);
        assertTrue(res.hit());
        assertTrue(res.crit(), "Should trigger critical hit");
        assertEquals(52, res.damage(), "Crit multiplier (2x) should apply after head multiplier (2.5x)");
    }

    @Test
    void waterExtinguishesFireAndWetPreventsIgnition() {
        World world = Scenario.testRoom(new Dice.Seeded(1L));
        Actor orc = world.actor("o1");
        orc.ignite();
        world.map().addTag(orc.pos(), Tag.BURNING);

        Substances.spill(world, orc.pos(), Tag.WATER);
        assertFalse(orc.has(Tag.BURNING), "Water should extinguish burning occupant");
        assertFalse(world.tile(orc.pos()).has(Tag.BURNING), "Water should extinguish burning tile");
        assertTrue(orc.has(Tag.WET), "Water should apply WET tag");

        assertFalse(Ignition.entity(orc), "WET entity should resist ignition");
        assertFalse(orc.has(Tag.BURNING));
        assertFalse(orc.has(Tag.WET), "Resisting ignition should evaporate WET");
    }
}
