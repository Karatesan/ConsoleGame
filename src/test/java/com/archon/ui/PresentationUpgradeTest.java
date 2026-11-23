package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.event.GameEvent;
import com.archon.model.Balance;
import com.archon.model.Dice;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.verb.ExitCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PresentationUpgradeTest {

    @Test
    void pixelAtlasBakesValidNonEmptyCells() {
        int[] pixels = PixelAtlas.bakeRgbaPixels();
        assertEquals(PixelAtlas.ATLAS_W * PixelAtlas.ATLAS_H, pixels.length);

        for (int idx = 0; idx < PixelAtlas.COUNT; idx++) {
            int ox = (idx % PixelAtlas.COLS) * PixelAtlas.CELL;
            int oy = (idx / PixelAtlas.COLS) * PixelAtlas.CELL;
            int opaque = 0;
            for (int y = 0; y < PixelAtlas.CELL; y++) {
                for (int x = 0; x < PixelAtlas.CELL; x++) {
                    if ((pixels[(oy + y) * PixelAtlas.ATLAS_W + (ox + x)] & 0xFF) != 0) opaque++;
                }
            }
            assertTrue(opaque > 16, "Sprite cell " + idx + " should have visible pixels");
        }

        int wx = (PixelAtlas.COUNT % PixelAtlas.COLS) * PixelAtlas.CELL;
        int wy = (PixelAtlas.COUNT / PixelAtlas.COLS) * PixelAtlas.CELL;
        assertEquals(0xFFFFFFFF, pixels[wy * PixelAtlas.ATLAS_W + wx]);

        int[] glow = PixelAtlas.bakeGlowPixels();
        assertEquals(PixelAtlas.GLOW_SIZE * PixelAtlas.GLOW_SIZE, glow.length);
        assertEquals(0, glow[0] & 0xFF, "Glow corners should fade to transparent");
        int mid = PixelAtlas.GLOW_SIZE / 2;
        assertTrue((glow[mid * PixelAtlas.GLOW_SIZE + mid] & 0xFF) > 220, "Glow center should be opaque");

        int[] animBases = {
                PixelAtlas.WATER_0, PixelAtlas.ACID_0, PixelAtlas.FIRE_0,
                PixelAtlas.THRALL_0, PixelAtlas.ORC_0, PixelAtlas.GOBLIN_0,
                PixelAtlas.OGRE_0, PixelAtlas.RAT_0, PixelAtlas.BRAZIER_0,
                PixelAtlas.THRALL_MOVE_0, PixelAtlas.THRALL_ATTACK_0, PixelAtlas.THRALL_CAST_0
        };
        for (int b = 0; b < animBases.length; b++) {
            int base = animBases[b];
            for (int f = 0; f < 4; f++) {
                int next = (f + 1) & 3;
                assertTrue(cellsDiffer(pixels, base + f, base + next),
                        "Animated sprite base " + base + " frame " + f + " should differ from " + next);
            }
        }

        assertEquals(PixelAtlas.THRALL_MOVE_2, PixelAtlas.thrallSprite(PixelAtlas.POSE_MOVE, 2));
        assertEquals(PixelAtlas.THRALL_ATTACK_3, PixelAtlas.thrallSprite(PixelAtlas.POSE_ATTACK, 3));
        assertEquals(PixelAtlas.THRALL_CAST_1, PixelAtlas.thrallSprite(PixelAtlas.POSE_CAST, 1));
        assertEquals(PixelAtlas.THRALL_GUARD, PixelAtlas.thrallSprite(PixelAtlas.POSE_GUARD, 0));
        assertEquals(PixelAtlas.THRALL_HURT, PixelAtlas.thrallSprite(PixelAtlas.POSE_HURT, 0));

        assertEquals(PixelAtlas.LOOT_BLADE, PixelAtlas.spriteForLoot(Scene.LOOT_BLADE));
        assertEquals(PixelAtlas.LOOT_OIL, PixelAtlas.spriteForLoot(Scene.LOOT_FLASK_OIL));
        assertEquals(PixelAtlas.LOOT_WATER, PixelAtlas.spriteForLoot(Scene.LOOT_FLASK_WATER));
        assertEquals(PixelAtlas.LOOT_ACID, PixelAtlas.spriteForLoot(Scene.LOOT_FLASK_ACID));
        assertEquals(PixelAtlas.LOOT_STONE, PixelAtlas.spriteForLoot(Scene.LOOT_STONE));
    }

    @Test
    void synthAudioBuildsValidRiffWaveForEverySoundKind() {
        assertEquals(22, SynthAudio.COUNT);
        SynthAudio audio = new SynthAudio();
        for (int k = 0; k < SynthAudio.COUNT; k++) {
            int variants = SfxGen.variantsFor(k);
            assertTrue(variants >= 1);
            for (int v = 0; v < variants; v++) {
                byte[] wav = v == 0 ? SfxGen.buildWav(k) : SfxGen.buildWav(k, v);
                assertTrue(wav.length > 44, "WAV too short for kind " + k + " v=" + v);
                assertEquals('R', wav[0]);
                assertEquals('I', wav[1]);
                assertEquals('F', wav[2]);
                assertEquals('F', wav[3]);
                assertEquals('W', wav[8]);
                assertEquals('A', wav[9]);
                assertEquals('V', wav[10]);
                assertEquals('E', wav[11]);
                assertEquals('f', wav[12]);
                assertEquals('m', wav[13]);
                assertEquals('t', wav[14]);
                assertEquals(' ', wav[15]);
                int channels = (wav[22] & 0xFF) | ((wav[23] & 0xFF) << 8);
                assertEquals(1, channels);
                int sampleRate = (wav[24] & 0xFF) | ((wav[25] & 0xFF) << 8)
                        | ((wav[26] & 0xFF) << 16) | ((wav[27] & 0xFF) << 24);
                assertEquals(44100, sampleRate);
                assertEquals('d', wav[36]);
                assertEquals('a', wav[37]);
                assertEquals('t', wav[38]);
                assertEquals('a', wav[39]);
                int dataBytes = (wav[40] & 0xFF) | ((wav[41] & 0xFF) << 8)
                        | ((wav[42] & 0xFF) << 16) | ((wav[43] & 0xFF) << 24);
                assertEquals(wav.length - 44, dataBytes);

                boolean nonZero = false;
                for (int i = 44; i < wav.length; i++) {
                    if (wav[i] != 0) {
                        nonZero = true;
                        break;
                    }
                }
                assertTrue(nonZero, "PCM samples should be non-zero for kind " + k + " v=" + v);
            }
            audio.play(k, 0.5f, 1.05f, -0.3f);
        }
        assertFalse(Arrays.equals(SfxGen.buildWav(SfxGen.BLOOD_BOLT, 0), SfxGen.buildWav(SfxGen.ZAP, 0)),
                "BLOOD_BOLT should differ from ZAP");
        int[] variedKinds = {SfxGen.BOOM, SfxGen.SPLASH, SfxGen.SIZZLE, SfxGen.BLOOD_BOLT};
        for (int i = 0; i < variedKinds.length; i++) {
            int k = variedKinds[i];
            assertFalse(Arrays.equals(SfxGen.buildWav(k, 0), SfxGen.buildWav(k, 1)),
                    "Variants 0 and 1 should differ for kind " + k);
        }
        for (int t = 0; t < 3; t++) {
            audio.playTier(SynthAudio.CRIT, t, 0.75f, 1.0f, 0f);
        }

        assertFalse(audio.toggleMute());
        assertTrue(audio.toggleMute());
        assertTrue(audio.toggleMusic());
        assertFalse(audio.toggleMusic());

        audio.setHpRatio(1f);
        audio.updateMusic(0.5f);
        for (int t = 0; t < SynthAudio.TRACK_COUNT; t++) {
            audio.setTrack(t);
            audio.updateMusic(0.2f);
        }
        audio.setHpRatio(0.25f);
        audio.setTrack(-1);
        audio.playDelayed(SynthAudio.SIPHON, 0.26f, 0.6f, 1.0f, 0f);
        audio.playTierDelayed(SynthAudio.CRIT, 2, 0.36f, 0.8f, 1.0f, 0f);
        audio.updateMusic(0.5f);
        audio.setHpRatio(1f);
        audio.updateMusic(0.5f);
        audio.dispose();
    }

    @Test
    void hasVisibleEnemyRespectsWallsGasAndLifeState() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        assertTrue(GdxView.hasVisibleEnemy(new Scene(arena)));

        // Wall off all living creatures from thrall at (2,4)
        for (var e : arena.entities()) {
            if (e.kind() == com.archon.model.Entity.Kind.CREATURE) e.takeDamage(999);
        }
        assertFalse(GdxView.hasVisibleEnemy(new Scene(arena)));

        World room = Scenario.testRoom(new Dice.Seeded(2L));
        // Place opaque steam between thrall (3,4) and archer (8,4), kill close orc (3,5)
        room.actor("o1").takeDamage(999);
        assertTrue(GdxView.hasVisibleEnemy(new Scene(room)));
        room.map().addTag(new Vec2(5, 4), Tag.STEAM);
        assertFalse(GdxView.hasVisibleEnemy(new Scene(room)));
    }

    @Test
    void scenePopulatesLootLightingLitOverwatchAndProne() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        arena.actor("o1").applyTag(Tag.PRONE);
        Scene s = new Scene(arena);

        assertEquals(Scene.LOOT_STONE, s.lootKind[4 * s.w + 4]);
        assertEquals(Scene.LOOT_BLADE, s.lootKind[4 * s.w + 3]);
        assertEquals(Scene.LOOT_FLASK_ACID, s.lootKind[1 * s.w + 1]);

        assertTrue(s.litEntity[0], "Thrall with lit torch should emit light");
        assertTrue(s.litEntity[s.indexOf("br1")], "Brazier should emit light");
        assertTrue(s.proneEntity[s.indexOf("o1")]);
        assertTrue(s.light[s.y[0] * s.w + s.x[0]] > s.light[1 * s.w + 9],
                "Tiles next to Thrall torch should be brighter than far corner");

        World room = Scenario.testRoom(new Dice.Seeded(2L));
        Scene sr = new Scene(room);
        assertTrue(sr.overwatchEntity[sr.indexOf("g1")], "Goblin Archer in testRoom starts on overwatch");
        assertFalse(sr.overwatchEntity[sr.indexOf("o1")]);

        assertEquals(PixelAtlas.THRALL_0, PixelAtlas.spriteForEntity(s, 0, 0));
        assertEquals(PixelAtlas.THRALL_2, PixelAtlas.spriteForEntity(s, 0, 2));
        assertEquals(PixelAtlas.ORC_1, PixelAtlas.spriteForEntity(s, s.indexOf("o1"), 1));
        assertEquals(PixelAtlas.ORC_3, PixelAtlas.spriteForEntity(s, s.indexOf("o1"), 3));
        assertEquals(PixelAtlas.GOBLIN_0, PixelAtlas.spriteForEntity(s, s.indexOf("g1"), 0));
        assertEquals(PixelAtlas.GOBLIN_2, PixelAtlas.spriteForEntity(s, s.indexOf("g1"), 2));
        assertEquals(PixelAtlas.OGRE_0, PixelAtlas.spriteForEntity(s, s.indexOf("og1"), 0));
        assertEquals(PixelAtlas.OGRE_3, PixelAtlas.spriteForEntity(s, s.indexOf("og1"), 3));
        assertEquals(PixelAtlas.RAT_0, PixelAtlas.spriteForEntity(s, s.indexOf("r1"), 0));
        assertEquals(PixelAtlas.RAT_2, PixelAtlas.spriteForEntity(s, s.indexOf("r1"), 2));
        assertEquals(PixelAtlas.BRAZIER_1, PixelAtlas.spriteForEntity(s, s.indexOf("br1"), 1));
        assertEquals(PixelAtlas.BRAZIER_3, PixelAtlas.spriteForEntity(s, s.indexOf("br1"), 3));
        assertEquals(PixelAtlas.BARREL, PixelAtlas.spriteForEntity(s, s.indexOf("b1"), 0));

        assertEquals("rusted cleaver", s.rightHand);
        assertEquals("torch [LIT]", s.leftHand);
        assertEquals(5, s.packCount);
        assertEquals(Balance.PACK_MAX, s.packMax);
        assertEquals(5, s.packItems.length);

        arena.get("br1").removeTag(Tag.LIT);
        arena.actor("o1").applyTag(Tag.FROZEN);
        Scene offAndFrozen = new Scene(arena);
        assertEquals(PixelAtlas.BRAZIER_OFF, PixelAtlas.spriteForEntity(offAndFrozen, offAndFrozen.indexOf("br1"), 3));
        assertEquals(PixelAtlas.ORC_0, PixelAtlas.spriteForEntity(offAndFrozen, offAndFrozen.indexOf("o1"), 3));
    }

    private static boolean cellsDiffer(int[] pixels, int a, int b) {
        int ax = (a % PixelAtlas.COLS) * PixelAtlas.CELL, ay = (a / PixelAtlas.COLS) * PixelAtlas.CELL;
        int bx = (b % PixelAtlas.COLS) * PixelAtlas.CELL, by = (b / PixelAtlas.COLS) * PixelAtlas.CELL;
        for (int y = 0; y < PixelAtlas.CELL; y++) {
            for (int x = 0; x < PixelAtlas.CELL; x++) {
                if (pixels[(ay + y) * PixelAtlas.ATLAS_W + (ax + x)] != pixels[(by + y) * PixelAtlas.ATLAS_W + (bx + x)]) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void findTargetsMatchesEntityIdsAndTileAnchors() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        Scene s = new Scene(arena);
        int[] xs = new int[8], ys = new int[8];

        int count = GdxView.findTargets("siphon @3,5/oil | throw @o1 ; ignite o1 -f", s, xs, ys, 8);
        assertEquals(2, count);
        assertEquals(3, xs[0]);
        assertEquals(5, ys[0]);
        int oi = s.indexOf("o1");
        assertEquals(s.x[oi], xs[1]);
        assertEquals(s.y[oi], ys[1]);

        int stoneCount = GdxView.findTargets("pull @4,4/stone | bolt @self", s, xs, ys, 8);
        assertEquals(2, stoneCount);
        assertEquals(4, xs[0]);
        assertEquals(4, ys[0]);
        assertEquals(s.x[0], xs[1]);
        assertEquals(s.y[0], ys[1]);
    }

    @Test
    void pipedElementalBoltsBlastsBloodAndPsionicSpawnsDistinctVfx() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        GdxView view = new GdxView(1L, true);
        view.scene(new Scene(arena), null);

        // Psionic / Kinetic bolt -> FX_PSIONIC_SPIKE (2)
        view.handle(new GameEvent.StageResult(1, 1, "bolt o1", ExitCode.SUCCESS, 1, 3));
        assertEquals(2, view.recentFxKind(0));

        // Bloodlance -> FX_BLOOD_BOLT (1)
        view.handle(new GameEvent.StageResult(1, 1, "bolt o1 --blood", ExitCode.SUCCESS, 1, 3));
        assertEquals(1, view.recentFxKind(0));

        // Piped Inferno bolt -> FX_DRAIN (8) then FX_FIREBALL (4)
        view.handle(new GameEvent.StageResult(1, 1, "siphon br1 | bolt o1", ExitCode.SUCCESS, 1, 3));
        assertEquals(4, view.recentFxKind(0));
        assertEquals(8, view.recentFxKind(1));

        // Piped Acid bolt -> FX_DRAIN (8) then FX_ELEM_BOLT (5) with ELEM_ACID (3)
        view.handle(new GameEvent.StageResult(1, 1, "siphon @1,3/acid | bolt o1", ExitCode.SUCCESS, 1, 3));
        assertEquals(5, view.recentFxKind(0));
        assertEquals(3, view.recentFxParam(0));
        assertEquals(8, view.recentFxKind(1));

        // Piped Water bolt -> FX_ELEM_BOLT (5) with ELEM_WATER (2)
        view.handle(new GameEvent.StageResult(1, 1, "siphon @3,2 | bolt o1", ExitCode.SUCCESS, 1, 3));
        assertEquals(5, view.recentFxKind(0));
        assertEquals(2, view.recentFxParam(0));

        // Piped Oil blast -> FX_CROSS (7) with ELEM_OIL (4) + FX_RING (6)
        view.handle(new GameEvent.StageResult(1, 1, "siphon @3,5/oil | blast @7,4", ExitCode.SUCCESS, 2, 2));
        assertEquals(6, view.recentFxKind(0));
        assertEquals(7, view.recentFxKind(1));
        assertEquals(4, view.recentFxParam(1));

        // Channeled Inferno blast -> FX_RING (channel) then FX_CROSS (7) with ELEM_FIRE (1) + FX_RING (6)
        arena.thrall().channeledPayload = Tag.INFERNO;
        arena.thrall().channelTurns = 2;
        view.scene(new Scene(arena), null);
        view.handle(new GameEvent.StageResult(1, 1, "channel | blast @7,4", ExitCode.SUCCESS, 1, 2));
        assertEquals(6, view.recentFxKind(0));
        assertEquals(7, view.recentFxKind(1));
        assertEquals(1, view.recentFxParam(1));
    }

    @Test
    void thrallPlaysMoveAttackCastGuardHurtAndFacingAnimations() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        GdxView view = new GdxView(1L, true);
        Scene s0 = new Scene(arena);
        view.scene(s0, null);
        assertEquals(1, view.thrallFacing());
        assertEquals(PixelAtlas.THRALL_2, view.thrallDrawSprite(2));

        // Move left -> flips facing to -1 and cycles THRALL_MOVE_0..3 over SLIDE (0.4s)
        Vec2 start = arena.thrall().pos();
        arena.thrall().moveTo(new Vec2(start.x() - 1, start.y()));
        Scene s1 = new Scene(arena);
        view.scene(s1, s0);
        assertEquals(-1, view.thrallFacing());
        assertEquals(PixelAtlas.THRALL_MOVE_0, view.thrallDrawSprite(0));
        view.stepAnim(0.25f);
        assertEquals(PixelAtlas.THRALL_MOVE_2, view.thrallDrawSprite(0));
        view.stepAnim(0.20f);
        assertEquals(PixelAtlas.THRALL_1, view.thrallDrawSprite(1));

        // Strike to the right -> flips facing back to 1 and plays THRALL_ATTACK_0..3
        view.handle(new GameEvent.StageResult(1, 1, "strike o1", ExitCode.SUCCESS, 1, 4));
        assertEquals(1, view.thrallFacing());
        assertEquals(PixelAtlas.THRALL_ATTACK_0, view.thrallDrawSprite(0));
        view.stepAnim(0.18f);
        assertEquals(PixelAtlas.THRALL_ATTACK_2, view.thrallDrawSprite(0));
        view.stepAnim(0.22f);
        assertEquals(PixelAtlas.THRALL_0, view.thrallDrawSprite(0));

        // Cast & Guard
        view.handle(new GameEvent.StageResult(1, 1, "bolt o1", ExitCode.SUCCESS, 1, 3));
        assertEquals(PixelAtlas.THRALL_CAST_0, view.thrallDrawSprite(0));
        view.stepAnim(0.20f);
        assertEquals(PixelAtlas.THRALL_CAST_1, view.thrallDrawSprite(0));
        view.stepAnim(0.20f);
        assertEquals(PixelAtlas.THRALL_CAST_2, view.thrallDrawSprite(0));
        view.stepAnim(0.35f);
        assertEquals(PixelAtlas.THRALL_0, view.thrallDrawSprite(0));

        view.handle(new GameEvent.StageResult(1, 1, "guard", ExitCode.SUCCESS, 1, 2));
        assertEquals(PixelAtlas.THRALL_GUARD, view.thrallDrawSprite(0));
        view.stepAnim(0.50f);

        // Hurt flinch when damaged while idle
        arena.thrall().takeDamage(3);
        Scene sHurt = new Scene(arena);
        view.scene(sHurt, s1);
        assertEquals(PixelAtlas.THRALL_HURT, view.thrallDrawSprite(0));
        view.stepAnim(0.30f);
        assertEquals(PixelAtlas.THRALL_0, view.thrallDrawSprite(0));

        // Frozen Thrall locks to idle frame 0
        arena.thrall().applyTag(Tag.FROZEN);
        view.scene(new Scene(arena), sHurt);
        assertEquals(PixelAtlas.THRALL_0, view.thrallDrawSprite(3));
    }

    @Test
    void criticalHitsTriggerShakeHitStopFlashVfxAndGoldFloaters() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        GdxView view = new GdxView(1L, true);
        Scene s0 = new Scene(arena);
        view.scene(s0, null);

        // 1. Normal melee crit against Orc Guard
        view.handle(new GameEvent.Narrative("CRITICAL! Cleaver hits Orc Guard's head for 12 dmg. [70% | roll 85 vs 31+ | crit 40%: 90 vs 61+]"));
        view.handle(new GameEvent.StageResult(1, 1, "strike o1", ExitCode.SUCCESS, 1, 4));
        arena.actor("o1").takeDamage(12);
        Scene s1 = new Scene(arena);
        view.scene(s1, s0);

        assertEquals(6.5f, view.shakeMag());
        assertTrue(view.hitStopLeft() > 0.05f);
        assertTrue(view.critFlashLeft() > 0.10f);
        assertEquals("CRIT! -12", view.recentFloaterText(0));
        assertEquals(6, view.recentFxKind(0)); // FX_RING inner
        assertEquals(6, view.recentFxKind(1)); // FX_RING outer
        assertEquals(9, view.recentFxKind(2)); // FX_CRIT

        // Hit-stop pauses fx/pose briefly while critFlash decays
        float flashBefore = view.critFlashLeft();
        view.stepAnim(0.04f);
        assertTrue(view.critFlashLeft() < flashBefore);
        view.stepAnim(0.60f);

        // 2. Multi-target spell blast where Goblin Archer takes T3 Inferno crit and Orc Guard takes normal damage
        view.handle(new GameEvent.Narrative("Inferno blast hits Orc Guard for 4 dmg. [auto-hit]"));
        view.handle(new GameEvent.Narrative("CRITICAL T3! Inferno blast hits Goblin Archer's head for 10 dmg. [85% | roll 95 vs 16+]"));
        view.handle(new GameEvent.StageResult(1, 1, "siphon br1 | blast @7,4", ExitCode.SUCCESS, 2, 2));
        arena.actor("o1").takeDamage(4);
        arena.actor("g1").takeDamage(10);
        Scene s2 = new Scene(arena);
        view.scene(s2, s1);

        assertEquals(10f, view.shakeMag());
        boolean foundGoblinCrit = false, foundOrcPlain = false;
        for (int b = 0; b < 4; b++) {
            String ft = view.recentFloaterText(b);
            if ("CRIT T3! -10".equals(ft)) foundGoblinCrit = true;
            if ("-4".equals(ft)) foundOrcPlain = true;
        }
        assertTrue(foundGoblinCrit, "Goblin Archer should receive T3 crit floater");
        assertTrue(foundOrcPlain, "Non-crit Orc Guard should keep plain damage floater");
        assertEquals(9, view.recentFxKind(2));

        view.stepAnim(0.60f);

        // 3. Hostile ActorBeat crit against Thrall
        view.handle(new GameEvent.ActorBeat("o1", "Orc Guard", new Vec2(5, 4), new Vec2(4, 4),
                List.of("CRITICAL! Orc Guard hits Thrall's torso for 5 dmg. [70% | roll 92 vs 31+]")));
        arena.thrall().takeDamage(5);
        Scene s3 = new Scene(arena);
        view.scene(s3, s2);
        assertEquals("CRIT! -5", view.recentFloaterText(0));
        assertEquals(9, view.recentFxKind(2));
    }
}
