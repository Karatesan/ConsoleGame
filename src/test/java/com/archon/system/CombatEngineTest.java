package com.archon.system;

import com.archon.app.Scenario;
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.World;
import com.archon.system.combat.CombatEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatEngineTest {

    @Test
    void testResolveMeleeHit() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Entity target = world.get("o1");
        int initialHp = target.hp;

        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                world.dice,
                world,
                world.thrall,
                target,
                BodyPart.TORSO,
                "normal",
                false
        );

        assertTrue(result.hit());
        assertTrue(result.damage() > 0);
        assertEquals(initialHp - result.damage(), target.hp);
        assertEquals(BodyPart.TORSO, result.part());
    }

    @Test
    void testResolveMeleeMiss() {
        World world = Scenario.testRoom(new Dice.Always(false));
        Entity target = world.get("o1");
        int initialHp = target.hp;

        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                world.dice,
                world,
                world.thrall,
                target,
                BodyPart.TORSO,
                "normal",
                false
        );

        assertFalse(result.hit());
        assertEquals(0, result.damage());
        assertEquals(initialHp, target.hp);
    }

    @Test
    void testResolveMeleeKillDropsHeldWeapon() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Entity target = world.get("o1");
        target.hp = 1;
        assertNotNull(target.held);
        Item heldItem = target.held;

        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                world.dice,
                world,
                world.thrall,
                target,
                BodyPart.HEAD,
                "heavy",
                false
        );

        assertTrue(result.killed());
        assertNull(target.held);
        assertTrue(world.tile(target.pos).ground.contains(heldItem));
    }

    @Test
    void testAttemptDisarmSuccess() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Entity target = world.get("o1");
        assertNotNull(target.held);
        Item heldItem = target.held;

        CombatEngine.DisarmResult result = CombatEngine.attemptDisarm(world.dice, world, target);

        assertTrue(result.success());
        assertEquals(heldItem, result.weapon());
        assertNull(target.held);
        assertTrue(world.tile(target.pos).ground.contains(heldItem));
    }

    @Test
    void testResolveRangedHitAndMiss() {
        World hitWorld = Scenario.testRoom(new Dice.Always(true));
        Entity target = hitWorld.get("o1");
        int initialHp = target.hp;

        CombatEngine.RangedHitResult hitResult = CombatEngine.resolveRanged(
                hitWorld.dice,
                hitWorld,
                hitWorld.thrall,
                target,
                BodyPart.TORSO
        );

        assertTrue(hitResult.hit());
        assertTrue(hitResult.damage() > 0);
        assertEquals(initialHp - hitResult.damage(), target.hp);

        World missWorld = Scenario.testRoom(new Dice.Always(false));
        Entity missTarget = missWorld.get("o1");
        int missInitialHp = missTarget.hp;

        CombatEngine.RangedHitResult missResult = CombatEngine.resolveRanged(
                missWorld.dice,
                missWorld,
                missWorld.thrall,
                missTarget,
                BodyPart.TORSO
        );

        assertFalse(missResult.hit());
        assertEquals(0, missResult.damage());
        assertEquals(missInitialHp, missTarget.hp);
    }
}
