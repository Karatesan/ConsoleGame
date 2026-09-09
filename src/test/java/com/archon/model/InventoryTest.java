package com.archon.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void testEquipmentSlotsInitialized() {
        Inventory inv = new Inventory();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            assertTrue(inv.equipment().containsKey(slot));
            assertNull(inv.getSlot(slot));
            assertNull(inv.getSlot(slot.path));
        }

        Item dagger = Item.weapon("dagger", "Iron Dagger", 4, 10, Tag.METAL);
        inv.setSlot(EquipmentSlot.HAND_RIGHT, dagger);
        assertEquals(dagger, inv.getSlot(EquipmentSlot.HAND_RIGHT));
        assertEquals(dagger, inv.getSlot("hand/right"));
    }

    @Test
    void testEquipFromPackAndSwap() {
        Inventory inv = new Inventory();
        Item sword = Item.weapon("sword", "Sword", 5, 10, Tag.METAL);
        Item shield = Item.weapon("shield", "Shield", 1, 10, Tag.WOOD);

        inv.addToPack(sword);
        inv.addToPack(shield);

        // Equip sword to right hand
        assertTrue(inv.equipFromPack(sword, EquipmentSlot.HAND_RIGHT));
        assertEquals(sword, inv.getSlot(EquipmentSlot.HAND_RIGHT));
        assertFalse(inv.pack().contains(sword));

        // Swap sword with shield
        assertTrue(inv.equipFromPack(shield, EquipmentSlot.HAND_RIGHT));
        assertEquals(shield, inv.getSlot(EquipmentSlot.HAND_RIGHT));
        assertFalse(inv.pack().contains(shield));
        assertTrue(inv.pack().contains(sword));

        // Unequip shield to pack
        assertTrue(inv.unequipToPack(EquipmentSlot.HAND_RIGHT));
        assertNull(inv.getSlot(EquipmentSlot.HAND_RIGHT));
        assertTrue(inv.pack().contains(shield));
    }

    @Test
    void testUnequipItemDirectly() {
        Inventory inv = new Inventory();
        Item sword = Item.weapon("sword", "Sword", 5, 10, Tag.METAL);
        inv.setSlot(EquipmentSlot.HAND_LEFT, sword);

        assertTrue(inv.unequipItem(sword));
        assertNull(inv.getSlot(EquipmentSlot.HAND_LEFT));
        assertFalse(inv.unequipItem(sword)); // already unequipped
    }

    @Test
    void testPackCapacityAndAddition() {
        Inventory inv = new Inventory();
        assertFalse(inv.isPackFull());

        for (int i = 0; i < Inventory.PACK_MAX; i++) {
            Item item = Item.flask("flask_" + i, "Flask " + i, Tag.WATER);
            assertTrue(inv.addToPack(item));
        }

        assertTrue(inv.isPackFull());
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