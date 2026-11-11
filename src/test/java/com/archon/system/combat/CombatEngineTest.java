package com.archon.system.combat;

import com.archon.model.Actor;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatEngineTest {

    private World world;
    private Thrall thrall;
    private Actor orc;

    @BeforeEach
    void setUp() {
        world = new World(10, 10, new Dice.Seeded(42L));
        thrall = new Thrall(new Vec2(1, 1), 40);
        thrall.inventory().placeInSlotForSetup(
                EquipmentSlot.HAND_RIGHT,
                Item.weapon("rusted_cleaver", "rusted cleaver", 4, 8, 10, Tag.METAL)
        );
        world.installThrall(thrall);

        orc = new Actor("o1", "Orc Guard", 'O', new Vec2(1, 2), new CreatureStats(24, 3, 5));
        world.add(orc);
    }

    @Test
    void headAimAppliesAimPenalty() {
        // Base 70 + Head (-40) - Orc evasion (5) = 25% hit chance
        var res = CombatEngine.resolveMelee(new Dice.Always(true), world, thrall, orc, BodyPart.HEAD, true, "normal", false);
        assertEquals(25, res.hitChance(), "Targeted head shot should apply -40 aim penalty");
    }

    @Test
    void untargetedStrikeHasNoAimPenalty() {
        // Base 70 + aimMod (0) - Orc evasion (5) = 65% hit chance
        var res = CombatEngine.resolveMelee(new Dice.Always(true), world, thrall, orc, BodyPart.TORSO, false, "normal", false);
        assertEquals(65, res.hitChance(), "Untargeted strike should have 0 aim penalty");
    }

    @Test
    void criticalHitBypassesArmor() {
        Dice always = new Dice.Always(true);
        // Cleaver max 8 + str 3 = 11; torso 1.0x; crit 2.0x = 22; armor bypassed = 22 dmg
        var res = CombatEngine.resolveMelee(always, world, thrall, orc, BodyPart.TORSO, false, "normal", false);
        assertTrue(res.crit());
        assertEquals(22, res.damage(), "Critical hit should completely bypass target's 3 armor");
    }

    @Test
    void nonCreatureTargetHasNoBodyPartMultipliers() {
        Prop barrel = new Prop("b1", "Barrel", 'B', new Vec2(1, 2), 20);
        world.add(barrel);

        Dice always = new Dice.Always(true);
        // Cleaver max 8 + str 3 = 11; part is null -> 1.0x; crit 2.0x = 22
        var res = CombatEngine.resolveMelee(always, world, thrall, barrel, null, false, "normal", false);
        assertNull(res.part(), "Prop should not have a body part");
        assertEquals(22, res.damage());
    }

    @Test
    void bodyPartWeightedRollDistribution() {
        // Test that rollLocation returns valid parts
        for (int i = 0; i < 50; i++) {
            BodyPart part = BodyPart.rollLocation(world.dice());
            assertNotNull(part);
        }
    }

    @Test
    void hitResultCarriesTelemetryAndArmorBypass() {
        Dice always = new Dice.Always(true);
        var res = CombatEngine.resolveMelee(always, world, thrall, orc, BodyPart.HEAD, true, "normal", false);
        assertTrue(res.hit());
        assertTrue(res.crit());
        assertEquals(25, res.hitChance());
        assertTrue(res.hitRoll() <= res.hitChance());
        assertEquals(3, res.armorBlocked(), "Should record 3 armor bypassed on critical hit");
    }

    @Test
    void criticalNarrativeFalloutPools() {
        Dice dice = new Dice.Always(true);
        var critRes = new CombatEngine.HitResult(true, 10, 25, 20, 3, BodyPart.HEAD, null, true, 5, 25,
                false, false, false, Tag.BLIND, com.archon.system.environment.Destruction.Result.EMPTY);
        String blindMsg = CombatNarrative.describeCrit(dice, BodyPart.HEAD, critRes, "Orc");
        assertTrue(blindMsg.contains("Orc") && (blindMsg.contains("blind") || blindMsg.contains("darkness") || blindMsg.contains("vision")),
                "Should pick from head blind pool: " + blindMsg);

        var killRes = new CombatEngine.HitResult(true, 10, 25, 50, 3, BodyPart.TORSO, null, true, 5, 25,
                true, false, false, null, com.archon.system.environment.Destruction.Result.EMPTY);
        String killMsg = CombatNarrative.describeCrit(dice, BodyPart.TORSO, killRes, "Orc");
        assertTrue(killMsg.contains("Orc") && (killMsg.contains("chest") || killMsg.contains("rib") || killMsg.contains("heart") || killMsg.contains("dead")),
                "Should pick from torso kill pool: " + killMsg);
    }

    @Test
    void aimedAttackCostsExtraAp() {
        var catalog = com.archon.app.DefaultVerbs.create(com.archon.exec.EconomyRules.prototype());
        var strikeDef = catalog.get("strike");
        var shootDef = catalog.get("shoot");

        // Untargeted strike: 2 AP
        assertEquals(2, strikeDef.price().cost(new com.archon.command.Ast.Invocation("strike", java.util.List.of("o1"), java.util.Map.of())));
        // Aimed strike with flag -a: 2 + 1 = 3 AP
        assertEquals(3, strikeDef.price().cost(new com.archon.command.Ast.Invocation("strike", java.util.List.of("o1"), java.util.Map.of("aim", "head"))));
        // Aimed strike with body path address: 2 + 1 = 3 AP
        assertEquals(3, strikeDef.price().cost(new com.archon.command.Ast.Invocation("strike", java.util.List.of("o1/body/head"), java.util.Map.of())));
        // Light power aimed: 1 + 1 = 2 AP
        assertEquals(2, strikeDef.price().cost(new com.archon.command.Ast.Invocation("strike", java.util.List.of("o1"), java.util.Map.of("aim", "head", "power", "light"))));
        // Heavy power aimed: 3 + 1 = 4 AP
        assertEquals(4, strikeDef.price().cost(new com.archon.command.Ast.Invocation("strike", java.util.List.of("o1"), java.util.Map.of("aim", "head", "power", "heavy"))));

        // Untargeted shoot: 2 AP
        assertEquals(2, shootDef.price().cost(new com.archon.command.Ast.Invocation("shoot", java.util.List.of("o1"), java.util.Map.of())));
        // Aimed shoot: 2 + 1 = 3 AP
        assertEquals(3, shootDef.price().cost(new com.archon.command.Ast.Invocation("shoot", java.util.List.of("o1"), java.util.Map.of("aim", "head"))));
        assertEquals(3, shootDef.price().cost(new com.archon.command.Ast.Invocation("shoot", java.util.List.of("o1/body/head"), java.util.Map.of())));
    }
}
