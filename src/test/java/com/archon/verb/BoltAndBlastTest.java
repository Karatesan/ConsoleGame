package com.archon.verb;

import com.archon.app.DefaultVerbs;
import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.Interrupts;
import com.archon.exec.RoundState;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.ai.AiSystem;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.physics.ThermalState;
import com.archon.ui.ConsoleView;
import com.archon.ui.GdxView;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BoltAndBlastTest {

    private static Dice fixedRoll(int rollValue) {
        return new Dice() {
            @Override
            public Roll check(int percent) {
                Dice.validatePercent(percent);
                return new Roll(rollValue <= percent, rollValue, percent);
            }

            @Override
            public int between(int lo, int hi) {
                return Math.clamp(rollValue, lo, hi);
            }
        };
    }

    private static World newWorld(Dice dice) {
        World w = new World(14, 12, dice);
        w.installThrall(new Thrall(new Vec2(2, 5), 40));
        return w;
    }

    private static Executor exec(World w) {
        EventBus bus = new EventBus();
        RoundState round = new RoundState(EconomyRules.prototype());
        return new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));
    }

    @Test
    void kineticForceFocusAimedAndPipedItemBolts() {
        World w = newWorld(fixedRoll(40));
        Executor ex = exec(w);

        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(6, 5), new CreatureStats(40, 1, 0));
        w.add(orc);

        // Standalone kinetic bolt with max roll (fixedRoll(40) clamps to DMG_KIN_MAX = 5) + floor(3/2) (1) - armor (1) = 5 dmg
        ex.submit("bolt o1");
        assertEquals(40 - 5, orc.hp());

        // channel -f | bolt adds +FORCE_FOCUS_BONUS (+2): 5 + 1 + 2 - 1 = 7 dmg
        ex.submit("channel -f | bolt o1");
        assertEquals(40 - 5 - 7, orc.hp());

        // Aimed bolt -a head o1 (roll 40 <= 55 hitChance)
        ex.submit("pass");
        orc.moveTo(new Vec2(6, 5)); // restore position after AI step if needed
        int hpBeforeAimed = orc.hp();
        ex.submit("bolt -a head o1");
        assertEquals(hpBeforeAimed - 5, orc.hp());

        // Low roll variation (hit without crit, between returns lo): DMG_KIN_MIN (2) + 1 - 1 = 2 dmg; INFERNO (6 - 1) + 1 = 6 dmg
        Dice hitMinDmg = new Dice() {
            @Override public Roll check(int percent) { return new Roll(40 <= percent, 40, percent); }
            @Override public int between(int lo, int hi) { return lo; }
        };
        World wLow = newWorld(hitMinDmg);
        Executor exLow = exec(wLow);
        Actor orcLow = new Actor("o1", "Orc", 'O', new Vec2(6, 5), new CreatureStats(40, 1, 0));
        wLow.add(orcLow);
        exLow.submit("bolt o1");
        assertEquals(40 - (Balance.DMG_KIN_MIN + 1 - 1), orcLow.hp());
        Prop brLow = new Prop("br1", "Brazier", 'i', new Vec2(2, 6), 10);
        brLow.with(Tag.METAL, Tag.LIT);
        wLow.add(brLow);
        exLow.submit("siphon br1 | bolt o1"); // Inferno min (3 - 1) + 1 = 3 dmg (armor bypassed)
        assertEquals(40 - 2 - (Math.max(1, Balance.DMG_INFERNO - Balance.BOLT_DMG_VAR) + 1), orcLow.hp());

        // Piped stone from ground fractures ICE and drops stone on target tile
        ex.submit("pass");
        Vec2 icePos = new Vec2(2, 8);
        w.map().addTag(icePos, Tag.ICE);
        Item stone = new Item("stone", "throwing stone", Set.of(Tag.STONE), null, 1, 2);
        w.map().placeGroundItem(new Vec2(2, 7), stone);
        ex.submit("pull @2,7/stone | bolt @2,8");
        assertFalse(w.tile(icePos).has(Tag.ICE));
        assertTrue(w.tile(icePos).has(Tag.WATER));
        assertTrue(w.tile(icePos).ground().contains(stone));

        // Piped weapon clatters against stone prop and drops on target tile
        World w2 = newWorld(fixedRoll(40));
        Executor ex2 = exec(w2);
        Prop boulder = new Prop("r1", "Boulder", 'O', new Vec2(4, 5), 20);
        boulder.with(Tag.STONE);
        w2.add(boulder);
        Item sword = Item.weapon("sword", "iron sword", 4, 6, 5, Tag.METAL);
        w2.map().placeGroundItem(new Vec2(3, 5), sword);
        ex2.submit("pull @3,5/sword | bolt r1");
        assertEquals(20, boulder.hp());
        assertEquals(4, sword.durability());
        assertTrue(w2.tile(boulder.pos()).ground().contains(sword));

        // Piped LIT torch intercepts gas first and drops there
        ThermalState.spawnGas(w2, new Vec2(2, 3), Tag.OIL_MIST);
        Item torch = new Item("torch", "torch", Set.of(Tag.WOOD, Tag.LIT), null, 10, 1, 2);
        w2.map().placeGroundItem(new Vec2(2, 4), torch);
        ex2.submit("pass");
        ex2.submit("pull @2,4/torch | bolt @2,1");
        assertFalse(w2.tile(new Vec2(2, 3)).has(Tag.OIL_MIST));
        assertTrue(w2.tile(new Vec2(2, 3)).ground().contains(torch));
    }

    @Test
    void elementalBoltAndFlaskGasInterceptionAndMissBehavior() {
        World w = newWorld(fixedRoll(95)); // always miss creatures (hitChance 80 < 95)
        Executor ex = exec(w);

        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(6, 5), new CreatureStats(30, 0, 0));
        w.add(orc);

        // Piped flask misses creature -> still shatters on floor
        Item acidFlask = Item.flask("flask_acid", "acid flask", Tag.ACID);
        w.map().placeGroundItem(new Vec2(3, 5), acidFlask);
        ex.submit("pull @3,5/flask_acid | bolt o1");
        assertEquals(30, orc.hp());
        assertTrue(w.tile(orc.pos()).has(Tag.ACID));

        // Acid on floor triggers at start of Orc's turn before Orc escapes; then Fire bolt intercepts opaque Steam
        ex.submit("pass");
        int hpAfterAcidTurnStart = orc.hp();
        assertEquals(30 - Balance.CAUSTIC_DMG, hpAfterAcidTurnStart, "Orc takes acid pool DoT at start of its turn");
        orc.moveTo(new Vec2(6, 5));
        Prop brazier = new Prop("br1", "Brazier", 'i', new Vec2(2, 6), 10);
        brazier.with(Tag.METAL, Tag.LIT);
        w.add(brazier);
        ThermalState.spawnGas(w, new Vec2(4, 5), Tag.STEAM);
        ex.submit("siphon br1 | bolt o1");
        assertFalse(w.tile(new Vec2(4, 5)).has(Tag.STEAM), "Fire bolt should convert Steam on ray");
        assertTrue(w.tile(new Vec2(4, 5)).has(Tag.SUPERHEATED_STEAM), "Steam + Fire becomes superheated steam");
        assertEquals(hpAfterAcidTurnStart, orc.hp(), "Orc behind opaque Steam should not take direct bolt damage");

        // Subsequent bolt passes straight through transparent superheated steam and hits target
        World wPass = newWorld(fixedRoll(40));
        Executor exPass = exec(wPass);
        Actor orcHit = new Actor("o1", "Orc", 'O', new Vec2(6, 5), new CreatureStats(30, 0, 0));
        wPass.add(orcHit);
        ThermalState.spawnSuperheatedSteam(wPass, new Vec2(4, 5), false);
        exPass.submit("bolt o1");
        assertTrue(orcHit.hp() < 30, "Bolt passes straight through transparent superheated steam");
    }

    @Test
    void kineticAndElementalBlastDeviationsAndCrossRules() {
        World w = newWorld(fixedRoll(40));
        Executor ex = exec(w);

        // Kinetic blast at (3,5): caster at (2,5) excluded; light creature PRONE; heavy creature immune; crate crushed; flask shattered; ICE fractured
        Actor light = new Actor("o1", "Goblin", 'g', new Vec2(3, 5), new CreatureStats(20, 0, 0, 2, 1, 1.0));
        Actor heavy = new Actor("o2", "Ogre", 'O', new Vec2(4, 5), new CreatureStats(40, 0, 0, 6, 1, 1.0));
        Prop crate = new Prop("c1", "Crate", 'c', new Vec2(3, 4), 2);
        crate.with(Tag.WOOD, Tag.BREAKABLE, Tag.FLAMMABLE);
        w.add(light);
        w.add(heavy);
        w.add(crate);

        Vec2 southPos = new Vec2(3, 6);
        w.map().addTag(southPos, Tag.ICE);
        w.map().placeGroundItem(southPos, Item.flask("fw", "water flask", Tag.WATER));

        ex.submit("blast @3,5");
        assertFalse(w.thrall().has(Tag.PRONE), "Caster excluded from cross");
        assertEquals(40, w.thrall().hp());
        assertTrue(light.has(Tag.PRONE));
        assertFalse(heavy.has(Tag.PRONE));
        assertFalse(crate.alive());
        assertFalse(w.tile(southPos).has(Tag.ICE));
        assertTrue(w.tile(southPos).has(Tag.WATER));
        assertTrue(w.tile(southPos).ground().isEmpty(), "Fragile flask shattered");

        // Outer cross arm uses BLAST_ARM_PRONE_CHANCE (20%): roll 40 stays upright, roll 15 falls prone
        World wArmStay = newWorld(fixedRoll(40));
        Executor exArmStay = exec(wArmStay);
        Actor outerStay = new Actor("o_stay", "GoblinStay", 'g', new Vec2(4, 6), new CreatureStats(20, 0, 0, 2, 1, 1.0));
        wArmStay.add(outerStay);
        exArmStay.submit("blast @4,5"); // (4,6) is outer arm with fixedRoll(40) -> 40 > 20
        assertFalse(outerStay.has(Tag.PRONE), "Outer arm should not prone at roll 40 > 20");

        World wArmHit = newWorld(fixedRoll(15));
        Executor exArmHit = exec(wArmHit);
        Actor outerProne = new Actor("o_arm", "GoblinArm", 'g', new Vec2(4, 6), new CreatureStats(20, 0, 0, 2, 1, 1.0));
        wArmHit.add(outerProne);
        exArmHit.submit("blast @4,5"); // (4,6) is outer arm with fixedRoll(15) -> 15 <= 20
        assertTrue(outerProne.has(Tag.PRONE), "Outer arm should prone at roll 15 <= 20");

        // Elemental blast S7: Thermal Shock full dmg & Oil Mist 1-hit cap; S10: Caustic Gas outward expansion
        ex.submit("pass"); // fresh round
        Actor frozen = new Actor("o3", "Frozen", 'O', new Vec2(2, 8), new CreatureStats(30, 2, 0));
        frozen.applyTag(Tag.FROZEN);
        frozen.applyTag(Tag.STUNNED);
        frozen.temp = -5;
        w.add(frozen);
        ThermalState.spawnGas(w, new Vec2(2, 7), Tag.OIL_MIST);
        ThermalState.spawnGas(w, new Vec2(3, 7), Tag.OIL_MIST);
        Prop brazier = new Prop("br1", "Brazier", 'i', new Vec2(1, 5), 10);
        brazier.with(Tag.METAL, Tag.LIT);
        w.add(brazier);

        ex.submit("siphon br1 | blast @2,7");
        // Frozen took Thermal Shock (12) + Fuel-Air Inferno (10 + 1 = 11), 1 hit max per detonation -> total 23
        assertFalse(frozen.has(Tag.FROZEN));
        assertEquals(30 - Balance.THERMAL_SHOCK_DMG - (Balance.FUEL_AIR_DMG_INFERNO + 1), frozen.hp());

        // Inferno blast evaporates Acid pool and superheats Steam while still heating occupant
        World wElem = newWorld(fixedRoll(40));
        Executor exElem = exec(wElem);
        Vec2 acidPos = new Vec2(7, 5);
        Vec2 steamPos = new Vec2(8, 5);
        ThermalState.setAcidPool(wElem, acidPos);
        ThermalState.spawnGas(wElem, steamPos, Tag.STEAM);
        Actor orcInSteam = new Actor("o_st", "Orc", 'O', steamPos, new CreatureStats(30, 0, 0));
        wElem.add(orcInSteam);
        Prop brazier2 = new Prop("br2", "Brazier", 'i', new Vec2(1, 5), 10);
        brazier2.with(Tag.METAL, Tag.LIT);
        wElem.add(brazier2);
        exElem.submit("siphon br2 | blast @8,5");
        assertFalse(wElem.tile(acidPos).has(Tag.ACID), "Inferno blast evaporates Acid pool");
        assertFalse(wElem.tile(steamPos).has(Tag.STEAM));
        assertTrue(wElem.tile(steamPos).has(Tag.SUPERHEATED_STEAM));
        assertTrue(orcInSteam.temp >= Balance.BODY_TEMP + Balance.SUPERHEATED_STEAM_TEMP_DELTA);
    }

    @Test
    void forbiddenBloodlanceAndCrimsonNovaBendLoFAndArmorAndHealOnBleedKill() {
        World w = newWorld(fixedRoll(40));
        Executor ex = exec(w);

        // Wall / gas blocking direct LoF is ignored by bolt -b (diagonal LoS past Steam!), and any kill heals +BLOOD_KILL_HEAL
        ThermalState.spawnGas(w, new Vec2(4, 4), Tag.STEAM);
        Actor target = new Actor("o1", "Orc", 'O', new Vec2(5, 3), new CreatureStats(13, 5, 0));
        w.add(target);

        ex.submit("bolt -b o1");
        assertFalse(target.alive(), "Bloodlance ignores 5 armor and kills 13-HP target");
        // Thrall pays 8 HP, then heals +5 HP for the kill
        assertEquals(40 - Balance.HP_COST_BOLT_BLOOD + Balance.BLOOD_KILL_HEAL, w.thrall().hp());

        // Crimson Nova uses LoS (diagonal past Steam), deals full blood dmg ignoring armor, and heals per kill
        ex.submit("pass");
        Actor a1 = new Actor("a1", "Goblin1", 'g', new Vec2(5, 5), new CreatureStats(20, 4, 0));
        Actor a2 = new Actor("a2", "Goblin2", 'g', new Vec2(6, 5), new CreatureStats(10, 4, 0));
        Actor a3 = new Actor("a3", "Goblin3", 'g', new Vec2(5, 6), new CreatureStats(14, 4, 0));
        a3.applyTag(Tag.BLEEDING);
        a3.bleedTurns = 1;
        w.add(a1);
        w.add(a2);
        w.add(a3);

        int hpBeforeNova = w.thrall().hp();
        ex.submit("blast -b @5,5");
        assertEquals(20 - (Balance.DMG_BLOOD_BLAST + 1), a1.hp());
        assertTrue(a1.has(Tag.BLEEDING));
        assertFalse(a2.alive());
        assertFalse(a3.alive(), "Already-bleeding target takes +BLOOD_HEMORRHAGE_DMG");
        assertEquals(hpBeforeNova - Balance.HP_COST_BLAST_BLOOD + 2 * Balance.BLOOD_NOVA_KILL_HEAL, w.thrall().hp());

        // Crimson Nova can center on @self without harming Thrall
        ThermalState.damageEntity(w, a1, 999, false, null);
        ex.submit("pass");
        Actor adj = new Actor("a4", "Goblin4", 'g', w.thrall().pos().plus(new Vec2(1, 0)), new CreatureStats(20, 3, 0));
        w.add(adj);
        int hpBeforeSelfNova = w.thrall().hp();
        ex.submit("blast -b @self");
        assertEquals(20 - (Balance.DMG_BLOOD_BLAST + 1), adj.hp());
        assertEquals(hpBeforeSelfNova - Balance.HP_COST_BLAST_BLOOD, w.thrall().hp());
    }

    @Test
    void combatLogsEmitRollArmorDamageAndStatusTelemetryForAllSourcesAndAi() {
        List<String> narrations = new ArrayList<>();
        World w = new World(14, 12, fixedRoll(40));
        w.installThrall(new Thrall(new Vec2(2, 5), 40));
        w.thrall().inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("cleaver", "rusted cleaver", 4, 4, 5, Tag.METAL));
        EventBus bus = new EventBus();
        bus.subscribe(ev -> {
            if (ev instanceof GameEvent.Narrative n) narrations.add(n.text());
            if (ev instanceof GameEvent.ActorBeat b) narrations.addAll(b.lines());
        });
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));

        // 1. strike (melee): roll-over telemetry + armor blocked, non-crit hides crit roll
        Actor adjacent = new Actor("m1", "Guard", 'G', new Vec2(2, 4), new CreatureStats(30, 1, 0));
        w.add(adjacent);
        ex.submit("strike m1");
        String strikeOut = String.join("\n", narrations);
        assertTrue(strikeOut.contains("rusted cleaver hits Guard"));
        assertTrue(strikeOut.contains("(1 blocked)"));
        assertTrue(strikeOut.contains("[70% | roll 61 vs 31+]"));
        assertFalse(strikeOut.contains("crit"));
        ThermalState.damageEntity(w, adjacent, 999, false, null);

        // 2. bolt, bolt -a head (non-crit + T2 crit), bolt -b (armor bypassed + bleeding)
        ex.submit("pass");
        narrations.clear();
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(6, 5), new CreatureStats(80, 2, 0));
        w.add(orc);
        ex.submit("bolt o1");
        String boltOut = String.join("\n", narrations);
        assertTrue(boltOut.contains("Kinetic bolt hits Orc for "));
        assertTrue(boltOut.contains("(2 blocked)"));
        assertTrue(boltOut.contains("[80% | roll 61 vs 21+]"));
        assertFalse(boltOut.contains("crit"));

        narrations.clear();
        ex.submit("bolt -a head o1");
        String aimedOut = String.join("\n", narrations);
        assertTrue(aimedOut.contains("Kinetic bolt hits Orc's head for "));
        assertTrue(aimedOut.contains("(2 blocked)"));
        assertTrue(aimedOut.contains("[55% | roll 61 vs 46+]"));
        assertFalse(aimedOut.contains("crit"));

        // Separate crit roll test (roll=15 <= hitChance 55 & critChance 35 -> T2 crit bypasses armor + applies DAZED)
        List<String> critLogs = new ArrayList<>();
        World wc = new World(14, 12, fixedRoll(15));
        wc.installThrall(new Thrall(new Vec2(2, 5), 40));
        EventBus busC = new EventBus();
        busC.subscribe(ev -> { if (ev instanceof GameEvent.Narrative n) critLogs.add(n.text()); });
        RoundState roundC = new RoundState(EconomyRules.prototype());
        Executor exC = new Executor(wc, busC, roundC, DefaultVerbs.create(roundC.rules()),
                new Interrupts(() -> ReactionSystem.fire(wc), busC),
                () -> SimulationSystem.tick(wc, busC));
        wc.add(new Actor("c1", "Orc", 'O', new Vec2(6, 5), new CreatureStats(80, 2, 0)));
        exC.submit("bolt -a head c1");
        String critOut = String.join("\n", critLogs);
        assertTrue(critOut.contains("CRITICAL T2! Kinetic bolt hits Orc's head for "));
        assertTrue(critOut.contains("(armor bypassed)"));
        assertTrue(critOut.contains("[55% | roll 86 vs 46+ | crit 35%: 86 vs 66+]"));
        assertTrue(critOut.contains("is dazed!"));

        ex.submit("pass");
        orc.moveTo(new Vec2(6, 5));
        narrations.clear();
        ex.submit("bolt -b o1");
        String lanceOut = String.join("\n", narrations);
        assertTrue(lanceOut.contains("Bloodlance hits Orc for "));
        assertTrue(lanceOut.contains("Bloodlance drains " + Balance.HP_COST_BOLT_BLOOD + " HP from Thrall"));
        assertTrue(lanceOut.contains("(armor bypassed). [auto-hit]"));
        assertTrue(lanceOut.contains("begins bleeding"));

        // 3. blast (PRONE) and blast -b (armor bypassed + BLEEDING) (north cardinal lane)
        ex.submit("pass");
        narrations.clear();
        Actor goblin = new Actor("g1", "Goblin", 'g', new Vec2(2, 2), new CreatureStats(30, 2, 0, 2, 1, 1.0));
        w.add(goblin);
        ex.submit("blast @2,2");
        String blastOut = String.join("\n", narrations);
        assertTrue(blastOut.contains("Kinetic blast hits Goblin for "));
        assertTrue(blastOut.contains("(2 blocked). [auto-hit]"));
        assertTrue(blastOut.contains("knocked prone!"));

        ex.submit("pass");
        goblin.moveTo(new Vec2(2, 2));
        narrations.clear();
        ex.submit("blast -b @2,2");
        String novaOut = String.join("\n", narrations);
        assertTrue(novaOut.contains("Crimson Nova drains " + Balance.HP_COST_BLAST_BLOOD + " HP from Thrall"));
        assertTrue(novaOut.contains("Crimson Nova hits Goblin for " + (Balance.DMG_BLOOD_BLAST + 1) + " dmg (armor bypassed). [auto-hit]"));
        assertTrue(novaOut.contains("begins bleeding"));
        ThermalState.damageEntity(w, goblin, 999, false, null);

        // 4. push slam against wall (south lane)
        ex.submit("pass");
        narrations.clear();
        w.map().setWall(new Vec2(2, 9), true);
        Actor slamTarget = new Actor("s1", "SlamTarget", 'S', new Vec2(2, 7), new CreatureStats(30, 1, 0, 0, 1, 1.0));
        w.add(slamTarget);
        ex.submit("push s1 3");
        String slamOut = String.join("\n", narrations);
        assertTrue(slamOut.contains("Telekinetic slam hits SlamTarget for " + Balance.FORCE_COLLISION_DMG + " dmg (armor bypassed). [auto-hit]"));
        assertTrue(slamOut.contains("knocked prone!"));
        ThermalState.damageEntity(w, slamTarget, 999, false, null);
        w.map().setWall(new Vec2(2, 9), false);

        // 5. siphon frostbite + siphon -b Blood Boil + AI armor telemetry (fresh world)
        World ws = newWorld(fixedRoll(40));
        ws.thrall().setArmor(2);
        List<String> sLogs = new ArrayList<>();
        EventBus busS = new EventBus();
        busS.subscribe(ev -> {
            if (ev instanceof GameEvent.Narrative n) sLogs.add(n.text());
            if (ev instanceof GameEvent.ActorBeat b) sLogs.addAll(b.lines());
        });
        RoundState roundS = new RoundState(EconomyRules.prototype());
        Executor exS = new Executor(ws, busS, roundS, DefaultVerbs.create(roundS.rules()),
                new Interrupts(() -> ReactionSystem.fire(ws), busS),
                () -> SimulationSystem.tick(ws, busS));
        Actor sOrc = new Actor("o1", "Orc", 'O', new Vec2(3, 5), new CreatureStats(80, 2, 0));
        sOrc.inventory().placeInSlotForSetup(EquipmentSlot.HAND_RIGHT, Item.weapon("axe", "battle axe", 5, 5, 10, Tag.METAL));
        ws.add(sOrc);
        exS.submit("siphon o1");
        String frostOut = String.join("\n", sLogs);
        assertTrue(frostOut.contains("Siphon hits Orc for " + Balance.FROSTBITE_DMG + " dmg (armor bypassed). [auto-hit]"));
        assertTrue(frostOut.contains("cooled to "));

        sOrc.applyTag(Tag.BLEEDING);
        sOrc.bleedTurns = 2;
        sLogs.clear();
        exS.submit("siphon -b o1");
        String boilOut = String.join("\n", sLogs);
        assertTrue(boilOut.contains("Blood Drain drains " + Balance.HP_COST_SIPHON_BLOOD + " HP from Thrall"));
        assertTrue(boilOut.contains("Blood Drain hits Orc for "));
        assertTrue(boilOut.contains("(armor bypassed). [auto-hit]"));
        assertTrue(boilOut.contains("BLEEDING consumed"));

        // Pass triggers AI attack against armored Thrall and reports blocked armor
        sLogs.clear();
        exS.submit("pass");
        String aiOut = String.join("\n", sLogs);
        assertTrue(aiOut.contains("Orc's battle axe hits Thrall's "));
        assertTrue(aiOut.contains("(2 blocked)"));
    }

    @Test
    void viewsHandleEmptyEffectLogsCausalOrderAndSpillIgniteLogs() {
        assertThrows(NullPointerException.class, () -> new GameEvent.Narrative(null));

        World w = Scenario.magicArena(fixedRoll(40));
        EventBus bus = new EventBus();
        List<String> narrations = new ArrayList<>();
        GdxView gdxView = new GdxView(1L, true);
        ConsoleView consoleView = new ConsoleView();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream consoleOut = new PrintStream(baos);
        PrintStream oldOut = System.out;
        bus.subscribe(ev -> {
            gdxView.handle(ev);
            try {
                System.setOut(consoleOut);
                consoleView.handle(ev);
            } finally {
                System.setOut(oldOut);
            }
            if (ev instanceof GameEvent.Narrative n) narrations.add(n.text());
        });
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));

        // Regression test: `bolt r1` in magicArena with empty secondary physics log must not narrate null
        assertDoesNotThrow(() -> ex.submit("bolt r1"));
        assertTrue(narrations.stream().anyMatch(s -> s.contains("Kinetic bolt hits Cave Rat for ")));
        assertTrue(baos.toString().contains("Kinetic bolt hits Cave Rat for "));
        assertDoesNotThrow(() -> ex.submit("bolt @4,4"));
        ex.submit("pass");
        assertDoesNotThrow(() -> ex.submit("blast @5,4"));

        // Push slam causal order on a clean 14x12 room: hurled message precedes slam hit
        World pushW = newWorld(fixedRoll(40));
        List<String> pushLogs = new ArrayList<>();
        EventBus pushBus = new EventBus();
        pushBus.subscribe(ev -> {
            gdxView.handle(ev);
            if (ev instanceof GameEvent.Narrative n) pushLogs.add(n.text());
        });
        RoundState pushRound = new RoundState(EconomyRules.prototype());
        Executor pushEx = new Executor(pushW, pushBus, pushRound, DefaultVerbs.create(pushRound.rules()),
                new Interrupts(() -> ReactionSystem.fire(pushW), pushBus),
                () -> SimulationSystem.tick(pushW, pushBus));
        pushW.map().setWall(new Vec2(2, 9), true);
        Actor slamTarget = new Actor("s1", "SlamTarget", 'S', new Vec2(2, 7), new CreatureStats(30, 1, 0, 0, 1, 1.0));
        pushW.add(slamTarget);
        pushEx.submit("push s1 3");
        int hurledIdx = -1, slamIdx = -1;
        for (int i = 0; i < pushLogs.size(); i++) {
            if (pushLogs.get(i).contains("is hurled")) hurledIdx = i;
            if (pushLogs.get(i).contains("Telekinetic slam hits")) slamIdx = i;
        }
        assertTrue(hurledIdx >= 0 && slamIdx > hurledIdx, "Hurled message must precede collision slam: " + pushLogs);

        // Throw/pour/ignite emit reaction logs instead of dropping them via null log
        ex.submit("pass");
        narrations.clear();
        ex.submit("throw flask_acid r1");
        String throwOut = String.join("\n", narrations);
        assertTrue(throwOut.contains("corroded") || throwOut.contains("takes "), throwOut);

        ex.submit("pass");
        narrations.clear();
        w.thrall().applyTag(Tag.BURNING);
        w.thrall().temp = Balance.BURN_CAP;
        ex.submit("pour flask_water @self");
        assertTrue(String.join("\n", narrations).contains("extinguished"), narrations.toString());

        ex.submit("pass");
        narrations.clear();
        Vec2 thrallPos = w.thrall().pos();
        Vec2 mistPos = new Vec2(thrallPos.x() + 1, thrallPos.y());
        ThermalState.spawnGas(w, mistPos, Tag.OIL_MIST);
        ex.submit("ignite @" + mistPos.x() + "," + mistPos.y());
        assertTrue(String.join("\n", narrations).contains("Fuel-air blast hits"), narrations.toString());

        // AI hit line precedes concentration backfire line in ActorBeat
        List<GameEvent.ActorBeat> aiBeats = new ArrayList<>();
        EventBus aiBus = new EventBus();
        aiBus.subscribe(ev -> { if (ev instanceof GameEvent.ActorBeat b) aiBeats.add(b); });
        World concW = new World(10, 10, new Dice() {
            int calls;
            @Override public Roll check(int percent) {
                int r = (calls++ < 2) ? 10 : 95;
                return new Roll(r <= percent, r, percent);
            }
            @Override public int between(int lo, int hi) { return lo; }
        });
        concW.installThrall(new Thrall(new Vec2(2, 2), 40));
        concW.thrall().channeledPayload = Tag.FORCE;
        concW.thrall().channelTurns = Balance.CHANNEL_MAX_TURNS;
        Actor concOrc = new Actor("o1", "Orc", 'O', new Vec2(3, 2), new CreatureStats(20, 0, 0));
        concOrc.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT, Item.weapon("sword", "iron sword", 4, 4, 10, Tag.METAL));
        concW.add(concOrc);
        AiSystem.tick(concW, aiBus);
        assertFalse(aiBeats.isEmpty());
        List<String> lines = aiBeats.getFirst().lines();
        int hitIdx = -1, backfireIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("iron sword hits Thrall")) hitIdx = i;
            if (lines.get(i).contains("loses concentration")) backfireIdx = i;
        }
        assertTrue(hitIdx >= 0 && backfireIdx > hitIdx, "Attack line must precede concentration backfire: " + lines);
    }

    @Test
    void blindRangeRejectionsExplainBlindedLimitAndNormalRangeReportsMax() {
        List<String> narrations = new ArrayList<>();
        World w = newWorld(fixedRoll(40));
        EventBus bus = new EventBus();
        bus.subscribe(ev -> { if (ev instanceof GameEvent.Narrative n) narrations.add(n.text()); });
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));

        Actor far = new Actor("o1", "Orc", 'O', new Vec2(12, 5), new CreatureStats(20, 0, 0));
        Actor mid = new Actor("o2", "Rat", 'r', new Vec2(4, 5), new CreatureStats(20, 0, 0));
        w.add(far);
        w.add(mid);

        ex.submit("bolt o1");
        assertTrue(String.join("\n", narrations).contains("target out of bolt range (max " + Balance.BOLT_RANGE + ")"));
        narrations.clear();
        ex.submit("blast @12,5");
        assertTrue(String.join("\n", narrations).contains("epicenter out of blast range (max " + Balance.BLAST_RANGE + ")"));
        narrations.clear();
        ex.submit("push o1 1");
        assertTrue(String.join("\n", narrations).contains("target out of range (max " + Balance.PUSH_RANGE + ")"));

        w.thrall().applyTag(Tag.BLIND);
        narrations.clear();
        ex.submit("bolt o2");
        assertTrue(String.join("\n", narrations).contains("blinded — cannot bolt beyond " + Balance.BLIND_MAX_RANGE + " tile"));
        narrations.clear();
        ex.submit("blast @4,5");
        assertTrue(String.join("\n", narrations).contains("blinded — cannot blast beyond " + Balance.BLIND_MAX_RANGE + " tile"));
        narrations.clear();
        ex.submit("push o2 1");
        assertTrue(String.join("\n", narrations).contains("blinded — cannot push beyond " + Balance.BLIND_MAX_RANGE + " tile"));
        narrations.clear();
        ex.submit("pull o2 1");
        assertTrue(String.join("\n", narrations).contains("blinded — cannot pull beyond " + Balance.BLIND_MAX_RANGE + " tile"));
        narrations.clear();
        ex.submit("siphon o2");
        assertTrue(String.join("\n", narrations).contains("blinded — cannot siphon beyond " + Balance.BLIND_MAX_RANGE + " tile"));
    }

    @Test
    void bloodSpellsAutoHitAtMaxRollAndLogHpDrain() {
        List<String> narrations = new ArrayList<>();
        World w = newWorld(fixedRoll(99));
        EventBus bus = new EventBus();
        bus.subscribe(ev -> { if (ev instanceof GameEvent.Narrative n) narrations.add(n.text()); });
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));

        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(5, 5), new CreatureStats(40, 0, 50));
        w.add(orc);

        ex.submit("channel -b");
        assertTrue(String.join("\n", narrations).contains(
                "Blood Surge drains " + Balance.HP_COST_CHANNEL_BLOOD + " HP from Thrall (33/40 HP)."));

        narrations.clear();
        ex.submit("bolt -b o1");
        String hitOut = String.join("\n", narrations);
        assertTrue(hitOut.contains("Bloodlance hits Orc for "));
        assertTrue(hitOut.contains("[auto-hit]"));
        assertTrue(hitOut.contains(
                "Bloodlance drains " + Balance.HP_COST_BOLT_BLOOD + " HP from Thrall (25/40 HP)."));

        // Second bolt -b triggers hemorrhage (+BLOOD_HEMORRHAGE_DMG)
        int hpAfterFirst = orc.hp();
        narrations.clear();
        ex.submit("bolt -b o1");
        String secondOut = String.join("\n", narrations);
        assertTrue(secondOut.contains("suffers blood hemorrhage"));
        assertEquals(hpAfterFirst - (Balance.DMG_BLOOD + Balance.BLOOD_BOLT_VAR + 1 + Balance.BLOOD_HEMORRHAGE_DMG), orc.hp());

        // Casting at empty tile still logs HP drain
        ex.submit("pass");
        narrations.clear();
        ex.submit("bolt -b @6,6");
        String emptyOut = String.join("\n", narrations);
        assertTrue(emptyOut.contains("dissipates harmlessly"));
        assertTrue(emptyOut.contains("Bloodlance drains " + Balance.HP_COST_BOLT_BLOOD + " HP from Thrall"));
    }
}
