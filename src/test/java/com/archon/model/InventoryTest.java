package com.archon.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void testEquipmentSlotsInitialized() {
        Inventory inv = new Inventory();
        for (String slot : Inventory.SLOTS) {
            assertTrue(inv.equipment().containsKey(slot));
            assertNull(inv.getSlot(slot));
        }

        Item dagger = Item.weapon("dagger", "Iron Dagger", 4, 10, Tag.METAL);
        inv.setSlot("hand/right", dagger);
        assertEquals(dagger, inv.getSlot("hand/right"));
    }

    @Test
    void testPackCapacityAndAddition() {
        Inventory inv = new Inventory();
        assertFalse(inv.isFull());

        for (int i = 0; i < Inventory.PACK_MAX; i++) {
            Item item = Item.flask("flask_" + i, "Flask " + i, Tag.WATER);
            assertTrue(inv.addToPack(item));
        }

        assertTrue(inv.isFull());
        Item extra = Item.flask("extra", "Extra Flask", Tag.OIL);
        assertFalse(inv.addToPack(extra));
    }

    @Test
    void testPackRemovalAndFind() {
        Inventory inv = new Inventory();
        Item potion = Item.flask("flask_oil", "Oil Flask", Tag.OIL);
        inv.addToPack(potion);

        Optional<Item> foundById = inv.find("flask_oil");
        assertTrue(foundById.isPresent());
        assertEquals(potion, foundById.get());

        Optional<Item> foundByName = inv.find("Oil Flask");
        assertTrue(foundByName.isPresent());
        assertEquals(potion, foundByName.get());

        assertEquals(potion, inv.findInPack("flask_oil"));

        assertTrue(inv.removeFromPack(potion));
        assertFalse(inv.find("flask_oil").isPresent());
    }
}
