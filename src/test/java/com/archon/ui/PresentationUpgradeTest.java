package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.event.GameEvent;
import com.archon.model.Balance;
import com.archon.model.Dice;
import com.archon.model.EquipmentSlot;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.ui.audio.SfxGen;
import com.archon.ui.audio.SynthAudio;
import com.archon.verb.ExitCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PresentationUpgradeTest {

    @Test
    void pixelAtlasBakesValidNonEmptyCells() {
        int[] pixels = PixelAtlas.loadRgbaPixels();
        assertEquals(PixelAtlas.ATLAS_W * PixelAtlas.ATLAS_H, pixels.length);

        for (int idx = 0; idx < PixelAtlas.COUNT; idx++) {
            int ox = (idx % PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
            int oy = (idx / PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
            int opaque = 0;
            for (int y = 0; y < PixelAtlas.CELL; y++) {
                for (int x = 0; x < PixelAtlas.CELL; x++) {
                    if ((pixels[(oy + y) * PixelAtlas.ATLAS_W + (ox + x)] & 0xFF) != 0) opaque++;
                }
            }
            assertTrue(opaque > 256, "Sprite cell " + idx + " should have visible pixels");
        }

        int[] glow = PixelAtlas.bakeGlowPixels();
        assertEquals(PixelAtlas.GLOW_SIZE * PixelAtlas.GLOW_SIZE, glow.length);
        assertEquals(0, glow[0] & 0xFF, "Glow corners should fade to transparent");
        int mid = PixelAtlas.GLOW_SIZE / 2;
        assertTrue((glow[mid * PixelAtlas.GLOW_SIZE + mid] & 0xFF) > 220, "Glow center should be opaque");

        assertTrue(cellsDiffer(pixels, PixelAtlas.FIRE_0, PixelAtlas.FIRE_1),
                "Flame variants FIRE_0 and FIRE_1 should differ");
        assertTrue(cellsDiffer(pixels, PixelAtlas.FIRE_0, PixelAtlas.TORCH_FIRE),
                "Dedicated TORCH_FIRE should differ from brazier FIRE_0");
        assertTrue(cellsDiffer(pixels, PixelAtlas.THRALL, PixelAtlas.THRALL_ATTACK),
                "Thrall attack pose should differ from idle pose");
        assertTrue(cellsDiffer(pixels, PixelAtlas.GOBLIN, PixelAtlas.GOBLIN_AIM),
                "Goblin archer aim pose should differ from idle");
        assertTrue(cellsDiffer(pixels, PixelAtlas.GOBLIN, PixelAtlas.GOBLIN_AFTER_SHOT),
                "Goblin archer after-shot pose should differ from idle");
        assertTrue(cellsDiffer(pixels, PixelAtlas.GOBLIN_AIM, PixelAtlas.GOBLIN_AFTER_SHOT),
                "Goblin archer after-shot pose should differ from aim pose");
        assertTrue(cellsDiffer(pixels, PixelAtlas.GOBLIN_ASSASSIN, PixelAtlas.GOBLIN_ASSASSIN_ATTACK),
                "Goblin assassin attack pose should differ from idle");
        assertTrue(cellsDiffer(pixels, PixelAtlas.OGRE, PixelAtlas.OGRE_ATTACK),
                "Ogre attack pose should differ from idle");
        assertTrue(cellsDiffer(pixels, PixelAtlas.OGRE, PixelAtlas.OGRE_ATTACK_2),
                "Ogre attack 2 pose should differ from idle");
        assertTrue(cellsDiffer(pixels, PixelAtlas.OGRE_ATTACK, PixelAtlas.OGRE_ATTACK_2),
                "Ogre attack poses should differ from each other");
        assertTrue(cellsDiffer(pixels, PixelAtlas.ORC, PixelAtlas.ORC_ATTACK),
                "Orc attack pose should differ from idle");
        assertTrue(cellsDiffer(pixels, PixelAtlas.BLOOD_SPLAT_0, PixelAtlas.BLOOD_SPLAT_1),
                "Blood splat variants BLOOD_SPLAT_0 and BLOOD_SPLAT_1 should differ");
        assertTrue(cellsDiffer(pixels, PixelAtlas.FLOOR_0, PixelAtlas.FLOOR_1),
                "Floor variants FLOOR_0 and FLOOR_1 should differ");

        // Ogre torso/belly center must remain solid opaque with no punched-out paper hole,
        // outer background cutout remains transparent, and Goblin Assassin back-arm gap is transparent
        assertOpaqueRegion(pixels, PixelAtlas.OGRE, 120, 112, 144, 140);
        assertOpaqueRegion(pixels, PixelAtlas.OGRE_ATTACK, 124, 108, 146, 138);
        assertEquals(0, cellAlpha(pixels, PixelAtlas.OGRE_ATTACK, 220, 40));
        assertEquals(0, cellAlpha(pixels, PixelAtlas.OGRE_ATTACK, 40, 60));
        assertEquals(0, cellAlpha(pixels, PixelAtlas.GOBLIN_ASSASSIN, 170, 108));

        // Every creature sprite (Thrall poses + all enemies) must expose valid eye anchors; props stay empty
        int[] creatureSprites = {
            PixelAtlas.THRALL, PixelAtlas.THRALL_ATTACK, PixelAtlas.THRALL_CAST,
            PixelAtlas.THRALL_GUARD, PixelAtlas.THRALL_HURT,
            PixelAtlas.ORC, PixelAtlas.ORC_ATTACK,
            PixelAtlas.GOBLIN, PixelAtlas.GOBLIN_AIM, PixelAtlas.GOBLIN_AFTER_SHOT,
            PixelAtlas.GOBLIN_ASSASSIN, PixelAtlas.GOBLIN_ASSASSIN_ATTACK,
            PixelAtlas.OGRE, PixelAtlas.OGRE_ATTACK, PixelAtlas.OGRE_ATTACK_2, PixelAtlas.RAT
        };
        for (int spr : creatureSprites) {
            float[] eyes = PixelAtlas.entityEyes(spr);
            assertTrue(eyes.length >= 2 && eyes.length % 2 == 0, "Expected eye anchors for sprite " + spr);
            for (float c : eyes) assertTrue(c > 0f && c < 1f);
        }
        assertEquals(0, PixelAtlas.entityEyes(PixelAtlas.BRAZIER).length);

        assertEquals(5, PixelAtlas.weaponGlows(PixelAtlas.ORC).length);
        assertEquals(5, PixelAtlas.weaponGlows(PixelAtlas.ORC_ATTACK).length);
        assertEquals(10, PixelAtlas.weaponGlows(PixelAtlas.GOBLIN_ASSASSIN).length);
        assertEquals(10, PixelAtlas.weaponGlows(PixelAtlas.GOBLIN_ASSASSIN_ATTACK).length);
        assertEquals(0, PixelAtlas.weaponGlows(PixelAtlas.GOBLIN).length);
        assertEquals(0, PixelAtlas.weaponGlows(PixelAtlas.OGRE).length);
        assertEquals(0, PixelAtlas.weaponGlows(PixelAtlas.THRALL).length);

        assertTrue(GdxView.GOBLIN_SCALE < 1f);
        assertTrue(GdxView.BRAZIER_SCALE < 1f);
        assertEquals(GdxView.GOBLIN_SCALE, GdxView.entityScale(PixelAtlas.GOBLIN));
        assertEquals(GdxView.GOBLIN_SCALE, GdxView.entityScale(PixelAtlas.GOBLIN_AIM));
        assertEquals(GdxView.GOBLIN_SCALE, GdxView.entityScale(PixelAtlas.GOBLIN_AFTER_SHOT));
        assertEquals(GdxView.GOBLIN_SCALE, GdxView.entityScale(PixelAtlas.GOBLIN_ASSASSIN));
        assertEquals(GdxView.GOBLIN_SCALE, GdxView.entityScale(PixelAtlas.GOBLIN_ASSASSIN_ATTACK));
        assertEquals(GdxView.BRAZIER_SCALE, GdxView.entityScale(PixelAtlas.BRAZIER));
        assertEquals(1f, GdxView.entityScale(PixelAtlas.THRALL));
        assertEquals(1f, GdxView.entityScale(PixelAtlas.ORC));
        assertEquals(1f, GdxView.entityScale(PixelAtlas.ORC_ATTACK));
        assertEquals(1f, GdxView.entityScale(PixelAtlas.OGRE));
        assertEquals(1f, GdxView.entityScale(PixelAtlas.OGRE_ATTACK));
        assertEquals(1f, GdxView.entityScale(PixelAtlas.OGRE_ATTACK_2));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.ORC));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.ORC_ATTACK));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.GOBLIN));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.GOBLIN_AIM));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.GOBLIN_AFTER_SHOT));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.GOBLIN_ASSASSIN));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.GOBLIN_ASSASSIN_ATTACK));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.OGRE));
        assertEquals(-1, PixelAtlas.naturalFacing(PixelAtlas.OGRE_ATTACK));
        assertEquals(1, PixelAtlas.naturalFacing(PixelAtlas.OGRE_ATTACK_2));
        assertEquals(1, PixelAtlas.naturalFacing(PixelAtlas.THRALL));

        assertEquals(PixelAtlas.ORC, PixelAtlas.entitySprite(PixelAtlas.ORC, PixelAtlas.POSE_IDLE));
        assertEquals(PixelAtlas.ORC_ATTACK, PixelAtlas.entitySprite(PixelAtlas.ORC, PixelAtlas.POSE_ATTACK));
        assertEquals(PixelAtlas.GOBLIN, PixelAtlas.entitySprite(PixelAtlas.GOBLIN, PixelAtlas.POSE_IDLE));
        assertEquals(PixelAtlas.GOBLIN_AIM, PixelAtlas.entitySprite(PixelAtlas.GOBLIN, PixelAtlas.POSE_ATTACK));
        assertEquals(PixelAtlas.GOBLIN_AFTER_SHOT, PixelAtlas.entitySprite(PixelAtlas.GOBLIN, PixelAtlas.POSE_AFTER_ATTACK));
        assertEquals(PixelAtlas.GOBLIN_ASSASSIN, PixelAtlas.entitySprite(PixelAtlas.GOBLIN_ASSASSIN, PixelAtlas.POSE_IDLE));
        assertEquals(PixelAtlas.GOBLIN_ASSASSIN_ATTACK, PixelAtlas.entitySprite(PixelAtlas.GOBLIN_ASSASSIN, PixelAtlas.POSE_ATTACK));
        assertEquals(PixelAtlas.OGRE, PixelAtlas.entitySprite(PixelAtlas.OGRE, PixelAtlas.POSE_IDLE));
        assertEquals(PixelAtlas.OGRE_ATTACK, PixelAtlas.entitySprite(PixelAtlas.OGRE, PixelAtlas.POSE_ATTACK));
        assertEquals(PixelAtlas.OGRE_ATTACK_2, PixelAtlas.entitySprite(PixelAtlas.OGRE, PixelAtlas.POSE_ATTACK_ALT));

        assertEquals(PixelAtlas.THRALL, PixelAtlas.thrallSprite(PixelAtlas.POSE_IDLE));
        assertEquals(PixelAtlas.THRALL_MOVE, PixelAtlas.thrallSprite(PixelAtlas.POSE_MOVE));
        assertEquals(PixelAtlas.THRALL_ATTACK, PixelAtlas.thrallSprite(PixelAtlas.POSE_ATTACK));
        assertEquals(PixelAtlas.THRALL_CAST, PixelAtlas.thrallSprite(PixelAtlas.POSE_CAST));
        assertEquals(PixelAtlas.THRALL_GUARD, PixelAtlas.thrallSprite(PixelAtlas.POSE_GUARD));
        assertEquals(PixelAtlas.THRALL_HURT, PixelAtlas.thrallSprite(PixelAtlas.POSE_HURT));

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

        assertEquals(PixelAtlas.THRALL, PixelAtlas.spriteForEntity(s, 0));
        assertEquals(PixelAtlas.ORC, PixelAtlas.spriteForEntity(s, s.indexOf("o1")));
        assertEquals(PixelAtlas.GOBLIN, PixelAtlas.spriteForEntity(s, s.indexOf("g1")));
        assertEquals(PixelAtlas.GOBLIN_ASSASSIN, PixelAtlas.spriteForEntity(s, s.indexOf("ga1")));
        assertEquals(PixelAtlas.OGRE, PixelAtlas.spriteForEntity(s, s.indexOf("og1")));
        assertEquals(PixelAtlas.RAT, PixelAtlas.spriteForEntity(s, s.indexOf("r1")));
        assertEquals(PixelAtlas.BRAZIER, PixelAtlas.spriteForEntity(s, s.indexOf("br1")));
        assertEquals(PixelAtlas.BARREL, PixelAtlas.spriteForEntity(s, s.indexOf("b1")));
        assertTrue(s.nockedEntity[s.indexOf("g1")]);
        assertFalse(s.nockedEntity[s.indexOf("ga1")]);
        assertTrue(sr.nockedEntity[sr.indexOf("g1")]);

        assertEquals("rusted cleaver", s.rightHand);
        assertEquals("torch [LIT]", s.leftHand);
        assertTrue(GdxView.hasLitTorch(s));
        assertEquals(5, s.packCount);
        assertEquals(Balance.PACK_MAX, s.packMax);
        assertEquals(5, s.packItems.length);

        arena.get("br1").removeTag(Tag.LIT);
        arena.thrall().inventory().equipped(EquipmentSlot.HAND_LEFT).tags().remove(Tag.LIT);
        arena.actor("o1").applyTag(Tag.FROZEN);
        Scene offAndFrozen = new Scene(arena);
        assertFalse(offAndFrozen.litEntity[offAndFrozen.indexOf("br1")]);
        assertFalse(GdxView.hasLitTorch(offAndFrozen));
        assertEquals(PixelAtlas.BRAZIER, PixelAtlas.spriteForEntity(offAndFrozen, offAndFrozen.indexOf("br1")));
        assertEquals(PixelAtlas.ORC, PixelAtlas.spriteForEntity(offAndFrozen, offAndFrozen.indexOf("o1")));
    }

    @Test
    void cameraClampsToViewportBoundsAndRespectsSmallMaps() {
        assertEquals(0f, GdxView.clampCam(-2f, 12, 6f));
        assertEquals(3.5f, GdxView.clampCam(3.5f, 12, 6f));
        assertEquals(6f, GdxView.clampCam(9f, 12, 6f));
        assertEquals(0f, GdxView.clampCam(-1f, 8, 4f));
        assertEquals(4f, GdxView.clampCam(7f, 8, 4f));
        assertEquals(0f, GdxView.clampCam(2f, 4, 6f));
    }

    private static int cellAlpha(int[] pixels, int cellIdx, int x, int y) {
        int ox = (cellIdx % PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
        int oy = (cellIdx / PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
        return pixels[(oy + y) * PixelAtlas.ATLAS_W + (ox + x)] & 0xFF;
    }

    private static void assertOpaqueRegion(int[] pixels, int cellIdx, int x0, int y0, int x1, int y1) {
        int ox = (cellIdx % PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
        int oy = (cellIdx / PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int alpha = pixels[(oy + y) * PixelAtlas.ATLAS_W + (ox + x)] & 0xFF;
                assertTrue(alpha >= 220, "Expected solid opaque pixel in cell " + cellIdx + " at (" + x + "," + y + "), alpha=" + alpha);
            }
        }
    }

    private static boolean cellsDiffer(int[] pixels, int a, int b) {
        int ax = (a % PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
        int ay = (a / PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
        int bx = (b % PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
        int by = (b / PixelAtlas.COLS) * PixelAtlas.STRIDE + PixelAtlas.PAD;
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
        assertEquals(PixelAtlas.THRALL, view.thrallDrawSprite(2));

        // Move left -> flips facing to -1 and selects THRALL_MOVE over SLIDE (0.4s)
        Vec2 start = arena.thrall().pos();
        arena.thrall().moveTo(new Vec2(start.x() - 1, start.y()));
        Scene s1 = new Scene(arena);
        view.scene(s1, s0);
        assertEquals(-1, view.thrallFacing());
        assertEquals(PixelAtlas.THRALL_MOVE, view.thrallDrawSprite(0));
        view.stepAnim(0.25f);
        assertEquals(PixelAtlas.THRALL_MOVE, view.thrallDrawSprite(0));
        view.stepAnim(0.20f);
        assertEquals(PixelAtlas.THRALL, view.thrallDrawSprite(1));

        // Strike to the right -> flips facing back to 1, holds THRALL_ATTACK (stab -> hold -> pull back)
        view.handle(new GameEvent.StageResult(1, 1, "strike o1", ExitCode.SUCCESS, 1, 4));
        assertEquals(1, view.thrallFacing());
        assertEquals(PixelAtlas.THRALL_ATTACK, view.thrallDrawSprite(0));
        view.stepAnim(0.18f);
        assertEquals(PixelAtlas.THRALL_ATTACK, view.thrallDrawSprite(0));
        view.stepAnim(0.32f);
        assertEquals(PixelAtlas.THRALL_ATTACK, view.thrallDrawSprite(0));
        view.stepAnim(0.35f);
        assertEquals(PixelAtlas.THRALL, view.thrallDrawSprite(0));

        assertTrue(GdxView.lungeFactor(0.61f, 0.72f, true) > 0.9f);
        assertEquals(1.0f, GdxView.lungeFactor(0.42f, 0.72f, true), 1e-4f);
        assertEquals(0.0f, GdxView.lungeFactor(0.0f, 0.72f, true), 1e-4f);

        // Cast & Guard
        view.handle(new GameEvent.StageResult(1, 1, "bolt o1", ExitCode.SUCCESS, 1, 3));
        assertEquals(PixelAtlas.THRALL_CAST, view.thrallDrawSprite(0));
        view.stepAnim(0.20f);
        assertEquals(PixelAtlas.THRALL_CAST, view.thrallDrawSprite(0));
        view.stepAnim(0.55f);
        assertEquals(PixelAtlas.THRALL, view.thrallDrawSprite(0));

        view.handle(new GameEvent.StageResult(1, 1, "guard", ExitCode.SUCCESS, 1, 2));
        assertEquals(PixelAtlas.THRALL_GUARD, view.thrallDrawSprite(0));
        view.stepAnim(0.50f);

        // Hurt flinch when damaged while idle
        arena.thrall().takeDamage(3);
        Scene sHurt = new Scene(arena);
        view.scene(sHurt, s1);
        assertEquals(PixelAtlas.THRALL_HURT, view.thrallDrawSprite(0));
        view.stepAnim(0.30f);
        assertEquals(PixelAtlas.THRALL, view.thrallDrawSprite(0));

        // Frozen Thrall locks to idle
        arena.thrall().applyTag(Tag.FROZEN);
        view.scene(new Scene(arena), sHurt);
        assertEquals(PixelAtlas.THRALL, view.thrallDrawSprite(3));
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

    @Test
    void enemyPosesAnimateArcherAssassinAndOgreCombat() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        GdxView view = new GdxView(1L, true);
        Scene s0 = new Scene(arena);
        view.scene(s0, null);

        int gi = s0.indexOf("g1");
        int gai = s0.indexOf("ga1");
        int ogi = s0.indexOf("og1");

        // Idle poses
        assertEquals(PixelAtlas.GOBLIN, view.entityDrawSprite(gi, 0));
        assertEquals(PixelAtlas.GOBLIN_ASSASSIN, view.entityDrawSprite(gai, 0));
        assertEquals(PixelAtlas.OGRE, view.entityDrawSprite(ogi, 0));

        // Goblin Archer nocking does NOT trigger attack aim pose
        view.handle(new GameEvent.ActorBeat("g1", "Goblin Archer", new Vec2(9, 2), new Vec2(9, 2),
                List.of("Goblin Archer nocks an arrow.")));
        assertEquals(PixelAtlas.GOBLIN, view.entityDrawSprite(gi, 0));

        // Goblin Archer firing -> GOBLIN_AIM -> step past 0.36s -> GOBLIN_AFTER_SHOT -> expires
        arena.actor("g1").fireNocked();
        view.handle(new GameEvent.ActorBeat("g1", "Goblin Archer", new Vec2(9, 2), new Vec2(9, 2),
                List.of("Goblin Archer's arrow hits Thrall's torso for 4 dmg. [60% | roll 50 vs 41+]")));
        Scene sShot = new Scene(arena);
        view.scene(sShot, s0);
        assertEquals(PixelAtlas.GOBLIN_AIM, view.entityDrawSprite(gi, 0));
        view.stepAnim(0.20f);
        assertEquals(PixelAtlas.GOBLIN_AIM, view.entityDrawSprite(gi, 0));
        view.stepAnim(0.20f);
        assertEquals(PixelAtlas.GOBLIN_AFTER_SHOT, view.entityDrawSprite(gi, 0));
        view.stepAnim(0.55f);
        assertEquals(PixelAtlas.GOBLIN_AFTER_SHOT, view.entityDrawSprite(gi, 0),
                "Un-nocked archer should remain in after-shot pose without arrow");

        // Re-nocking returns archer to idle (bow down with arrow), and overwatch raises bow (GOBLIN_AIM)
        arena.actor("g1").nock();
        Scene sNocked = new Scene(arena);
        view.scene(sNocked, sShot);
        assertEquals(PixelAtlas.GOBLIN, view.entityDrawSprite(gi, 0));

        arena.actor("g1").ready(com.archon.model.Actor.Trigger.ON_MOVEMENT_IN_LOS, "fires on movement", 6);
        Scene sOverwatch = new Scene(arena);
        view.scene(sOverwatch, sNocked);
        assertEquals(PixelAtlas.GOBLIN_AIM, view.entityDrawSprite(gi, 0));

        // InterruptFired transitions archer to GOBLIN_AFTER_SHOT and stays there once un-nocked
        view.handle(new GameEvent.InterruptFired("g1", "fires on movement", 6));
        arena.actor("g1").fireNocked();
        arena.actor("g1").spendReadied();
        Scene sAfterInterrupt = new Scene(arena);
        view.scene(sAfterInterrupt, sOverwatch);
        assertEquals(PixelAtlas.GOBLIN_AFTER_SHOT, view.entityDrawSprite(gi, 0));
        view.stepAnim(0.60f);
        assertEquals(PixelAtlas.GOBLIN_AFTER_SHOT, view.entityDrawSprite(gi, 0));

        // Goblin Assassin melee attack -> GOBLIN_ASSASSIN_ATTACK -> expires back to GOBLIN_ASSASSIN
        view.handle(new GameEvent.ActorBeat("ga1", "Goblin Assassin", new Vec2(6, 5), new Vec2(3, 4),
                List.of("Goblin Assassin's twin daggers hits Thrall's torso for 5 dmg. [70% | roll 50 vs 31+]")));
        assertEquals(PixelAtlas.GOBLIN_ASSASSIN_ATTACK, view.entityDrawSprite(gai, 0));
        view.stepAnim(0.85f);
        assertEquals(PixelAtlas.GOBLIN_ASSASSIN, view.entityDrawSprite(gai, 0));

        // Orc melee attack -> ORC_ATTACK -> expires back to ORC
        int oi = s0.indexOf("o1");
        assertEquals(PixelAtlas.ORC, view.entityDrawSprite(oi, 0));
        view.handle(new GameEvent.ActorBeat("o1", "Orc Guard", new Vec2(7, 4), new Vec2(3, 4),
                List.of("Orc Guard's iron sword hits Thrall's torso for 5 dmg. [70% | roll 50 vs 31+]")));
        assertEquals(PixelAtlas.ORC_ATTACK, view.entityDrawSprite(oi, 0));
        view.stepAnim(0.85f);
        assertEquals(PixelAtlas.ORC, view.entityDrawSprite(oi, 0));

        // Ogre non-attack ("raises guard") stays OGRE; melee attacks randomly pick OGRE_ATTACK / OGRE_ATTACK_2
        view.handle(new GameEvent.ActorBeat("og1", "Ogre Brute", new Vec2(9, 5), new Vec2(8, 5),
                List.of("Ogre Brute advances to (8,5).", "Ogre Brute raises guard.")));
        assertEquals(PixelAtlas.OGRE, view.entityDrawSprite(ogi, 0));

        boolean sawAttack1 = false, sawAttack2 = false;
        for (int attempt = 0; attempt < 16; attempt++) {
            view.handle(new GameEvent.ActorBeat("og1", "Ogre Brute", new Vec2(8, 5), new Vec2(3, 5),
                    List.of("Ogre Brute's fists misses Thrall's legs. [55% | roll 20 vs 46+]")));
            int spr = view.entityDrawSprite(ogi, 0);
            assertTrue(spr == PixelAtlas.OGRE_ATTACK || spr == PixelAtlas.OGRE_ATTACK_2,
                    "Expected OGRE_ATTACK or OGRE_ATTACK_2, got " + spr);
            if (spr == PixelAtlas.OGRE_ATTACK) sawAttack1 = true;
            else sawAttack2 = true;
            view.stepAnim(0.85f);
            assertEquals(spr, view.entityDrawSprite(ogi, 0),
                    "Ogre attack should still hold attack texture at 0.85s");
            view.stepAnim(0.45f);
            assertEquals(PixelAtlas.OGRE, view.entityDrawSprite(ogi, 0));
        }
        assertTrue(sawAttack1 && sawAttack2, "Expected Ogre to randomly use both attack animations");
    }

    @Test
    void bloodSplatsSpawnOnCreatureHitsWithoutAffectingWorldState() {
        World arena = Scenario.magicArena(new Dice.Seeded(1L));
        GdxView view = new GdxView(1L, true);
        Scene s0 = new Scene(arena);
        view.scene(s0, null);
        assertEquals(0, view.bloodSplatCount());

        // Damaging a prop leaves zero blood splats
        arena.get("c1").takeDamage(4);
        Scene sProp = new Scene(arena);
        view.scene(sProp, s0);
        assertEquals(0, view.bloodSplatCount());

        // Normal creature hit spawns 2 blood splats without mutating world tags or entity state
        Vec2 ratPos = arena.actor("r1").pos();
        int tagsBefore = arena.tile(ratPos).tags().size();
        arena.actor("r1").takeDamage(5);
        Scene sHit = new Scene(arena);
        view.scene(sHit, sProp);
        assertEquals(2, view.bloodSplatCount());
        assertEquals(tagsBefore, arena.tile(ratPos).tags().size());
        assertFalse(arena.actor("r1").has(Tag.BLEEDING));

        // Crit creature hit spawns 4 blood splats
        view.handle(new GameEvent.Narrative("CRITICAL! Cleaver hits Orc Guard's torso for 8 dmg. [70% | roll 90 vs 31+]"));
        arena.actor("o1").takeDamage(8);
        Scene sCrit = new Scene(arena);
        view.scene(sCrit, sHit);
        assertEquals(6, view.bloodSplatCount());

        // Lethal creature hit spawns 6 blood splats
        arena.actor("ga1").takeDamage(99);
        Scene sKill = new Scene(arena);
        view.scene(sKill, sCrit);
        assertEquals(12, view.bloodSplatCount());
    }
}
