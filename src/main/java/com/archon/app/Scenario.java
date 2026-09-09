package com.archon.app;

import com.archon.model.*;

import java.util.Set;

/** The single hand-built test chamber. Layout is fixed so acceptance tests stay stable. */
public final class Scenario {

    public static World testRoom(Dice dice) {
        World w = new World(12, 8, dice);

        for (int x = 0; x < w.w; x++) { w.wall(x, 0); w.wall(x, w.h - 1); }
        for (int y = 0; y < w.h; y++) { w.wall(0, y); w.wall(w.w - 1, y); }
        w.wall(3, 3);   // pillar directly north of the thrall — used by the BLOCKED tests

        Thrall t = new Thrall(new Vec2(3, 4), 40);
        t.inventory().setSlot(EquipmentSlot.HAND_RIGHT, Item.weapon("rusted_cleaver", "rusted cleaver", 6, 10, Tag.METAL));
        t.inventory().setSlot(EquipmentSlot.HAND_LEFT,  new Item("torch", "torch", Set.of(Tag.WOOD, Tag.LIT), null, 20, 1));
        t.inventory().addToPack(Item.flask("flask_oil", "oil flask", Tag.OIL));
        t.inventory().addToPack(Item.flask("flask_water", "water flask", Tag.WATER));
        w.thrall = t;

        Entity orc = new Entity("o1", "Orc Guard", 'O', Entity.Kind.CREATURE, new Vec2(3, 5), 24);
        orc.armor = 3; orc.evasion = 5;
        orc.held = Item.weapon("iron_sword", "iron sword", 7, 12, Tag.METAL);
        orc.with(Tag.ORGANIC, Tag.FLESH, Tag.FLAMMABLE);
        w.add(orc);

        Entity archer = new Entity("g1", "Goblin Archer", 'G', Entity.Kind.CREATURE, new Vec2(8, 4), 12);
        archer.evasion = 10;
        archer.with(Tag.ORGANIC, Tag.FLESH, Tag.FLAMMABLE)
                .ready(Entity.Trigger.ON_MOVEMENT_IN_LOS, "fires on movement in line of sight", 6);
        w.add(archer);

        Entity barrel = new Entity("b1", "Oil Barrel", 'B', Entity.Kind.PROP, new Vec2(2, 5), 8);
        barrel.with(Tag.WOOD, Tag.CONTAINER, Tag.FLAMMABLE, Tag.BREAKABLE);
        barrel.held = new Item("oil", "oil", Set.of(Tag.LIQUID, Tag.OIL, Tag.FLAMMABLE), Tag.OIL, 1, 0);
        w.add(barrel);

        Entity brazier = new Entity("br1", "Brazier", 'i', Entity.Kind.PROP, new Vec2(4, 5), 10);
        brazier.with(Tag.METAL, Tag.LIT);
        w.add(brazier);

        Entity door = new Entity("d1", "Oak Door", '+', Entity.Kind.DOOR, new Vec2(2, 3), 30);
        door.armor = 2;
        door.with(Tag.WOOD, Tag.BREAKABLE, Tag.SOLID, Tag.FLAMMABLE);
        w.add(door);

        return w;
    }
}