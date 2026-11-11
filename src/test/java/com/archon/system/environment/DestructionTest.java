package com.archon.system.environment;

import com.archon.app.DefaultVerbs;
import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.Interrupts;
import com.archon.exec.RoundState;
import com.archon.model.Actor;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.verb.VerbCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DestructionTest {

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(10, 10, new Dice.Seeded(42L));
        world.installThrall(new Thrall(new Vec2(1, 1), 40));
    }

    @Test
    void oilBarrelSpillsOilOnDestruction() {
        Vec2 pos = new Vec2(2, 2);
        Prop barrel = new Prop("b1", "Oil Barrel", 'B', pos, 8);
        barrel.with(Tag.WOOD, Tag.CONTAINER, Tag.FLAMMABLE, Tag.BREAKABLE);
        barrel.setContents(new Item("oil", "oil", Set.of(Tag.LIQUID, Tag.OIL, Tag.FLAMMABLE), Tag.OIL, 1, 0));
        world.add(barrel);

        Destruction.Result result = Destruction.onKilled(world, barrel);

        assertNull(barrel.contents());
        assertTrue(world.tile(pos).has(Tag.LIQUID));
        assertTrue(world.tile(pos).has(Tag.OIL));
        assertTrue(world.tile(pos).has(Tag.FLAMMABLE));
        assertFalse(world.tile(pos).has(Tag.BURNING));
        assertEquals(Tag.OIL, result.spilledSubstance());
        assertTrue(result.messages().contains("Oil spills across the floor."));
    }

    @Test
    void burningOilBarrelSpillsOilAndCatchesFire() {
        Vec2 pos = new Vec2(2, 3);
        Prop barrel = new Prop("b2", "Burning Barrel", 'B', pos, 8);
        barrel.with(Tag.WOOD, Tag.CONTAINER, Tag.FLAMMABLE, Tag.BREAKABLE, Tag.BURNING);
        barrel.setContents(new Item("oil", "oil", Set.of(Tag.LIQUID, Tag.OIL, Tag.FLAMMABLE), Tag.OIL, 1, 0));
        world.add(barrel);

        Destruction.Result result = Destruction.onKilled(world, barrel);

        assertNull(barrel.contents());
        assertTrue(world.tile(pos).has(Tag.OIL));
        assertTrue(world.tile(pos).has(Tag.BURNING));
        assertTrue(result.ignitedTile());
        assertTrue(result.messages().contains("Oil spills across the floor."));
        assertTrue(result.messages().contains("Fire takes hold of the floor."));
    }

    @Test
    void litBrazierIgnitesTile() {
        Vec2 pos = new Vec2(4, 5);
        Prop brazier = new Prop("br1", "Brazier", 'i', pos, 10);
        brazier.with(Tag.METAL, Tag.LIT);
        world.add(brazier);

        Destruction.Result result = Destruction.onKilled(world, brazier);

        assertTrue(world.tile(pos).has(Tag.BURNING));
        assertTrue(result.ignitedTile());
        assertTrue(result.messages().contains("Fire takes hold of the floor."));
    }

    @Test
    void waterBarrelSpillsWaterAndExtinguishesTile() {
        Vec2 pos = new Vec2(3, 3);
        world.map().addTag(pos, Tag.BURNING);
        assertTrue(world.tile(pos).has(Tag.BURNING));

        Prop barrel = new Prop("b_water", "Water Barrel", 'B', pos, 8);
        barrel.with(Tag.WOOD, Tag.CONTAINER, Tag.BREAKABLE);
        barrel.setContents(new Item("water", "water", Set.of(Tag.LIQUID, Tag.WATER), Tag.WATER, 1, 0));
        world.add(barrel);

        Destruction.Result result = Destruction.onKilled(world, barrel);

        assertNull(barrel.contents());
        assertTrue(world.tile(pos).has(Tag.WATER));
        assertFalse(world.tile(pos).has(Tag.BURNING));
        assertEquals(Tag.WATER, result.spilledSubstance());
        assertTrue(result.messages().contains("Water spills across the floor."));
    }

    @Test
    void containerWithSolidItemPlacesItemOnGround() {
        Vec2 pos = new Vec2(5, 5);
        Prop chest = new Prop("c1", "Chest", 'C', pos, 20);
        chest.with(Tag.WOOD, Tag.CONTAINER);
        Item sword = Item.weapon("iron_sword", "iron sword", 5, 9, 12, Tag.METAL);
        chest.setContents(sword);
        world.add(chest);

        Destruction.Result result = Destruction.onKilled(world, chest);

        assertNull(chest.contents());
        assertTrue(world.tile(pos).ground().contains(sword));
        assertTrue(result.droppedItem());
        assertTrue(result.messages().contains("Something drops onto the floor."));
    }

    @Test
    void creatureDropsWeaponOnDestruction() {
        Vec2 pos = new Vec2(6, 6);
        Actor orc = new Actor("o1", "Orc", 'O', pos, new CreatureStats(20, 2, 0));
        Item axe = Item.weapon("battleaxe", "battleaxe", 6, 12, 10, Tag.METAL);
        orc.inventory().placeInSlotForSetup(EquipmentSlot.HAND_RIGHT, axe);
        world.add(orc);

        Destruction.Result result = Destruction.onKilled(world, orc);

        assertNull(orc.mainHand());
        assertTrue(world.tile(pos).ground().contains(axe));
    }

    @Test
    void strikeDestroyingBarrelAndOrcLogsDistinctMessages() {
        World testWorld = Scenario.testRoom(new Dice.Always(true)); // Always hit
        EventBus bus = new EventBus();
        List<String> narratives = new ArrayList<>();
        bus.subscribe(e -> {
            if (e instanceof GameEvent.Narrative n) {
                narratives.add(n.text());
            }
        });

        RoundState round = new RoundState(EconomyRules.prototype());
        VerbCatalog catalog = DefaultVerbs.create(round.rules());
        Interrupts interrupts = new Interrupts(() -> ReactionSystem.fire(testWorld), bus);
        Executor exec = new Executor(testWorld, bus, round, catalog, interrupts, () -> SimulationSystem.tick(testWorld, bus));

        // b1 is at (2,5), adjacent to Thrall at (3,4)
        // b1 has 8 HP. Thrall's weapon does 4-8 + strength. Let's strike it twice or lower its HP to 1.
        testWorld.get("b1").takeDamage(7); // b1 now has 1 HP

        exec.submit("strike b1");

        assertTrue(narratives.stream().anyMatch(s -> s.contains("Oil Barrel is destroyed.")),
                "Prop destruction must say 'is destroyed.' instead of 'falls.'");
        assertTrue(narratives.stream().anyMatch(s -> s.contains("Oil spills across the floor.")),
                "Should notify player that oil spilled");
        assertFalse(narratives.stream().anyMatch(s -> s.contains("Oil Barrel falls.")),
                "Props should not say 'falls.'");

        // Now test creature: o1 has 24 HP, reduce to 1 HP
        testWorld.get("o1").takeDamage(23);
        narratives.clear();

        exec.submit("strike o1");

        assertTrue(narratives.stream().anyMatch(s -> s.contains("Orc Guard falls.")),
                "Creatures should say 'falls.'");
    }
}
