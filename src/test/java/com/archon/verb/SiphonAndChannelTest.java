package com.archon.verb;

import com.archon.app.DefaultVerbs;
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
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.physics.ThermalState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SiphonAndChannelTest {

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

    private static World newWorld() {
        World w = new World(12, 10, fixedRoll(40));
        w.installThrall(new Thrall(new Vec2(2, 5), 40));
        return w;
    }

    private static Executor exec(World w, RoundState round) {
        EventBus bus = new EventBus();
        return new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));
    }

    @Test
    void siphonWorldSourcesTrueFirePriorityThermalSiphonAndSelfOil() {
        World w = newWorld();
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = exec(w, round);

        // True fire on burning creature takes priority over living creature -> extinguishes & promotes with channel
        Actor burningOrc = new Actor("o1", "Burning Orc", 'O', new Vec2(3, 5), new CreatureStats(30, 0, 0));
        burningOrc.applyTag(Tag.BURNING);
        burningOrc.temp = Balance.BURN_CAP;
        w.add(burningOrc);

        ex.submit("siphon o1 | channel");
        assertFalse(burningOrc.has(Tag.BURNING));
        assertEquals(Balance.BODY_TEMP, burningOrc.temp);
        assertEquals(Tag.INFERNO, w.thrall().channeledPayload);
        assertEquals(Balance.CHANNEL_MAX_TURNS, w.thrall().channelTurns);

        // Release stored Inferno into bolt
        ex.submit("channel | bolt o1");
        assertNull(w.thrall().channeledPayload);
        assertEquals(30 - (Balance.DMG_INFERNO + Balance.BOLT_DMG_VAR + 1), burningOrc.hp());

        // Living creature siphon -> FROSTBITE_DMG + SIPHON_COOL_DELTA + FIRE stored via channel
        ex.submit("pass");
        burningOrc.extinguish();
        burningOrc.temp = Balance.BODY_TEMP;
        ex.submit("siphon o1 | channel");
        assertEquals(Tag.FIRE, w.thrall().channeledPayload);
        assertEquals(Balance.BODY_TEMP + Balance.SIPHON_COOL_DELTA, burningOrc.temp);
        assertTrue(burningOrc.has(Tag.CHILLED));

        // Thermal promotion: channel | siphon <heat src> stores INFERNO when terminal in pipe
        Actor warmOrc = new Actor("o2", "Orc2", 'O', new Vec2(2, 6), new CreatureStats(30, 0, 0));
        w.add(warmOrc);
        ex.submit("channel | siphon o2");
        assertEquals(Tag.INFERNO, w.thrall().channeledPayload);
        assertEquals(Balance.CHANNEL_MAX_TURNS, w.thrall().channelTurns);

        // siphon self extracts OIL coat
        w.thrall().applyTag(Tag.OIL);
        w.thrall().oilTurns = Balance.OIL_TURNS;
        ex.submit("pass");
        ex.submit("siphon self | bolt o2");
        assertFalse(w.thrall().has(Tag.OIL));
        assertTrue(o2HasOil(warmOrc));
    }

    private static boolean o2HasOil(Actor a) {
        return a.has(Tag.OIL);
    }

    @Test
    void siphonThermalOnWaterAndGasesAndInvalidPoolThermal() {
        World w = newWorld();
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = exec(w, round);

        // @4,5 -t freezes puddle to ICE and emits FIRE
        Vec2 waterPos = new Vec2(4, 5);
        ThermalState.setWaterPuddle(w, waterPos);
        ex.submit("siphon @4,5 -t | channel");
        assertFalse(w.tile(waterPos).has(Tag.WATER));
        assertTrue(w.tile(waterPos).has(Tag.ICE));
        assertEquals(Tag.FIRE, w.thrall().channeledPayload);

        // @2,4/gas -t condenses STEAM under floor/standalone to WATER puddle and promotes channel FIRE -> INFERNO into bolt
        ex.submit("pass");
        Vec2 steamPos = new Vec2(2, 4);
        ThermalState.spawnGas(w, steamPos, Tag.STEAM);
        Actor target = new Actor("o1", "Orc", 'O', new Vec2(5, 5), new CreatureStats(30, 0, 0));
        w.add(target);
        ex.submit("channel | siphon @2,4/gas -t | bolt o1");
        assertFalse(w.tile(steamPos).has(Tag.STEAM));
        assertTrue(w.tile(steamPos).has(Tag.WATER));
        assertEquals(30 - (Balance.DMG_INFERNO + Balance.BOLT_DMG_VAR + 1), target.hp());

        // @2,3/oil_mist -t condenses to OIL slick; @3,5/caustic -t condenses to ACID pool
        Vec2 mistPos = new Vec2(2, 3);
        ThermalState.spawnGas(w, mistPos, Tag.OIL_MIST);
        ex.submit("siphon @2,3/oil_mist -t");
        assertFalse(w.tile(mistPos).has(Tag.OIL_MIST));
        assertTrue(w.tile(mistPos).has(Tag.OIL));

        Vec2 causticPos = new Vec2(3, 5);
        ThermalState.spawnGas(w, causticPos, Tag.CAUSTIC_GAS);
        ex.submit("pass");
        ex.submit("siphon @3,5/caustic -t");
        assertFalse(w.tile(causticPos).has(Tag.CAUSTIC_GAS));
        assertTrue(w.tile(causticPos).has(Tag.ACID));

        // -t rejected on /acid and /oil pools (pool remains intact)
        ex.submit("siphon @3,5/acid -t");
        assertTrue(w.tile(causticPos).has(Tag.ACID));
        ex.submit("siphon @2,3/oil -t");
        assertTrue(w.tile(mistPos).has(Tag.OIL));
    }

    @Test
    void channelForceFocusBloodSurgeAndBloodBoil() {
        World w = newWorld();
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = exec(w, round);

        // channel rejects liquids
        Vec2 waterPos = new Vec2(3, 5);
        ThermalState.setWaterPuddle(w, waterPos);
        ex.submit("siphon @3,5/water | channel");
        assertNull(w.thrall().channeledPayload);

        // channel -f stores FORCE standalone
        ex.submit("channel -f");
        assertEquals(Tag.FORCE, w.thrall().channeledPayload);
        assertEquals(Balance.CHANNEL_MAX_TURNS, w.thrall().channelTurns);

        // channel -b grants +3 AP once per round and breaks out of tax; siphon -b works on any creature, consumes BLEEDING for burst + 50% lifesteal, and does not overheat
        ex.submit("pass");
        int startHp = w.thrall().hp();
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(4, 5), new CreatureStats(16, 2, 0, 3, 1, 3.0));
        orc.applyTag(Tag.BLEEDING);
        orc.bleedTurns = 2; // Blood Drain dmg: 7 + 1 + 2*4 = 16 -> kills o1, heals 8 HP
        w.add(orc);

        Actor orc2 = new Actor("o2", "Orc2", 'O', new Vec2(6, 5), new CreatureStats(25, 3, 0, 3, 1, 3.0));
        orc2.applyTag(Tag.BLEEDING);
        orc2.bleedTurns = 2;
        w.add(orc2);

        ex.submit("channel -b ; siphon -b o1 ; bolt -b o2");
        assertFalse(orc.alive());
        int expectedBoltDmg = Balance.DMG_BLOOD + Balance.BLOOD_BOLT_VAR + 1 + Balance.BLOOD_HEMORRHAGE_DMG;
        assertEquals(25 - expectedBoltDmg, orc2.hp());
        assertEquals(startHp - Balance.HP_COST_CHANNEL_BLOOD - Balance.HP_COST_SIPHON_BLOOD
                + (16 / 2) - Balance.HP_COST_BOLT_BLOOD, w.thrall().hp());
        assertTrue(w.thrall().bloodSurgedThisRound);
        assertEquals(Balance.AP_PER_ROUND + Balance.BLOOD_SURGE_AP - Balance.AP_SIPHON_BLOOD - Balance.AP_BOLT_BLOOD,
                round.ap());

        // siphon -b also works on a non-bleeding enemy without cauterizing it above BOIL_TEMP
        ex.submit("pass");
        Actor fresh = new Actor("o3", "Orc3", 'O', new Vec2(3, 5), new CreatureStats(30, 3, 0, 3, 1, 1.0));
        w.add(fresh);
        int hpBeforeDrain = w.thrall().hp();
        ex.submit("siphon -b o3");
        int drainDmg = Balance.BLOOD_BOIL_BASE + 1;
        assertEquals(30 - drainDmg, fresh.hp());
        assertTrue(fresh.temp < Balance.BOIL_TEMP);
        assertEquals(hpBeforeDrain - Balance.HP_COST_SIPHON_BLOOD + (drainDmg / 2), w.thrall().hp());
        ex.submit("bolt -b o3");
        assertTrue(fresh.has(Tag.BLEEDING), "Target drained by siphon -b can still be afflicted with BLEEDING");
    }

    @Test
    void unpipedThermalSiphonLogsCausallyAndDetonatesOnCasterTile() {
        World w = newWorld();
        EventBus bus = new EventBus();
        List<String> logs = new ArrayList<>();
        bus.subscribe(ev -> {
            if (ev instanceof GameEvent.Narrative n) logs.add(n.text());
        });
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor ex = new Executor(w, bus, round, DefaultVerbs.create(round.rules()),
                new Interrupts(() -> ReactionSystem.fire(w), bus),
                () -> SimulationSystem.tick(w, bus));

        // 1. Unpiped siphon on chilled wet enemy logs hit/freeze/temp BEFORE Thrall draws heat, then blasts Thrall's tile
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(3, 5), new CreatureStats(30, 2, 0));
        orc.applyTag(Tag.WET);
        orc.wetTurns = Balance.WET_TURNS;
        orc.temp = 10;
        orc.applyTag(Tag.CHILLED);
        w.add(orc);

        int startHp = w.thrall().hp();
        ex.submit("siphon o1");
        assertTrue(orc.has(Tag.FROZEN));
        assertEquals(startHp - (Balance.DMG_HEAT + Balance.BASE_MAGIC / 2), w.thrall().hp());
        assertEquals(Balance.BODY_TEMP + Balance.TD_HEAT, w.thrall().temp);

        int hitIdx = indexOfContaining(logs, "Siphon hits Orc");
        int freezeIdx = indexOfContaining(logs, "frozen solid!");
        int coolIdx = indexOfContaining(logs, "cooled to -20C");
        int drawsIdx = indexOfContaining(logs, "Thrall draws heat.");
        int unstableIdx = indexOfContaining(logs, "goes unstable and blasts Thrall!");
        int backfireHitIdx = indexOfContaining(logs, "Unstable heat hits Thrall");
        assertTrue(hitIdx >= 0 && freezeIdx > hitIdx && coolIdx > freezeIdx && drawsIdx > coolIdx,
                "Expected hit -> freeze -> cool -> draws heat order, got: " + logs);
        assertTrue(unstableIdx > drawsIdx && backfireHitIdx > unstableIdx,
                "Expected unstable blast logs after draws heat, got: " + logs);

        // Next bolt is plain kinetic (nothing left from unpiped siphon)
        logs.clear();
        ex.submit("bolt o1");
        assertTrue(logs.stream().anyMatch(s -> s.contains("Kinetic bolt")));

        // 2. Unpiped siphon on water spills water over Thrall only; unpiped siphon on true fire (brazier) blasts Thrall with Inferno
        ex.submit("pass");
        w.thrall().heal(w.thrall().maxHp());
        Vec2 waterPos = new Vec2(3, 4);
        ThermalState.setWaterPuddle(w, waterPos);
        int hpBeforeWater = w.thrall().hp();
        int tempBeforeWater = w.thrall().temp;
        ex.submit("siphon @3,4/water");
        assertFalse(w.tile(waterPos).has(Tag.WATER));
        assertFalse(w.tile(w.thrall().pos()).has(Tag.WATER), "Unpiped water spills over Thrall only, not floor");
        assertTrue(w.thrall().has(Tag.WET));
        assertEquals(hpBeforeWater, w.thrall().hp());
        assertEquals(tempBeforeWater + Balance.TD_WATER, w.thrall().temp);

        w.thrall().removeTag(Tag.WET);
        w.thrall().wetTurns = 0;
        w.thrall().temp = Balance.BODY_TEMP;
        Prop brazier = Prop.brazier("br1", new Vec2(2, 6));
        w.add(brazier);
        int hpBeforeInferno = w.thrall().hp();
        ex.submit("siphon br1");
        assertFalse(brazier.has(Tag.LIT));
        assertEquals(hpBeforeInferno - (Balance.DMG_INFERNO + Balance.BASE_MAGIC / 2), w.thrall().hp());
        assertTrue(w.thrall().has(Tag.BURNING));
        assertEquals(Balance.BURN_CAP, w.thrall().temp);

        // 3. Unpiped -t on water puddle freezes water to ICE and detonates emitted heat on caster's tile
        ex.submit("pass");
        w.thrall().heal(w.thrall().maxHp());
        w.thrall().extinguish();
        w.thrall().temp = Balance.BODY_TEMP;
        Vec2 pondPos = new Vec2(2, 4);
        ThermalState.setWaterPuddle(w, pondPos);
        int hpBeforeThermalWater = w.thrall().hp();
        logs.clear();
        ex.submit("siphon @2,4/water -t");
        assertTrue(w.tile(pondPos).has(Tag.ICE));
        assertEquals(hpBeforeThermalWater - (Balance.DMG_HEAT + Balance.BASE_MAGIC / 2), w.thrall().hp());
        assertEquals(Balance.BODY_TEMP + Balance.TD_HEAT, w.thrall().temp);
        int iceIdx = indexOfContaining(logs, "freezes into ice!");
        int drawsThermalIdx = indexOfContaining(logs, "Thrall draws heat.");
        int unstableThermalIdx = indexOfContaining(logs, "goes unstable and blasts Thrall!");
        assertTrue(iceIdx >= 0 && drawsThermalIdx > iceIdx && unstableThermalIdx > drawsThermalIdx,
                "Expected ice -> draws heat -> unstable blast order, got: " + logs);

        // 4. Piped siphon whose downstream consumer is BLOCKED still detonates unconsumed thermal energy
        ex.submit("pass");
        w.thrall().heal(w.thrall().maxHp());
        w.thrall().temp = Balance.BODY_TEMP;
        Actor offLof = new Actor("o2", "OffLof", 'O', new Vec2(3, 6), new CreatureStats(30, 0, 0));
        w.add(offLof);
        Actor victim = new Actor("o3", "Victim", 'O', new Vec2(2, 3), new CreatureStats(30, 0, 0));
        w.add(victim);
        int hpBeforeBlockedPipe = w.thrall().hp();
        ex.submit("siphon o3 | bolt o2");
        assertEquals(30 - Balance.FROSTBITE_DMG, victim.hp());
        assertEquals(hpBeforeBlockedPipe - (Balance.DMG_HEAT + Balance.BASE_MAGIC / 2), w.thrall().hp());

        // 5. Unpiped siphon @acid clears pool and spills acid onto Thrall ONLY (not Thrall's floor tile)
        ex.submit("pass");
        w.thrall().heal(w.thrall().maxHp());
        w.thrall().setArmor(2);
        Vec2 acidPos = new Vec2(3, 5);
        ThermalState.setAcidPool(w, acidPos);
        int hpBeforeAcid = w.thrall().hp();
        ex.submit("siphon @3,5/acid");
        assertFalse(w.tile(acidPos).has(Tag.ACID), "Siphoned acid pool should be cleared");
        assertFalse(w.tile(w.thrall().pos()).has(Tag.ACID), "Unpiped liquid spills over Thrall only, not tile");
        assertTrue(w.thrall().has(Tag.CORRODED));
        assertEquals(1, w.thrall().armor());
        assertEquals(hpBeforeAcid - (Balance.DMG_ACID + Balance.BASE_MAGIC / 2 - 1), w.thrall().hp());

        // 6. Extinguished brazier and ground torch relight with a single fire bolt or ignite command
        ex.submit("pass");
        w.thrall().heal(w.thrall().maxHp());
        Prop brazier2 = Prop.brazier("br2", new Vec2(1, 5));
        w.add(brazier2);
        ex.submit("siphon br2 | bolt @1,5");
        assertTrue(brazier2.has(Tag.LIT), "Single inferno bolt must relight extinguished brazier");

        Item unlitTorch = Item.torch("ground_torch", false);
        w.map().placeGroundItem(new Vec2(3, 4), unlitTorch);
        w.thrall().inventory().placeInSlotForSetup(EquipmentSlot.HAND_LEFT, Item.torch("hand_torch", true));
        ex.submit("ignite @3,4/ground_torch");
        assertTrue(unlitTorch.has(Tag.LIT), "ignite @tile/torch should light unlit torch in 1 action");

        brazier2.removeTag(Tag.LIT);
        brazier2.temp = Balance.AMBIENT_TEMP;
        ex.submit("ignite br2");
        assertTrue(brazier2.has(Tag.LIT), "ignite br2 should relight extinguished brazier in 1 action");
    }

    private static int indexOfContaining(List<String> logs, String needle) {
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).contains(needle)) return i;
        }
        return -1;
    }
}
