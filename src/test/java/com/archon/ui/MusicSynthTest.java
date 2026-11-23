package com.archon.ui;

import com.archon.ui.audio.Dsp;
import com.archon.ui.audio.MusicSynth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicSynthTest {

    @Test
    void streamingMultiTrackConstantLoudnessTensionAndMute() {
        short[] buf = new short[MusicSynth.BLOCK_FRAMES * 2];
        int blocksPerSec = Math.ceilDiv(Dsp.SAMPLE_RATE, MusicSynth.BLOCK_FRAMES);

        // 1. All 3 tracks across low/mid/high HP tension stay audible, steady in loudness, and clip-free
        for (int track = 0; track < MusicSynth.TRACK_COUNT; track++) {
            MusicSynth music = new MusicSynth();
            music.muted = false;
            music.musicMuted = false;
            music.forcedTrack = track;

            // Warm up 5s past bar 0 intro at full HP (tension = 0.0)
            music.targetTension = 0f;
            renderBlocksAndCheck(music, buf, blocksPerSec * 5, "track " + track + " warmup");
            double rmsFullHp = renderBlocksAndCheck(music, buf, blocksPerSec * 4, "track " + track + " t=0.0");
            assertTrue(rmsFullHp > 200.0, "Track " + track + " RMS too low at t=0.0: " + rmsFullHp);

            // Mid HP (tension = 0.5)
            music.targetTension = 0.5f;
            renderBlocksAndCheck(music, buf, blocksPerSec * 2, "track " + track + " settle t=0.5");
            double rmsMid = renderBlocksAndCheck(music, buf, blocksPerSec * 4, "track " + track + " t=0.5");
            assertTrue(rmsMid > 200.0, "Track " + track + " RMS too low at t=0.5: " + rmsMid);

            // Low HP crisis (tension = 0.9) — verify constant loudness (no volume collapse/spike)
            music.targetTension = 0.9f;
            renderBlocksAndCheck(music, buf, blocksPerSec * 2, "track " + track + " settle t=0.9");
            double rmsLowHp = renderBlocksAndCheck(music, buf, blocksPerSec * 4, "track " + track + " t=0.9");
            assertTrue(rmsLowHp > 200.0, "Track " + track + " RMS too low at t=0.9: " + rmsLowHp);

            double ratio = rmsLowHp / rmsFullHp;
            assertTrue(ratio >= 0.5 && ratio <= 2.0,
                    "Track " + track + " loudness drifted too much across HP tension: ratio=" + ratio
                            + " (full=" + rmsFullHp + ", low=" + rmsLowHp + ")");
        }

        // 2. Explore <-> Combat maintains constant loudness across 1.5s crossfade
        MusicSynth combatCheck = new MusicSynth();
        combatCheck.muted = false;
        combatCheck.musicMuted = false;
        combatCheck.forcedTrack = -1;
        combatCheck.seedRng(0x12345678);
        combatCheck.inCombat = false;
        renderBlocksAndCheck(combatCheck, buf, blocksPerSec * 4, "explore warmup");
        double rmsExplore = renderBlocksAndCheck(combatCheck, buf, blocksPerSec * 4, "explore steady");
        assertTrue(rmsExplore > 200.0, "Explore RMS too low: " + rmsExplore);

        combatCheck.inCombat = true;
        renderBlocksAndCheck(combatCheck, buf, blocksPerSec * 2, "combat crossfade");
        double rmsCombat = renderBlocksAndCheck(combatCheck, buf, blocksPerSec * 4, "combat steady");
        assertTrue(rmsCombat > 200.0, "Combat RMS too low: " + rmsCombat);
        double combatRatio = rmsCombat / rmsExplore;
        assertTrue(combatRatio >= 0.65 && combatRatio <= 1.55,
                "Combat/Explore loudness drifted: ratio=" + combatRatio
                        + " (explore=" + rmsExplore + ", combat=" + rmsCombat + ")");

        combatCheck.inCombat = false;
        renderBlocksAndCheck(combatCheck, buf, blocksPerSec * 2, "explore return crossfade");
        double rmsReturn = renderBlocksAndCheck(combatCheck, buf, blocksPerSec * 4, "explore return steady");
        assertTrue(rmsReturn > 200.0, "Explore return RMS too low: " + rmsReturn);
        double returnRatio = rmsReturn / rmsExplore;
        assertTrue(returnRatio >= 0.65 && returnRatio <= 1.55,
                "Explore return loudness drifted: ratio=" + returnRatio);

        // 3. Verify asymmetric War Horn brass + FDN4 on Track 2 produces zero DC offset runaway
        MusicSynth brassCheck = new MusicSynth();
        brassCheck.muted = false;
        brassCheck.musicMuted = false;
        brassCheck.forcedTrack = 2;
        brassCheck.targetTension = 0.6f;
        renderBlocksAndCheck(brassCheck, buf, blocksPerSec * 5, "brass warmup");
        long sumL = 0, sumR = 0;
        int dcBlocks = blocksPerSec * 2;
        for (int b = 0; b < dcBlocks; b++) {
            brassCheck.renderBlock(buf, MusicSynth.BLOCK_FRAMES);
            for (int i = 0; i < MusicSynth.BLOCK_FRAMES; i++) {
                sumL += buf[i * 2];
                sumR += buf[i * 2 + 1];
            }
        }
        double totalFrames = (double) dcBlocks * MusicSynth.BLOCK_FRAMES;
        double meanL = sumL / totalFrames, meanR = sumR / totalFrames;
        assertTrue(Math.abs(meanL) < 300.0 && Math.abs(meanR) < 300.0,
                "Track 2 brass DC offset too high: meanL=" + meanL + " meanR=" + meanR);

        // 3. F3 musicMuted keeps cavern ambient running
        brassCheck.musicMuted = true;
        renderBlocksAndCheck(brassCheck, buf, blocksPerSec / 2, "F3 settle");
        double ambRms = renderBlocksAndCheck(brassCheck, buf, 4, "F3 ambient");
        assertTrue(ambRms > 10.0, "Ambient bed should remain audible when musicMuted=true: " + ambRms);

        // 4. F2 muted silences completely once gain ramps to 0
        brassCheck.muted = true;
        renderBlocksAndCheck(brassCheck, buf, blocksPerSec / 5, "F2 settle");
        brassCheck.renderBlock(buf, MusicSynth.BLOCK_FRAMES);
        for (int i = 0; i < buf.length; i++) {
            assertEquals(0, buf[i], "Muted output should be exact 0 at sample " + i);
        }

        // 5. Unmute restores clean non-zero output
        brassCheck.muted = false;
        brassCheck.musicMuted = false;
        double unmuteRms = renderBlocksAndCheck(brassCheck, buf, blocksPerSec / 2, "unmute");
        assertTrue(unmuteRms > 200.0, "Unmuted audio should resume cleanly: " + unmuteRms);
    }

    private static double renderBlocksAndCheck(MusicSynth music, short[] buf, int blocks, String label) {
        double sumSq = 0.0;
        long totalSamples = 0;
        int railRunL = 0, railRunR = 0;
        for (int b = 0; b < blocks; b++) {
            music.renderBlock(buf, MusicSynth.BLOCK_FRAMES);
            for (int i = 0; i < MusicSynth.BLOCK_FRAMES; i++) {
                short l = buf[i * 2];
                short r = buf[i * 2 + 1];
                sumSq += (double) l * l + (double) r * r;
                totalSamples += 2;
                railRunL = (l == 32767 || l == -32767) ? railRunL + 1 : 0;
                railRunR = (r == 32767 || r == -32767) ? railRunR + 1 : 0;
                assertTrue(railRunL < 16 && railRunR < 16, "Stuck rail clipping in " + label);
            }
        }
        return Math.sqrt(sumSq / totalSamples);
    }
}
