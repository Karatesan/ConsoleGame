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

import java.util.Set;

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
                Item.weapon("rusted_cleaver", "rusted cleaver", 6, 10, Tag.METAL));
        t.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_LEFT,
                new Item("torch", "torch", Set.of(Tag.WOOD, Tag.LIT), null, 20, 1));
        t.inventory().addToPack(Item.flask("flask_oil", "oil flask", Tag.OIL));
        t.inventory().addToPack(Item.flask("flask_water", "water flask", Tag.WATER));
        world.installThrall(t);

        Actor orc = new Actor("o1", "Orc Guard", 'O', new Vec2(3, 5), new CreatureStats(24, 3, 5));
        orc.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("iron_sword", "iron sword", 7, 12, Tag.METAL));
        orc.with(Tag.ORGANIC, Tag.FLESH, Tag.FLAMMABLE);
        world.add(orc);

        Actor archer =
                new Actor("g1", "Goblin Archer", 'G', new Vec2(8, 4), new CreatureStats(12, 0, 10));
        archer.with(Tag.ORGANIC, Tag.FLESH, Tag.FLAMMABLE);
        archer.ready(Actor.Trigger.ON_MOVEMENT_IN_LOS, "fires on movement in line of sight", 6);
        world.add(archer);

        Prop barrel = new Prop("b1", "Oil Barrel", 'B', new Vec2(2, 5), 8);
        barrel.with(Tag.WOOD, Tag.CONTAINER, Tag.FLAMMABLE, Tag.BREAKABLE);
        barrel.setContents(
                new Item(
                        "oil",
                        "oil",
                        Set.of(Tag.LIQUID, Tag.OIL, Tag.FLAMMABLE),
                        Tag.OIL,
                        1,
                        0));
        world.add(barrel);

        Prop brazier = new Prop("br1", "Brazier", 'i', new Vec2(4, 5), 10);
        brazier.with(Tag.METAL, Tag.LIT);
        world.add(brazier);

        Door door = new Door("d1", "Oak Door", '+', new Vec2(2, 3), 30, 10);
        door.setArmor(2);
        door.with(Tag.WOOD, Tag.BREAKABLE, Tag.SOLID, Tag.FLAMMABLE);
        world.add(door);

        return world;
    }
}
