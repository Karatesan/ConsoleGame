package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.model.Dice;
import com.archon.model.Tag;
import com.archon.model.World;
import org.junit.jupiter.api.Test;

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

        assertEquals(PixelAtlas.LOOT_BLADE, PixelAtlas.spriteForLoot(Scene.LOOT_BLADE));
        assertEquals(PixelAtlas.LOOT_OIL, PixelAtlas.spriteForLoot(Scene.LOOT_FLASK_OIL));
        assertEquals(PixelAtlas.LOOT_WATER, PixelAtlas.spriteForLoot(Scene.LOOT_FLASK_WATER));
        assertEquals(PixelAtlas.LOOT_ACID, PixelAtlas.spriteForLoot(Scene.LOOT_FLASK_ACID));
        assertEquals(PixelAtlas.LOOT_STONE, PixelAtlas.spriteForLoot(Scene.LOOT_STONE));
    }

    @Test
    void synthAudioBuildsValidRiffWaveForEverySoundKind() {
        for (int k = 0; k < SynthAudio.COUNT; k++) {
            byte[] wav = SynthAudio.buildWav(k);
            assertTrue(wav.length > 44, "WAV too short for kind " + k);
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
            assertEquals('d', wav[36]);
            assertEquals('a', wav[37]);
            assertEquals('t', wav[38]);
            assertEquals('a', wav[39]);

            boolean nonZero = false;
            for (int i = 44; i < wav.length; i++) {
                if (wav[i] != 0) {
                    nonZero = true;
                    break;
                }
            }
            assertTrue(nonZero, "PCM samples should be non-zero for kind " + k);
        }
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
        assertEquals(PixelAtlas.ORC_1, PixelAtlas.spriteForEntity(s, s.indexOf("o1"), 1));
        assertEquals(PixelAtlas.GOBLIN_0, PixelAtlas.spriteForEntity(s, s.indexOf("g1"), 0));
        assertEquals(PixelAtlas.OGRE_0, PixelAtlas.spriteForEntity(s, s.indexOf("og1"), 0));
        assertEquals(PixelAtlas.RAT_0, PixelAtlas.spriteForEntity(s, s.indexOf("r1"), 0));
        assertEquals(PixelAtlas.BRAZIER_1, PixelAtlas.spriteForEntity(s, s.indexOf("br1"), 1));
        assertEquals(PixelAtlas.BARREL, PixelAtlas.spriteForEntity(s, s.indexOf("b1"), 0));
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
}
