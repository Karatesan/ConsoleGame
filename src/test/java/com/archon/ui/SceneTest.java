package com.archon.ui;

import com.archon.app.DefaultVerbs;
import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.Interrupts;
import com.archon.exec.RoundState;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.physics.ThermalState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneTest {

    @Test
    void enemyPhaseEmitsBeatsAndSceneDiffSeesMovement() {
        World world = Scenario.testRoom(new Dice.Seeded(42L));
        EventBus bus = new EventBus();
        List<GameEvent.ActorBeat> beats = new ArrayList<>();
        bus.subscribe(e -> { if (e instanceof GameEvent.ActorBeat b) beats.add(b); });

        Scene before = new Scene(world);
        SimulationSystem.tick(world, bus);
        Scene after = new Scene(world);

        assertFalse(beats.isEmpty());
        assertEquals("self", after.id[0]);
        for (GameEvent.ActorBeat b : beats) {
            int i = after.indexOf(b.id());
            assertEquals(!b.from().equals(b.to()), after.moved(i, before), b.id());
        }
    }

    @Test
    void sceneCapturesMagicFieldsAndTileSurfaces() {
        World world = Scenario.magicArena(new Dice.Seeded(1L));
        world.map().addTag(new Vec2(6, 2), Tag.ICE);
        ThermalState.setAcidPool(world, new Vec2(7, 2));
        ThermalState.setToxicTar(world, new Vec2(8, 2));
        ThermalState.spawnGas(world, new Vec2(6, 3), Tag.STEAM);
        ThermalState.spawnGas(world, new Vec2(7, 3), Tag.CAUSTIC_GAS);
        ThermalState.spawnGas(world, new Vec2(8, 3), Tag.OIL_MIST);

        world.thrall().channeledPayload = Tag.FIRE;
        world.thrall().channelTurns = 2;
        world.thrall().temp = 55;
        world.thrall().applyTag(Tag.FROZEN);
        world.thrall().applyTag(Tag.STUNNED);
        world.thrall().applyTag(Tag.BLEEDING);

        Scene s = new Scene(world);
        int w = s.w;
        assertTrue(s.water[2 * w + 3]);
        assertTrue(s.ice[2 * w + 6]);
        assertTrue(s.acid[2 * w + 7]);
        assertTrue(s.toxicTar[2 * w + 8]);
        assertTrue(s.steam[3 * w + 6]);
        assertTrue(s.causticGas[3 * w + 7]);
        assertTrue(s.oilMist[3 * w + 8]);

        assertEquals(Balance.BASE_MAGIC, s.magic);
        assertEquals(Balance.CONC_BASE, s.concentration);
        assertEquals("FIRE (2t)", s.channel);
        assertEquals(55, s.temp);
        assertEquals(55, s.entityTemp[0]);
        assertTrue(s.thrallFrozen);
        assertTrue(s.thrallStunned);
        assertTrue(s.frozenEntity[0]);
        assertTrue(s.stunnedEntity[0]);
        assertTrue(s.bleedingEntity[0]);
        assertTrue(s.shortConds.contains("FROZEN"));
        assertTrue(s.shortConds.contains("BLEEDING"));

        world.thrall().cripple(BodyPart.LEGS);
        assertTrue(new Scene(world).shortConds.contains("CRIPPLED(legs)"));

        EventBus bus = new EventBus();
        List<String> statusLogs = new ArrayList<>();
        bus.subscribe(e -> { if (e instanceof GameEvent.Narrative n) statusLogs.add(n.text()); });
        RoundState round = new RoundState(new EconomyRules(6, 1, 1, 1));
        Executor exec = new Executor(world, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(world), bus),
                () -> SimulationSystem.tick(world, bus));
        ConsoleView view = new ConsoleView();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        try {
            System.setOut(new PrintStream(baos));
            view.frame(world, round);
        } finally {
            System.setOut(oldOut);
        }
        assertTrue(baos.toString().contains("CRIPPLED(legs)"));
        exec.submit("status");
        assertTrue(String.join("\n", statusLogs).contains("crippled: legs"));
    }

    @Test
    void consoleViewBloodSurgeInspectionAndManualSmokeTest() {
        Dice hitNoCrit = new Dice() {
            @Override public Roll check(int percent) { return new Roll(40 <= percent, 40, percent); }
            @Override public int between(int lo, int hi) { return lo; }
        };
        World world = Scenario.magicArena(hitNoCrit);
        EventBus bus = new EventBus();
        List<String> narrations = new ArrayList<>();
        bus.subscribe(e -> {
            if (e instanceof GameEvent.Narrative n) {
                narrations.add(n.text());
            }
        });
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor exec = new Executor(world, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(world), bus),
                () -> SimulationSystem.tick(world, bus));

        ConsoleView view = new ConsoleView();
        world.thrall().numbArmTurns = 2;
        world.thrall().numbArmPart = BodyPart.ARM_R;
        world.thrall().applyTag(Tag.NUMB_ARM);

        exec.submit("channel -b");
        assertEquals(Balance.AP_PER_ROUND + Balance.BLOOD_SURGE_AP, round.ap());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        try {
            System.setOut(new PrintStream(baos));
            assertDoesNotThrow(() -> view.frame(world, round));
        } finally {
            System.setOut(oldOut);
        }
        String rendered = baos.toString();
        assertTrue(rendered.contains((Balance.AP_PER_ROUND + Balance.BLOOD_SURGE_AP) + "/" + Balance.AP_PER_ROUND));
        assertTrue(rendered.contains("Magic 3"));
        assertTrue(rendered.contains("Temp 36C"));
        assertTrue(rendered.contains("Channel: none"));

        narrations.clear();
        exec.submit("status");
        String status = String.join("\n", narrations);
        assertTrue(status.contains("Magic 3"));
        assertTrue(status.contains("ThermalMass 1.0"));
        assertTrue(status.contains("numbArm(arm.r)=2t"));

        narrations.clear();
        exec.submit("inspect br1");
        String inspectBrazier = String.join("\n", narrations);
        assertTrue(inspectBrazier.contains("Temp " + Balance.BURN_CAP + "C"));
        assertTrue(inspectBrazier.contains("ThermalMass 1.0"));

        narrations.clear();
        exec.submit("inspect @3,2");
        assertTrue(String.join("\n", narrations).contains("temp 20C"));

        narrations.clear();
        exec.submit("scan");
        String scan = String.join("\n", narrations);
        assertTrue(scan.contains("Cave Rat"));
        assertTrue(scan.contains("36C"));

        narrations.clear();
        exec.submit("help address");
        String helpAddr = String.join("\n", narrations);
        assertTrue(helpAddr.contains("@<tile>/<item>"));
        assertTrue(helpAddr.contains("@<tile>/gas"));

        narrations.clear();
        exec.submit("help economy");
        assertTrue(String.join("\n", narrations).contains("channel -b"));

        narrations.clear();
        exec.submit("help operators");
        assertTrue(String.join("\n", narrations).contains("pass -r"));

        // Smoke test magic commands on magicArena (including combat log lines in ConsoleView)
        exec.submit("siphon br1 | channel");
        assertEquals(Tag.INFERNO, world.thrall().channeledPayload);
        baos.reset();
        try {
            System.setOut(new PrintStream(baos));
            assertDoesNotThrow(() -> view.frame(world, round));
        } finally {
            System.setOut(oldOut);
        }
        assertTrue(baos.toString().contains("Channel: INFERNO (2t)"));

        narrations.clear();
        exec.submit("channel | bolt r1");
        assertNull(world.thrall().channeledPayload);
        assertTrue(world.actor("r1").hp() < 14);
        assertTrue(world.actor("r1").has(Tag.BURNING));
        String boltLog = String.join("\n", narrations);
        assertTrue(boltLog.contains("hits Cave Rat") && boltLog.contains("dmg") && boltLog.contains("% | roll "));

        exec.submit("pull @4,4/stone | bolt r1");
        assertTrue(world.tile(new Vec2(4, 4)).ground().isEmpty());

        Vec2 orcPos = world.actor("o1").pos();
        exec.submit("blast @" + orcPos.x() + "," + orcPos.y());
        assertTrue(world.actor("o1").has(Tag.PRONE));

        exec.submit("push o1 1");
        assertEquals(orcPos.plus(new Vec2(1, 0)), world.actor("o1").pos());
        exec.submit("pass -r");
        assertTrue(world.thrall().has(Tag.PRONE));
    }
}
