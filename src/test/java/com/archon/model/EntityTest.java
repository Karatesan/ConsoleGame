package com.archon.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    void testApplyDamageAndAlive() {
        Entity entity = new Entity("test", "Test Dummy", 'D', Entity.Kind.CREATURE, new Vec2(0, 0), 20);
        assertTrue(entity.alive());

        int remaining = entity.takeDamage(5);
        assertEquals(15, remaining);
        assertEquals(15, entity.hp);
        assertTrue(entity.alive());

        entity.takeDamage(15);
        assertEquals(0, entity.hp);
        assertFalse(entity.alive());
    }

    @Test
    void testIgniteAndExtinguish() {
        Entity entity = new Entity("test", "Test Dummy", 'D', Entity.Kind.CREATURE, new Vec2(0, 0), 20);
        assertFalse(entity.has(Tag.BURNING));

        entity.ignite();
        assertTrue(entity.has(Tag.BURNING));

        entity.extinguish();
        assertFalse(entity.has(Tag.BURNING));
    }

    @Test
    void testDisarm() {
        Entity entity = new Entity("test", "Test Dummy", 'D', Entity.Kind.CREATURE, new Vec2(0, 0), 20);
        Item sword = Item.weapon("blade", "Iron Blade", 5, 10, Tag.METAL);
        entity.held = sword;

        Item disarmed = entity.disarm();
        assertEquals(sword, disarmed);
        assertNull(entity.held);

        // Disarming when holding nothing returns null
        assertNull(entity.disarm());
    }

    @Test
    void testApplyAndRemoveTag() {
        Entity entity = new Entity("test", "Test Dummy", 'D', Entity.Kind.CREATURE, new Vec2(0, 0), 20);
        entity.applyTag(Tag.FLAMMABLE);
        assertTrue(entity.has(Tag.FLAMMABLE));

        entity.removeTag(Tag.FLAMMABLE);
        assertFalse(entity.has(Tag.FLAMMABLE));
    }
}
