package com.archon.verb;

import com.archon.app.DefaultVerbs;
import com.archon.event.EventBus;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.Interrupts;
import com.archon.exec.RoundState;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.Door;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.physics.ThermalState;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TelekinesisTest {

    private static World newWorld(Dice dice) {
        World w = new World(14, 10, dice);
        for (int x = 0; x < w.width(); x++) {
            w.map().setWall(new Vec2(x, 0), true);
            w.map().setWall(new Vec2(x, w.height() - 1), true);
        }
        for (int y = 0; y < w.height(); y++) {
            w.map().setWall(new Vec2(0, y), true);
            w.map().setWall(new Vec2(w.width() - 1, y), true);
        }
        w.installThrall(new Thrall(new Vec2(3, 5), 40));
        return w;
    }

    private static Executor exec(World w) {
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        return new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));
    }

    @Test
    void forceVsResistanceWallSlamEntitySlamAndAgainstWallRule() {
        World w = newWorld(new Dice.Always(false));
        Executor ex = exec(w);

        // Creature Str 2 -> res 1; Magic 3 -> moves up to 2 tiles; hits blocker at (6,5) after 1 tile travel -> entity slam!
        Actor goblin = new Actor("g1", "Goblin", 'g', new Vec2(4, 5), new CreatureStats(20, 3, 0, 2, 1, 1.0));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(6, 5), new CreatureStats(20, 2, 0, 4, 1, 1.0));
        w.add(goblin);
        w.add(orc);

        ex.submit("push g1 2");
        assertEquals(new Vec2(5, 5), goblin.pos());
        assertEquals(20 - Balance.FORCE_COLLISION_DMG, goblin.hp(), "Slam ignores armor");
        assertEquals(20 - Balance.FORCE_COLLISION_DMG, orc.hp(), "Entity slam hits both");
        assertTrue(goblin.has(Tag.PRONE));
        assertTrue(orc.has(Tag.PRONE));

        // Target already against wall (x=7 next to wall at x=8) takes 0 slam damage
        Actor corner = new Actor("c1", "Corner", 'C', new Vec2(7, 5), new CreatureStats(20, 0, 0, 2, 1, 1.0));
        w.map().setWall(new Vec2(8, 5), true);
        w.add(corner);
        ex.submit("pass");
        // Restore corner/thrall positions after AI phase and push corner against wall
        corner.moveTo(new Vec2(7, 5));
        w.thrall().moveTo(new Vec2(6, 5));
        ex.submit("push c1 2");
        assertEquals(new Vec2(7, 5), corner.pos());
        assertEquals(20, corner.hp(), "Must travel >= SLAM_MIN_TRAVEL before slam applies");
        assertFalse(corner.has(Tag.PRONE));

        // Wood crate (RES_MEDIUM 2) pushed into wall shatters; heavy door (RES_HEAVY) stays put
        ex.submit("pass");
        w.thrall().moveTo(new Vec2(3, 5));
        Prop crate = new Prop("cr1", "Crate", 'c', new Vec2(3, 7), 8);
        crate.with(Tag.WOOD, Tag.BREAKABLE, Tag.FLAMMABLE);
        w.add(crate);
        ex.submit("channel -f | push cr1 3"); // Force = 5, Res = 2 -> moves 3 tiles -> hits wall at y=9 -> shatters
        assertFalse(crate.alive());

        Door door = new Door("d1", "Oak Door", '+', new Vec2(3, 3), 30, 10);
        w.add(door);
        ex.submit("push d1 3");
        assertEquals(new Vec2(3, 3), door.pos());
    }

    @Test
    void pullStopsBeforeCasterSlipHaltsMovementAndPullCatchesItems() {
        World w = newWorld(new Dice.Always(true));
        Executor ex = exec(w);

        // Pull target stops before caster; slip mid-move halts displacement
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(7, 5), new CreatureStats(20, 0, 0, 0, 1, 1.0));
        w.add(orc);
        ThermalState.setOilSlick(w, new Vec2(6, 5));
        ex.submit("channel -f | pull o1 5");
        assertEquals(new Vec2(6, 5), orc.pos(), "Slipping PRONE on tile 1 halts remaining pull");
        assertTrue(orc.has(Tag.PRONE));

        // Loose item pull catch vs push rejection
        Item stone = new Item("stone", "throwing stone", Set.of(Tag.STONE), null, 1, 2);
        w.map().placeGroundItem(new Vec2(3, 3), stone); // distance 2 <= Magic(3) - RES_LIGHT(1) = 2
        ex.submit("push @3,3/stone 2");
        assertTrue(w.tile(new Vec2(3, 3)).ground().contains(stone), "Push must not launch loose items");

        ex.submit("pass");
        ex.submit("pull @3,3/stone");
        assertTrue(w.tile(new Vec2(3, 3)).ground().isEmpty());
        assertTrue(w.thrall().inventory().pack().contains(stone));
    }

    @Test
    void gasAndLiquidPushRelocatesWithWallSplashAndLayerDisambiguation() {
        World w = newWorld(new Dice.Always(false));
        Executor ex = exec(w);

        // Water under Steam at (3,3): @3,3/gas moves only Steam; Water puddle stays on floor
        ThermalState.setWaterPuddle(w, new Vec2(3, 3));
        ThermalState.spawnGas(w, new Vec2(3, 3), Tag.STEAM);
        ex.submit("push @3,3/gas 3");
        assertFalse(w.tile(new Vec2(3, 3)).has(Tag.STEAM));
        assertTrue(w.tile(new Vec2(3, 3)).has(Tag.WATER));
        assertTrue(w.tile(new Vec2(2, 1)).has(Tag.STEAM));
        assertTrue(w.tile(new Vec2(4, 1)).has(Tag.STEAM));
    }
}
