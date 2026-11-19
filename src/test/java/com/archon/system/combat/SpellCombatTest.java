package com.archon.system.combat;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.CreatureStats;
import com.archon.model.Dice;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpellCombatTest {

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
        World w = new World(10, 10, dice);
        w.installThrall(new Thrall(new Vec2(2, 5), 40));
        return w;
    }

    @Test
    void hitAndCritChanceModifiersAndNonCritElementRules() {
        World w = newWorld(fixedRoll(10));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(5, 5), new CreatureStats(30, 2, 10, 3, 3, 1.0));
        Prop crate = new Prop("p1", "Crate", 'c', new Vec2(4, 5), 10);
        w.add(orc);
        w.add(crate);

        assertEquals(100, SpellCombat.hitChance(w.thrall(), crate, BodyPart.TORSO));
        assertEquals(Balance.BASE_HIT - 10, SpellCombat.hitChance(w.thrall(), orc, BodyPart.TORSO));
        assertEquals(Balance.BASE_HIT - 10 + Balance.ZONE_HEAD_HIT, SpellCombat.hitChance(w.thrall(), orc, BodyPart.HEAD));
        assertEquals(Balance.BASE_HIT - 10 + Balance.ZONE_ARM_HIT, SpellCombat.hitChance(w.thrall(), orc, BodyPart.ARM_L));
        assertEquals(Balance.BASE_HIT - 10 + Balance.ZONE_LEGS_HIT, SpellCombat.hitChance(w.thrall(), orc, BodyPart.LEGS));

        orc.applyTag(Tag.CHILLED);
        assertEquals(Balance.BASE_HIT - (10 + Balance.EVA_CHILLED), SpellCombat.hitChance(w.thrall(), orc, BodyPart.TORSO));
        orc.applyTag(Tag.FROZEN);
        assertEquals(Balance.BASE_HIT - (10 + Balance.EVA_FROZEN), SpellCombat.hitChance(w.thrall(), orc, BodyPart.TORSO));
        orc.removeTag(Tag.FROZEN);
        orc.removeTag(Tag.CHILLED);

        orc.applyTag(Tag.PRONE);
        assertEquals(
                Math.clamp(Balance.BASE_HIT - (10 - Balance.PRONE_EVA_PENALTY), 0, 100),
                SpellCombat.hitChance(w.thrall(), orc, BodyPart.TORSO));
        orc.removeTag(Tag.PRONE);

        w.thrall().applyTag(Tag.DAZED);
        w.thrall().applyTag(Tag.BLIND);
        assertEquals(
                Math.clamp(Balance.BASE_HIT - 10 + Balance.DAZED_HIT_PENALTY + Balance.SPELL_BLIND_HIT_PENALTY, 0, 100),
                SpellCombat.hitChance(w.thrall(), orc, BodyPart.TORSO));

        assertEquals(0, SpellCombat.critChance(80, BodyPart.HEAD, Tag.WATER));
        assertEquals(0, SpellCombat.critChance(80, BodyPart.HEAD, Tag.OIL));
        assertEquals(Balance.BASE_CRIT + Balance.ZONE_HEAD_CRIT, SpellCombat.critChance(80, BodyPart.HEAD, Tag.FORCE));
        assertEquals(Balance.BASE_CRIT + Balance.ZONE_HEAD_CRIT, SpellCombat.critChance(10, BodyPart.HEAD, Tag.FORCE));
        assertEquals(0, SpellCombat.critChance(0, BodyPart.HEAD, Tag.FORCE));
    }

    @Test
    void severityTierLadderMultipliersAndArmorBypass() {
        // Two separate rolls: hitRoll=50 <= 55 hits, critRoll=40 > 35 does not crit
        Dice hitNoCrit = new Dice() {
            int calls;
            @Override public Roll check(int percent) {
                int r = (calls++ == 0) ? 50 : 40;
                return new Roll(r <= percent, r, percent);
            }
            @Override public int between(int lo, int hi) { return lo; }
        };
        SpellCombat.RollResult plainHit = SpellCombat.rollAttack(hitNoCrit, 55, 35, BodyPart.HEAD);
        assertTrue(plainHit.hit());
        assertFalse(plainHit.crit());
        assertEquals(50, plainHit.hitRoll());
        assertEquals(40, plainHit.critRoll());

        // Head critChance = 35: critRolls 35 -> T1, 23 -> T2, 11 -> T3
        SpellCombat.RollResult t1 = SpellCombat.rollAttack(fixedRoll(35), 55, 35, BodyPart.HEAD);
        assertTrue(t1.hit());
        assertTrue(t1.crit());
        assertEquals(1, t1.severityTier());
        assertEquals(Balance.CRIT_MULT_HEAD_T1, t1.critMult());
        assertFalse(t1.ignoreArmor());

        SpellCombat.RollResult t2 = SpellCombat.rollAttack(fixedRoll(23), 55, 35, BodyPart.HEAD);
        assertEquals(2, t2.severityTier());
        assertEquals(Balance.CRIT_MULT_HEAD_T2, t2.critMult());
        assertTrue(t2.ignoreArmor());

        SpellCombat.RollResult t3 = SpellCombat.rollAttack(fixedRoll(11), 55, 35, BodyPart.HEAD);
        assertEquals(3, t3.severityTier());
        assertEquals(Balance.CRIT_MULT_HEAD_T3, t3.critMult());
        assertTrue(t3.ignoreArmor());

        assertEquals(Balance.CRIT_MULT_TORSO, SpellCombat.critMult(BodyPart.TORSO, 3));
        assertEquals(Balance.CRIT_MULT_LIMB_T1, SpellCombat.critMult(BodyPart.ARM_L, 1));
        assertEquals(Balance.CRIT_MULT_LIMB_T2, SpellCombat.critMult(BodyPart.ARM_R, 2));
        assertEquals(Balance.CRIT_MULT_LIMB_T3, SpellCombat.critMult(BodyPart.LEGS, 3));
    }

    @Test
    void aimedZonesApplyNonCritStatusPerHandDisarmAndHeadRipFatality() {
        World w = newWorld(fixedRoll(10));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(4, 5), new CreatureStats(40, 1, 0, 0, 3, 1.0));
        Item leftShield = Item.weapon("buckler", "buckler", 1, 2, 5);
        Item rightSword = Item.weapon("sword", "iron sword", 4, 6, 8);
        orc.inventory().placeInSlotForSetup(EquipmentSlot.HAND_LEFT, leftShield);
        orc.inventory().placeInSlotForSetup(EquipmentSlot.HAND_RIGHT, rightSword);
        w.add(orc);

        List<String> log = new ArrayList<>();

        // Aimed non-crit Head (roll 10 <= 20%) -> DAZED
        SpellCombat.RollResult nonCrit = new SpellCombat.RollResult(true, false, 40, 50, 55, 35, 0, 1.0, false);
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.HEAD, true, Tag.FORCE, null, nonCrit, log);
        assertTrue(orc.has(Tag.DAZED));
        assertEquals(Balance.DAZED_TURNS, orc.dazedTurns);

        // Aimed Arm.L T3 crit (roll 10 <= 75%) -> NUMB_ARM on ARM_L and drops HAND_LEFT only
        SpellCombat.RollResult t3Crit = new SpellCombat.RollResult(true, true, 1, 1, 65, 20, 3, Balance.CRIT_MULT_LIMB_T3, true);
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.ARM_L, true, Tag.FORCE, null, t3Crit, log);
        assertTrue(orc.has(Tag.NUMB_ARM));
        assertEquals(BodyPart.ARM_L, orc.numbArmPart);
        assertNull(orc.inventory().equipped(EquipmentSlot.HAND_LEFT));
        assertSame(rightSword, orc.inventory().equipped(EquipmentSlot.HAND_RIGHT));
        assertTrue(w.tile(orc.pos()).ground().contains(leftShield));

        // Aimed Arm.R T2 crit -> drops HAND_RIGHT
        SpellCombat.RollResult t2Crit = new SpellCombat.RollResult(true, true, 5, 5, 65, 20, 2, Balance.CRIT_MULT_LIMB_T2, true);
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.ARM_R, true, Tag.FORCE, null, t2Crit, log);
        assertEquals(BodyPart.ARM_R, orc.numbArmPart);
        assertNull(orc.inventory().equipped(EquipmentSlot.HAND_RIGHT));
        assertTrue(w.tile(orc.pos()).ground().contains(rightSword));

        // Aimed Legs non-crit sweep vs str 0 (resist = 80; roll 95 > 80 -> PRONE) + T2 CRIPPLED
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(95), w, orc, BodyPart.LEGS, true, Tag.FORCE, null, nonCrit, log);
        assertTrue(orc.has(Tag.PRONE));
        orc.removeTag(Tag.PRONE);
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.LEGS, true, Tag.FORCE, null, t2Crit, log);
        assertTrue(orc.has(Tag.PRONE));
        assertTrue(orc.has(Tag.CRIPPLED));
        assertEquals(Balance.CRIPPLED_TURNS, orc.crippledTurns);

        // Aimed Head T3 crit -> STUNNED + PRONE; lethal Head hit -> Head-Rip log
        orc.removeTag(Tag.PRONE);
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.HEAD, true, Tag.FORCE, null, t3Crit, log);
        assertTrue(orc.has(Tag.STUNNED));
        assertEquals(Balance.STUN_TURNS, orc.stunTurns);
        assertTrue(orc.has(Tag.PRONE));

        orc.takeDamage(orc.hp());
        log.clear();
        log.add("Orc falls.");
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.HEAD, true, Tag.FORCE, null, nonCrit, log);
        int ripIdx = -1, fallsIdx = -1;
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).contains("head is ripped apart")) ripIdx = i;
            if (log.get(i).equals("Orc falls.")) fallsIdx = i;
        }
        assertTrue(ripIdx >= 0 && fallsIdx > ripIdx, "Fatality description must precede 'falls.': " + log);
    }

    @Test
    void unaimedElementCritBonusesForForceFireAcidAndBlood() {
        World w = newWorld(fixedRoll(10));
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(4, 5), new CreatureStats(40, 3, 0));
        w.add(orc);

        List<String> log = new ArrayList<>();
        SpellCombat.RollResult t3 = new SpellCombat.RollResult(true, true, 1, 1, 80, 5, 3, Balance.CRIT_MULT_TORSO, true);

        // FORCE T3 -> PRONE + DAZED
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.TORSO, false, Tag.FORCE, null, t3, log);
        assertTrue(orc.has(Tag.PRONE));
        assertTrue(orc.has(Tag.DAZED));

        // FIRE T3 -> ignites Dry + SCALD_FLASH_DMG
        int hpBeforeFire = orc.hp();
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.TORSO, false, Tag.FIRE, null, t3, log);
        assertTrue(orc.has(Tag.BURNING));
        assertEquals(hpBeforeFire - Balance.SCALD_FLASH_DMG, orc.hp());

        // ACID T3 -> extra armor shred + ACID pool on tile
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.TORSO, false, Tag.ACID, null, t3, log);
        assertEquals(3 - Balance.ACID_ARMOR_SHRED, orc.armor());
        assertTrue(w.tile(orc.pos()).has(Tag.ACID));

        // BLOOD T3 -> +CRIT_BLEED_BONUS_TURNS + instant BLEED_DMG tick
        orc.applyTag(Tag.BLEEDING);
        orc.bleedTurns = Balance.BLEED_TURNS;
        int hpBeforeBlood = orc.hp();
        SpellCombat.applyBoltStatusesAndBonuses(fixedRoll(10), w, orc, BodyPart.TORSO, false, Tag.BLOOD, null, t3, log);
        assertEquals(Balance.BLEED_TURNS + Balance.CRIT_BLEED_BONUS_TURNS, orc.bleedTurns);
        assertEquals(hpBeforeBlood - Balance.BLEED_DMG, orc.hp());
    }
}
