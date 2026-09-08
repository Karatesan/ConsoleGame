package com.archon.system;

import com.archon.app.Scenario;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReactionSystemTest {

    @Test
    void testAdjacencyInterruptFiresWhenNear() {
        World world = Scenario.testRoom(new Dice.Always(true));
        Entity guard = world.get("o1"); // pos (3,5), thrall at (3,4) -> distance 1
        guard.ready(Entity.Trigger.ON_ADJACENCY, "strikes adjacent targets", 5);

        Entity interrupt = ReactionSystem.pendingInterrupt(world, false);
        assertEquals(guard, interrupt);

        int damage = ReactionSystem.resolveInterrupt(world, guard);
        assertEquals(5, damage);
        assertTrue(guard.readiedSpent);
        assertEquals(35, world.thrall.hp);

        // Once spent, should no longer trigger
        assertNull(ReactionSystem.pendingInterrupt(world, false));
    }

    @Test
    void testMovementInLosInterruptRequiresMovement() {
        World world = Scenario.testRoom(new Dice.Always(true));
        // g1 is Goblin Archer at (8,4) with ON_MOVEMENT_IN_LOS
        Entity archer = world.get("g1");
        assertNotNull(archer.readied);
        assertEquals(Entity.Trigger.ON_MOVEMENT_IN_LOS, archer.readied.trigger());

        // Without movement, pendingInterrupt should be null
        assertNull(ReactionSystem.pendingInterrupt(world, false));

        // With movement, archer triggers
        Entity interrupt = ReactionSystem.pendingInterrupt(world, true);
        assertEquals(archer, interrupt);

        int damage = ReactionSystem.resolveInterrupt(world, archer);
        assertEquals(6, damage);
        assertTrue(archer.readiedSpent);
        assertEquals(34, world.thrall.hp);
    }
}
