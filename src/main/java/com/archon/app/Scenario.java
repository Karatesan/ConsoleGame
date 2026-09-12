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
        t.inventory().equip(EquipmentSlot.HAND_RIGHT, Item.weapon("rusted_cleaver", "rusted cleaver", 6, 10, Tag.METAL));
        t.inventory().equip(EquipmentSlot.HAND_LEFT,  new Item("torch", "torch", Set.of(Tag.WOOD, Tag.LIT), null, 20, 1));
        t.inventory().addToPack(Item.flask("flask_oil", "oil flask", Tag.OIL));
        t.inventory().addToPack(Item.flask("flask_water", "water flask", Tag.WATER));
        w.thrall = t;

        Actor orc = new Actor("o1", "Orc Guard", 'O', new Vec2(3, 5), new CreatureStats(24, 3, 5));
        orc.held = Item.weapon("iron_sword", "iron sword", 7, 12, Tag.METAL);
        orc.with(Tag.ORGANIC, Tag.FLESH, Tag.FLAMMABLE);
        w.add(orc);

        Actor archer = new Actor("g1", "Goblin Archer", 'G', new Vec2(8, 4), new CreatureStats(12, 0, 10));
        archer.with(Tag.ORGANIC, Tag.FLESH, Tag.FLAMMABLE)
                .ready(Entity.Trigger.ON_MOVEMENT_IN_LOS, "fires on movement in line of sight", 6);
        w.add(archer);

        Prop barrel = new Prop("b1", "Oil Barrel", 'B', new Vec2(2, 5), 8);
        barrel.with(Tag.WOOD, Tag.CONTAINER, Tag.FLAMMABLE, Tag.BREAKABLE);
        barrel.held = new Item("oil", "oil", Set.of(Tag.LIQUID, Tag.OIL, Tag.FLAMMABLE), Tag.OIL, 1, 0);
        w.add(barrel);

        Prop brazier = new Prop("br1", "Brazier", 'i', new Vec2(4, 5), 10);
        brazier.with(Tag.METAL, Tag.LIT);
        w.add(brazier);

        Door door = new Door("d1", "Oak Door", '+', new Vec2(2, 3), 30);
        door.armor = 2;
        door.with(Tag.WOOD, Tag.BREAKABLE, Tag.SOLID, Tag.FLAMMABLE);
        w.add(door);

        return w;
    }
}