package com.archon.app;

import com.archon.model.Actor;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.Door;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.physics.ThermalState;

/** The single hand-built test chamber. Layout is fixed so acceptance tests stay stable. */
public final class Scenario {

    public static World testRoom(Dice dice) {
        World world = new World(12, 8, dice);

        for (int x = 0; x < world.width(); x++) {
            world.map().setWall(new Vec2(x, 0), true);
            world.map().setWall(new Vec2(x, world.height() - 1), true);
        }
        for (int y = 0; y < world.height(); y++) {
            world.map().setWall(new Vec2(0, y), true);
            world.map().setWall(new Vec2(world.width() - 1, y), true);
        }
        world.map().setWall(new Vec2(3, 3), true);

        Thrall t = new Thrall(new Vec2(3, 4), 40);
        t.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("rusted_cleaver", "rusted cleaver", 4, 8, 10, Tag.METAL));
        t.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_LEFT,
                Item.torch("torch", true));
        t.inventory().addToPack(Item.flask("flask_oil", "oil flask", Tag.OIL));
        t.inventory().addToPack(Item.flask("flask_water", "water flask", Tag.WATER));
        world.installThrall(t);

        Actor orc = new Actor("o1", "Orc Guard", 'O', new Vec2(3, 5), new CreatureStats(24, 3, 5));
        orc.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("iron_sword", "iron sword", 5, 9, 12, Tag.METAL));
        orc.with(Tag.ORGANIC, Tag.FLESH);
        world.add(orc);

        Actor archer =
                new Actor("g1", "Goblin Archer", 'G', new Vec2(8, 4), new CreatureStats(12, 0, 10));
        archer.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("shortbow", "shortbow", 5, 8, 12, Tag.WOOD, Tag.RANGED, Tag.FLAMMABLE));
        archer.with(Tag.ORGANIC, Tag.FLESH);
        archer.nock();
        archer.ready(Actor.Trigger.ON_MOVEMENT_IN_LOS, "fires on movement in line of sight", 6);
        world.add(archer);

        world.add(Prop.oilBarrel("b1", new Vec2(2, 5)));
        world.add(Prop.brazier("br1", new Vec2(4, 5)));

        Door door = new Door("d1", "Oak Door", '+', new Vec2(2, 3), 30, 10);
        door.setArmor(2);
        door.with(Tag.WOOD, Tag.BREAKABLE, Tag.SOLID, Tag.FLAMMABLE);
        world.add(door);

        return world;
    }

    /** Practice arena with varied thermalMass enemies, puddles, gas sources, brazier, and loose items. */
    public static World magicArena(Dice dice) {
        World world = new World(12, 8, dice);

        for (int x = 0; x < world.width(); x++) {
            world.map().setWall(new Vec2(x, 0), true);
            world.map().setWall(new Vec2(x, world.height() - 1), true);
        }
        for (int y = 0; y < world.height(); y++) {
            world.map().setWall(new Vec2(0, y), true);
            world.map().setWall(new Vec2(world.width() - 1, y), true);
        }
        world.map().setWall(new Vec2(5, 3), true);

        Thrall t = new Thrall(new Vec2(2, 4), 60);
        t.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("rusted_cleaver", "rusted cleaver", 4, 8, 10, Tag.METAL));
        t.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_LEFT,
                Item.torch("torch", true));
        t.inventory().addToPack(Item.flask("flask_oil", "oil flask", Tag.OIL));
        t.inventory().addToPack(Item.flask("flask_water", "water flask", Tag.WATER));
        t.inventory().addToPack(Item.flask("flask_acid", "acid flask", Tag.ACID));
        t.inventory().addToPack(Item.flask("flask_water2", "water flask", Tag.WATER));
        t.inventory().addToPack(Item.flask("flask_oil2", "oil flask", Tag.OIL));
        world.installThrall(t);

        ThermalState.setWaterPuddle(world, new Vec2(3, 2));
        ThermalState.setWaterPuddle(world, new Vec2(4, 2));
        ThermalState.setWaterPuddle(world, new Vec2(3, 3));
        ThermalState.setOilSlick(world, new Vec2(3, 5));
        ThermalState.setAcidPool(world, new Vec2(1, 3));
        ThermalState.setAcidPool(world, new Vec2(4, 6));
        world.map().addTag(new Vec2(1, 6), Tag.ICE);

        world.map().placeGroundItem(new Vec2(4, 4), Item.stone("stone"));
        world.map().placeGroundItem(new Vec2(3, 4), Item.weapon("iron_dagger", "iron dagger", 3, 5, 8, Tag.METAL));
        world.map().placeGroundItem(new Vec2(1, 1), Item.flask("ground_acid", "acid flask", Tag.ACID));

        world.add(Prop.brazier("br1", new Vec2(2, 5)));
        world.add(Prop.oilBarrel("b1", new Vec2(1, 5)));
        world.add(Prop.woodCrate("c1", new Vec2(2, 3)));
        world.add(Prop.stonePillar("st1", new Vec2(2, 1)));

        Door door = new Door("d1", "Oak Door", '+', new Vec2(1, 4), 30, 10);
        door.setArmor(2);
        door.with(Tag.WOOD, Tag.BREAKABLE, Tag.SOLID, Tag.FLAMMABLE);
        world.add(door);

        Actor rat = new Actor("r1", "Cave Rat", 'r', new Vec2(5, 4), new CreatureStats(14, 0, 10, 1, 0, 2));
        rat.with(Tag.ORGANIC, Tag.FLESH);
        world.add(rat);

        Actor orc = new Actor("o1", "Orc Guard", 'O', new Vec2(7, 4), new CreatureStats(28, 3, 5, 3, 2, 1.0));
        orc.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("iron_sword", "iron sword", 5, 9, 12, Tag.METAL));
        orc.with(Tag.ORGANIC, Tag.FLESH);
        world.add(orc);

        Actor ogre = new Actor("og1", "Ogre Brute", 'G', new Vec2(9, 5), new CreatureStats(45, 2, 0, 6, 1, 1.5));
        ogre.with(Tag.ORGANIC, Tag.FLESH);
        world.add(ogre);

        Actor archer = new Actor("g1", "Goblin Archer", 'g', new Vec2(9, 2), new CreatureStats(14, 0, 10, 2, 1, 0.8));
        archer.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("shortbow", "shortbow", 5, 8, 12, Tag.WOOD, Tag.RANGED, Tag.FLAMMABLE));
        archer.with(Tag.ORGANIC, Tag.FLESH);
        archer.nock();
        world.add(archer);

        return world;
    }
}