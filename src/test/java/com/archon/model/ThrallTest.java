package com.archon.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThrallTest {

    @Test
    void testThrallSlotsAndMainHand() {
        Thrall thrall = new Thrall(new Vec2(0, 0), 40);

        assertNull(thrall.mainHand());

        Item leftDagger = Item.weapon("dagger", "Dagger", 3, 10, Tag.METAL);
        Item rightSword = Item.weapon("sword", "Sword", 5, 10, Tag.METAL);

        // Equip left hand only via inventory()
        thrall.inventory().setSlot(EquipmentSlot.HAND_LEFT, leftDagger);
        assertEquals(leftDagger, thrall.inventory().getSlot(EquipmentSlot.HAND_LEFT));
        assertEquals(leftDagger, thrall.inventory().getSlot("hand/left"));
        assertEquals(leftDagger, thrall.mainHand());

        // Equip right hand, mainHand prefers right hand
        thrall.inventory().setSlot(EquipmentSlot.HAND_RIGHT, rightSword);
        assertEquals(rightSword, thrall.inventory().getSlot(EquipmentSlot.HAND_RIGHT));
        assertEquals(rightSword, thrall.inventory().getSlot("hand/right"));
        assertEquals(rightSword, thrall.mainHand());
    }

    @Test
    void testThrallPackOperations() {
        Thrall thrall = new Thrall(new Vec2(0, 0), 40);
        assertFalse(thrall.inventory().isPackFull());

        Item flask = Item.flask("flask", "Flask", Tag.WATER);
        thrall.inventory().addToPack(flask);

        assertEquals(flask, thrall.inventory().findInPack("flask"));
        assertEquals(1, thrall.inventory().pack().size());
    }
}
