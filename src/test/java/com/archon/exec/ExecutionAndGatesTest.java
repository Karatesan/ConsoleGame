package com.archon.exec;

import com.archon.address.Resolved;
import com.archon.app.DefaultVerbs;
import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.command.FlagSpec;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.physics.ThermalState;
import com.archon.verb.ApCost;
import com.archon.verb.Check;
import com.archon.verb.ExitCode;
import com.archon.verb.MaterialKind;
import com.archon.verb.Verb;
import com.archon.verb.VerbCatalog;
import com.archon.verb.VerbDefinition;
import com.archon.verb.VerbDefinition.Documentation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionAndGatesTest {

    private static VerbCatalog extendedCatalog() {
        VerbCatalog base = DefaultVerbs.create(EconomyRules.prototype());
        List<VerbDefinition> defs = new ArrayList<>();
        FlagSpec aim = new FlagSpec("aim", "a", "part", null, "Aim");
        FlagSpec thermal = new FlagSpec("thermal", "t", null, null, "Thermal");
        FlagSpec blood = new FlagSpec("blood", "b", null, null, "Blood");
        FlagSpec force = new FlagSpec("force", "f", null, null, "Force");

        Verb noop = c -> ExitCode.SUCCESS;
        Documentation doc = new Documentation("TEST", "", "stub", "stub", List.of());

        Set<String> overrideNames = Set.of("siphon", "bolt", "blast", "push", "pull", "channel");
        for (VerbDefinition def : base.all()) {
            if (!overrideNames.contains(def.name())) {
                defs.add(def);
            }
        }
        defs.add(new VerbDefinition("siphon", new com.archon.verb.SiphonVerb(), List.of(thermal, blood),
                new ApCost.Fixed(Balance.AP_SIPHON), false, Set.of(), Set.of(MaterialKind.SUBSTANCE), doc));
        defs.add(new VerbDefinition("bolt", noop, List.of(aim, blood),
                new ApCost.Fixed(Balance.AP_BOLT_KIN), false, Set.of(MaterialKind.ITEM, MaterialKind.SUBSTANCE), Set.of(), doc));
        defs.add(new VerbDefinition("blast", noop, List.of(blood),
                new ApCost.Fixed(Balance.AP_BLAST), false, Set.of(MaterialKind.SUBSTANCE), Set.of(), doc));
        defs.add(new VerbDefinition("push", noop, List.of(),
                new ApCost.Fixed(Balance.AP_PUSH), false, Set.of(MaterialKind.SUBSTANCE), Set.of(), doc));
        defs.add(new VerbDefinition("pull", noop, List.of(),
                new ApCost.Fixed(Balance.AP_PULL), false, Set.of(MaterialKind.SUBSTANCE), Set.of(MaterialKind.ITEM), doc));
        defs.add(new VerbDefinition("channel", noop, List.of(force, blood),
                new ApCost.ByPresence("force", Balance.AP_CHANNEL_THERMAL, Balance.AP_CHANNEL_FORCE),
                false, Set.of(MaterialKind.SUBSTANCE), Set.of(MaterialKind.SUBSTANCE), doc));
        return new VerbCatalog(defs);
    }

    private static World newWorld(Dice dice) {
        World w = new World(12, 10, dice);
        Thrall thrall = new Thrall(new Vec2(2, 5), 40);
        w.installThrall(thrall);
        return w;
    }

    private static Executor executor(World world, EventBus bus, RoundState round, VerbCatalog catalog) {
        Interrupts interrupts = new Interrupts(() -> ReactionSystem.fire(world), bus);
        return new Executor(world, bus, round, catalog, interrupts, () -> SimulationSystem.tick(world, bus));
    }

    @Test
    void explicitTileAndLayerItemAddressingRejectsAutomaticSelectors() {
        World w = newWorld(new Dice.Seeded(1L));

        // Automatic global selectors must all be rejected
        ThermalState.setWaterPuddle(w, new Vec2(8, 5));
        ThermalState.spawnGas(w, new Vec2(4, 2), Tag.STEAM);
        ThermalState.spawnGas(w, new Vec2(6, 2), Tag.OIL_MIST);
        ThermalState.spawnGas(w, new Vec2(7, 2), Tag.SUPERHEATED_STEAM);
        ThermalState.setAcidPool(w, new Vec2(2, 8));
        ThermalState.setOilSlick(w, new Vec2(9, 8));
        ThermalState.setTar(w, new Vec2(3, 8));
        Item stone = new Item("stone", "throwing stone", Set.of(Tag.STONE), null, 1, 2);
        Item dagger = Item.weapon("iron_dagger", "iron dagger", 3, 4, 2, Tag.METAL);
        w.map().placeGroundItem(new Vec2(1, 5), stone);
        w.map().placeGroundItem(new Vec2(4, 5), dagger);
        Prop brazier = new Prop("br1", "Brazier", 'i', new Vec2(4, 5), 10);
        brazier.with(Tag.METAL, Tag.LIT);
        w.add(brazier);

        String[] rejected = {"@water", "@steam", "@oil_mist", "@superheated_steam", "@tar",
                "@acid", "@oil", "@item", "@stone", "@iron_dagger", "@nonexistent"};
        for (int i = 0; i < rejected.length; i++) {
            assertInstanceOf(Resolved.Failure.class, Resolved.resolve(rejected[i], w), rejected[i]);
        }

        // Explicit @x,y and @dir[n] anchors with explicit /layer
        assertEquals(new Resolved.OnTile(new Vec2(8, 5)), Resolved.resolve("@8,5", w));
        assertEquals(new Resolved.OnTile(new Vec2(8, 5), "floor"), Resolved.resolve("@8,5/floor", w));
        assertEquals(new Resolved.OnTile(new Vec2(8, 5), "water"), Resolved.resolve("@8,5/water", w));
        assertEquals(new Resolved.OnTile(new Vec2(4, 2), "gas"), Resolved.resolve("@4,2/gas", w));
        assertEquals(new Resolved.OnTile(new Vec2(4, 2), "steam"), Resolved.resolve("@4,2/steam", w));
        assertEquals(new Resolved.OnTile(new Vec2(7, 2), "superheated_steam"), Resolved.resolve("@7,2/superheated", w));
        assertEquals(new Resolved.OnTile(new Vec2(3, 8), "tar"), Resolved.resolve("@3,8/tar", w));

        // Relative anchor from thrall at (2,5): @w1 = (1,5), @e2 = (4,5)
        assertEquals(new Resolved.OnItem(stone, new ItemLocation.Ground(new Vec2(1, 5))), Resolved.resolve("@1,5/stone", w));
        assertEquals(new Resolved.OnItem(stone, new ItemLocation.Ground(new Vec2(1, 5))), Resolved.resolve("@w1/stone", w));
        assertEquals(new Resolved.OnItem(dagger, new ItemLocation.Ground(new Vec2(4, 5))), Resolved.resolve("@4,5/iron_dagger", w));
        assertEquals(new Resolved.OnItem(dagger, new ItemLocation.Ground(new Vec2(4, 5))), Resolved.resolve("@br1/iron_dagger", w));
        assertEquals(new Resolved.OnItem(dagger, new ItemLocation.Ground(new Vec2(4, 5))), Resolved.resolve("@e2/dagger", w));
        assertEquals(new Resolved.OnTile(new Vec2(4, 5), "floor"), Resolved.resolve("@br1/floor", w));
        assertInstanceOf(Resolved.Failure.class, Resolved.resolve("@4,5/nonexistent_item", w));
        assertInstanceOf(Resolved.Failure.class, Resolved.resolve("@4,5/unknown_layer/extra", w));

        // @self/<ground_item>
        Item coin = new Item("coin", "gold coin", Set.of(), null, 0, 1);
        w.map().placeGroundItem(w.thrall().pos(), coin);
        assertEquals(new Resolved.OnItem(coin, new ItemLocation.Ground(w.thrall().pos())), Resolved.resolve("@self/coin", w));
    }

    @Test
    void pipelineApSummingStepModifiersAndBloodSurgeQuote() {
        World w = newWorld(new Dice.Seeded(1L));
        VerbCatalog catalog = extendedCatalog();
        CommandParser parser = new CommandParser(catalog);
        Validator validator = new Validator(w, catalog);
        RoundState round = new RoundState(EconomyRules.prototype());

        assertEquals(3, validator.stageCost(parser.parse("siphon @3,3 | bolt o1").stages().getFirst()));
        assertEquals(3, validator.stageCost(parser.parse("pull @3,4/item | bolt o1").stages().getFirst()));
        assertEquals(4, validator.stageCost(parser.parse("pull @3,4/item | bolt -a head o1").stages().getFirst()));
        assertEquals(2, validator.stageCost(parser.parse("bolt o1").stages().getFirst()));
        assertEquals(3, validator.stageCost(parser.parse("bolt -a head o1").stages().getFirst()));
        assertEquals(Balance.AP_BOLT_BLOOD, validator.stageCost(parser.parse("bolt -b o1").stages().getFirst()));
        assertEquals(3, validator.stageCost(parser.parse("blast -b @5,5").stages().getFirst()));
        assertEquals(3, validator.stageCost(parser.parse("siphon -b o1").stages().getFirst()));
        assertEquals(2, validator.stageCost(parser.parse("channel | siphon @3,3").stages().getFirst()));

        // Step modifiers: base 1 (2 with -c) + CHILLED + CRIPPLED; PRONE step stands in place for 1 AP
        Ast.Stage step = parser.parse("step e").stages().getFirst();
        Ast.Stage carefulStep = parser.parse("step e -c").stages().getFirst();
        assertEquals(1, validator.stageCost(step));
        w.thrall().applyTag(Tag.CHILLED);
        assertEquals(2, validator.stageCost(step));
        w.thrall().applyTag(Tag.CRIPPLED);
        assertEquals(3, validator.stageCost(step));
        assertEquals(4, validator.stageCost(carefulStep));
        w.thrall().applyTag(Tag.PRONE);
        assertEquals(Balance.AP_STAND_UP, validator.stageCost(step));
        assertEquals(Balance.AP_STAND_UP, validator.stageCost(carefulStep));
        assertEquals(4, validator.quote(parser.parse("step e ; step e"), round).allocation());
        w.thrall().removeTag(Tag.PRONE);
        w.thrall().removeTag(Tag.CHILLED);
        w.thrall().removeTag(Tag.CRIPPLED);

        // 9 AP line with channel -b fits within 6 AP via BLOOD_SURGE_AP (+3 AP)
        Ast.Line surgeLine = parser.parse("channel -b ; blast -b @5,5 ; siphon -b o1 ; bolt -a head o1");
        Validator.Plan plan = validator.quote(surgeLine, round);
        assertEquals(9, plan.allocation());
        assertEquals(Balance.BLOOD_SURGE_AP, plan.surgeBonus());
        assertEquals(6, plan.requiredAp());

        // channel -b breaks out of line tax even after untaxed line is spent
        RoundState taxedRound = new RoundState(EconomyRules.prototype());
        taxedRound.countLine();
        assertEquals(1, taxedRound.rules().taxForNextLine(taxedRound.linesUsed()));
        Validator.Plan soloSurge = validator.quote(parser.parse("channel -b"), taxedRound);
        assertTrue(soloSurge.free());
        assertEquals(0, soloSurge.tax());
        Validator.Plan surgeAfterLine = validator.quote(parser.parse("channel -b ; bolt o1"), taxedRound);
        assertFalse(surgeAfterLine.free());
        assertEquals(0, surgeAfterLine.tax());
        assertEquals(Balance.BLOOD_SURGE_AP, surgeAfterLine.surgeBonus());

        // Conditional channel -b must not pre-credit surgeBonus
        Validator.Plan conditionalSurge = validator.quote(parser.parse("strike o1 && channel -b ; bolt o1"), round);
        assertEquals(0, conditionalSurge.surgeBonus());

        // Second surge same round no longer grants surgeBonus
        w.thrall().bloodSurgedThisRound = true;
        Validator.Plan blockedPlan = validator.quote(surgeLine, round);
        assertEquals(0, blockedPlan.surgeBonus());
        assertEquals(9, blockedPlan.requiredAp());

        round.gain(Balance.BLOOD_SURGE_AP);
        assertEquals(Balance.AP_PER_ROUND + Balance.BLOOD_SURGE_AP, round.ap());
    }

    @Test
    void validatorEnforcesChannelPassRollAndPipeRules() {
        World w = newWorld(new Dice.Seeded(1L));
        VerbCatalog catalog = extendedCatalog();
        CommandParser parser = new CommandParser(catalog);
        Validator validator = new Validator(w, catalog);

        // Valid forms
        assertTrue(validator.validateStructure(parser.parse("pass -r ; step e")).valid());
        assertTrue(validator.validateStructure(parser.parse("channel | siphon @3,3")).valid());
        assertTrue(validator.validateStructure(parser.parse("channel | siphon @3,3 | bolt o1")).valid());
        assertTrue(validator.validateStructure(parser.parse("siphon @3,3 | channel")).valid());
        assertTrue(validator.validateStructure(parser.parse("channel -f")).valid());
        assertTrue(validator.validateStructure(parser.parse("channel -f | push o1 2")).valid());
        assertTrue(validator.validateStructure(parser.parse("channel -b")).valid());
        assertTrue(validator.validateStructure(parser.parse("pull @3,4/item | bolt -a head o1")).valid());
        assertTrue(validator.validateStructure(parser.parse("channel | bolt -a head o1")).valid());

        // Rejected forms
        assertFalse(validator.validateStructure(parser.parse("pass ; step e")).valid());
        assertFalse(validator.validateStructure(parser.parse("pass -r | step e")).valid());
        assertFalse(validator.validateStructure(parser.parse("channel")).valid());
        assertFalse(validator.validateStructure(parser.parse("channel | channel")).valid());
        assertFalse(validator.validateStructure(parser.parse("channel | siphon @3,3 | channel")).valid());
        assertFalse(validator.validateStructure(parser.parse("siphon @3,3 | channel | bolt o1")).valid());
        assertFalse(validator.validateStructure(parser.parse("take stone | channel")).valid());
        assertFalse(validator.validateStructure(parser.parse("channel -b | bolt o1")).valid());
        assertFalse(validator.validateStructure(parser.parse("channel -b -f")).valid());
        assertFalse(validator.validateStructure(parser.parse("siphon @3,3 | channel -f")).valid());
        assertFalse(validator.validateStructure(parser.parse("siphon @3,3 | bolt -b o1")).valid());
        assertFalse(validator.validateStructure(parser.parse("siphon @3,3 | push o1 2")).valid());
        assertFalse(validator.validateStructure(parser.parse("siphon @3,3 | pull @3,4/item")).valid());
        assertFalse(validator.validateStructure(parser.parse("channel -f | blast @5,5")).valid());
        assertFalse(validator.validateStructure(parser.parse("pull @3,4/item | blast @5,5")).valid());
        assertFalse(validator.validateStructure(parser.parse("bolt -b -a head o1")).valid());
        assertFalse(validator.validateStructure(parser.parse("bolt -b o1/body/head")).valid());
        assertFalse(validator.validateStructure(parser.parse("bolt -a torso o1")).valid());
        assertFalse(validator.validateStructure(parser.parse("bolt o1/body/torso")).valid());
        assertFalse(validator.validateStructure(parser.parse("siphon @3,3 | bolt -a head o1")).valid());
        assertFalse(validator.validateStructure(parser.parse("siphon @3,3 | bolt o1/body/head")).valid());
        assertFalse(validator.validateStructure(parser.parse("step ne")).valid());
        assertFalse(validator.validateStructure(parser.parse("step sw")).valid());
    }

    @Test
    void frozenAndStunnedGateBlocksPaidCommandsPassRollAndChannelBlood() {
        World w = newWorld(new Dice.Seeded(1L));
        EventBus bus = new EventBus();
        List<GameEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor exec = executor(w, bus, round, extendedCatalog());

        w.thrall().applyTag(Tag.FROZEN);
        w.thrall().applyTag(Tag.STUNNED);

        exec.submit("step e");
        assertInstanceOf(GameEvent.Rejected.class, events.getLast());
        assertEquals(new Vec2(2, 5), w.thrall().pos());

        exec.submit("pass -r");
        assertInstanceOf(GameEvent.Rejected.class, events.getLast());

        exec.submit("channel -b");
        assertInstanceOf(GameEvent.Rejected.class, events.getLast());

        events.clear();
        exec.submit("status");
        assertTrue(events.stream().noneMatch(GameEvent.Rejected.class::isInstance));
        assertEquals(1, w.roundNumber());

        exec.submit("pass");
        assertEquals(2, w.roundNumber());
    }

    @Test
    void passRollSmothersFireWithoutEndingRoundAndRejectsBurningTile() {
        World w = newWorld(new Dice.Seeded(1L));
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor exec = executor(w, bus, round, DefaultVerbs.create(round.rules()));

        // Blocked on burning floor
        ThermalState.igniteTile(w, w.thrall().pos());
        exec.submit("pass -r");
        assertFalse(w.thrall().has(Tag.PRONE));

        // Succeeds on safe floor
        w.map().removeTag(w.thrall().pos(), Tag.BURNING);
        w.thrall().applyTag(Tag.BURNING);
        w.thrall().applyTag(Tag.OIL);
        w.thrall().oilTurns = Balance.OIL_TURNS;
        w.thrall().temp = Balance.BURN_CAP;
        w.thrall().setGuarded(true);
        w.thrall().ready(Actor.Trigger.ON_ADJACENCY, "ready", 3);

        exec.submit("pass -r");
        assertTrue(w.thrall().has(Tag.PRONE));
        assertFalse(w.thrall().has(Tag.BURNING));
        assertFalse(w.thrall().has(Tag.OIL));
        assertEquals(0, w.thrall().oilTurns);
        assertEquals(Balance.BODY_TEMP, w.thrall().temp);
        assertFalse(w.thrall().isGuarded());
        assertNull(w.thrall().readied());
        assertEquals(1, w.roundNumber(), "pass -r must not end the round");

        // Non-burning CHILLED creature keeps temp and CHILLED when dropping prone with pass -r
        w.thrall().removeTag(Tag.PRONE);
        w.thrall().applyTag(Tag.CHILLED);
        w.thrall().temp = 5;
        exec.submit("pass -r");
        assertTrue(w.thrall().has(Tag.PRONE));
        assertTrue(w.thrall().has(Tag.CHILLED));
        assertEquals(5, w.thrall().temp);
    }

    @Test
    void interruptDamageTriggersConcentrationBackfireAndThrowInterceptsGas() {
        World w = newWorld(new Dice.Always(false));
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor exec = executor(w, bus, round, DefaultVerbs.create(round.rules()));

        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(3, 4), new CreatureStats(30, 0, 0));
        orc.ready(Actor.Trigger.ON_ADJACENCY, "ambush", 5);
        w.add(orc);

        w.thrall().channeledPayload = Tag.FORCE;
        w.thrall().channelTurns = Balance.CHANNEL_MAX_TURNS;

        exec.submit("step e ; guard");
        assertNull(w.thrall().channeledPayload);
        assertEquals(0, w.thrall().channelTurns);
        assertTrue(w.thrall().has(Tag.PRONE));
        assertEquals(40 - 5 - Balance.FORCE_COLLISION_DMG, w.thrall().hp());

        // ThrowVerb intercepts first gas tile on ray for flask and LIT torch
        w.thrall().removeTag(Tag.PRONE);
        Vec2 gasPos = new Vec2(5, 5);
        ThermalState.spawnGas(w, gasPos, Tag.STEAM);
        Item waterFlask = Item.flask("flask_water", "water flask", Tag.WATER);
        w.thrall().inventory().addToPack(waterFlask);
        exec.submit("throw flask_water @8,5");
        assertFalse(w.tile(gasPos).has(Tag.STEAM));
        assertTrue(w.tile(gasPos).has(Tag.WATER));
        assertFalse(w.tile(new Vec2(8, 5)).has(Tag.WATER));

        Vec2 mistPos = new Vec2(6, 5);
        ThermalState.spawnGas(w, mistPos, Tag.OIL_MIST);
        Item torch = new Item("torch", "torch", Set.of(Tag.WOOD, Tag.LIT), null, 20, 1, 2);
        w.thrall().inventory().addToPack(torch);
        exec.submit("throw torch @8,5");
        assertFalse(w.tile(mistPos).has(Tag.OIL_MIST));
        assertTrue(w.tile(mistPos).ground().contains(torch));

        // LIT torch thrown onto STEAM tile with oiled occupant extinguishes and must NOT ignite occupant
        Vec2 steamOrcPos = new Vec2(7, 5);
        Actor oiledOrc = new Actor("o2", "Oiled Orc", 'O', steamOrcPos, new CreatureStats(24, 0, 0));
        oiledOrc.applyTag(Tag.OIL);
        w.add(oiledOrc);
        ThermalState.spawnGas(w, steamOrcPos, Tag.STEAM);
        Item torch2 = new Item("torch2", "torch", Set.of(Tag.WOOD, Tag.LIT), null, 20, 1, 2);
        w.thrall().inventory().addToPack(torch2);
        exec.submit("throw torch2 @" + steamOrcPos.x() + "," + steamOrcPos.y());
        assertFalse(torch2.has(Tag.LIT));
        assertFalse(oiledOrc.has(Tag.BURNING));
    }

    @Test
    void pourRejectsNonLiquidSubstancesAndChannelBloodDoesNotTriggerTax() {
        World w = newWorld(new Dice.Seeded(1L));
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        VerbCatalog catalog = extendedCatalog();

        // PourVerb rejects piped FORCE or INFERNO
        com.archon.verb.PourVerb pour = new com.archon.verb.PourVerb();
        Ast.Invocation pourInv = new Ast.Invocation("pour", List.of("@self"), java.util.Map.of());
        assertEquals(ExitCode.BLOCKED, pour.execute(new com.archon.verb.VerbContext(
                w, pourInv, bus, new com.archon.verb.Material.OfSubstance(Tag.FORCE), round)));
        assertEquals(ExitCode.BLOCKED, pour.execute(new com.archon.verb.VerbContext(
                w, pourInv, bus, new com.archon.verb.Material.OfSubstance(Tag.INFERNO), round)));

        // Executor keeps line untaxed when channel -b runs alone or heads a paid line
        Executor exec = executor(w, bus, round, catalog);
        exec.submit("guard");
        assertEquals(1, round.linesUsed());
        assertEquals(5, round.ap());
        exec.submit("channel -b");
        assertEquals(1, round.linesUsed(), "standalone channel -b should not consume or trigger line tax");
        assertEquals(5, round.ap());
        exec.submit("channel -b ; guard");
        assertEquals(2, round.linesUsed());
        assertEquals(4, round.ap(), "channel -b line should pay 0 tax even after untaxed line is spent");
    }

    @Test
    void proneStepStandsUpInPlaceForOneApAndThenSteps() {
        World w = newWorld(new Dice.Seeded(1L));
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor exec = executor(w, bus, round, DefaultVerbs.create(round.rules()));

        w.thrall().applyTag(Tag.PRONE);
        w.thrall().applyTag(Tag.CHILLED);
        w.thrall().applyTag(Tag.CRIPPLED);
        Vec2 start = w.thrall().pos();

        exec.submit("step e ; step e");
        assertFalse(w.thrall().has(Tag.PRONE));
        assertEquals(start.plus(Vec2.dir("e")), w.thrall().pos());
        assertEquals(6 - Balance.AP_STAND_UP - (1 + Balance.CHILLED_AP_STEP + Balance.CRIPPLED_AP_STEP), round.ap());
    }

    @Test
    void strikeRequiresCardinalAdjacency() {
        World w = newWorld(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor exec = executor(w, bus, round, DefaultVerbs.create(round.rules()));

        // Thrall at (2,5), diagonal enemy at (3,4)
        Actor diag = new Actor("d1", "DiagGoblin", 'g', new Vec2(3, 4), new CreatureStats(20, 0, 0));
        w.add(diag);
        exec.submit("strike d1");
        assertEquals(20, diag.hp(), "Diagonal strike must be blocked");
        exec.submit("strike");
        assertEquals(20, diag.hp(), "Bare strike must ignore diagonal enemy");

        // Cardinal enemy at (3,5) can be struck directly and via bare strike
        Actor card = new Actor("c1", "CardGoblin", 'g', new Vec2(3, 5), new CreatureStats(20, 0, 0));
        w.add(card);
        exec.submit("strike");
        assertTrue(card.hp() < 20, "Bare strike should target sole cardinally adjacent enemy");
    }

    @Test
    void tarAddsOneApToStepOutWhileProneStandUpRemainsOneAp() {
        World w = newWorld(new Dice.Seeded(1L));
        EventBus bus = new EventBus();
        RoundState round = new RoundState(new EconomyRules(6, 1, 1, 1));
        Executor exec = executor(w, bus, round, DefaultVerbs.create(round.rules()));

        ThermalState.setTar(w, w.thrall().pos());
        w.thrall().applyTag(Tag.PRONE);

        // Stand up (1 AP, stays on TAR) + step off TAR (2 AP) + step on clear floor (1 AP) = 4 AP
        exec.submit("step e ; step e ; step e");
        assertEquals(new Vec2(4, 5), w.thrall().pos());
        assertEquals(6 - 4, round.ap());
    }
}
