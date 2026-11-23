package com.archon.ui;

import com.archon.ui.audio.Dsp;
import com.archon.ui.audio.MusicSynth;
import com.archon.ui.audio.SfxGen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

@EnabledIfEnvironmentVariable(named = "AUDIO_DUMP", matches = "1")
class AudioDumpTest {

    @Test
    void dumpBeforeAfterAudio() throws Exception {
        String dirEnv = System.getenv("AUDIO_DUMP_DIR");
        Path outDir = Path.of((dirEnv == null || dirEnv.isBlank()) ? "target/audio" : dirEnv);
        Files.createDirectories(outDir);

        int blocksPerSec = Math.ceilDiv(Dsp.SAMPLE_RATE, MusicSynth.BLOCK_FRAMES);
        short[] block = new short[MusicSynth.BLOCK_FRAMES * 2];

        for (int track = 0; track < MusicSynth.TRACK_COUNT; track++) {
            MusicSynth music = new MusicSynth();
            music.muted = false;
            music.musicMuted = false;
            music.forcedTrack = track;
            music.seedRng(0x12345678 + track);

            int b0 = blocksPerSec * 20;
            int b1 = blocksPerSec * 10;
            int totalBlocks = b0 + b1;
            int totalFrames = totalBlocks * MusicSynth.BLOCK_FRAMES;
            short[] pcm = new short[totalFrames * 2];

            music.targetTension = 0f;
            int pos = 0;
            for (int b = 0; b < b0; b++) {
                music.renderBlock(block, MusicSynth.BLOCK_FRAMES);
                System.arraycopy(block, 0, pcm, pos, block.length);
                pos += block.length;
            }
            music.targetTension = 0.8f;
            for (int b = 0; b < b1; b++) {
                music.renderBlock(block, MusicSynth.BLOCK_FRAMES);
                System.arraycopy(block, 0, pcm, pos, block.length);
                pos += block.length;
            }

            double rms = calcRms(pcm);
            System.out.printf("AUDIO_DUMP %s/music_track%d.wav RMS=%.2f%n", outDir, track, rms);
            writeStereoWav(outDir.resolve("music_track" + track + ".wav"), pcm, totalFrames);
        }

        {
            MusicSynth music = new MusicSynth();
            music.muted = false;
            music.musicMuted = false;
            music.forcedTrack = -1;
            music.seedRng(0x12345678);

            int b0 = blocksPerSec * 15;
            int b1 = blocksPerSec * 15;
            int totalBlocks = b0 + b1;
            int totalFrames = totalBlocks * MusicSynth.BLOCK_FRAMES;
            short[] pcm = new short[totalFrames * 2];

            music.inCombat = false;
            int pos = 0;
            for (int b = 0; b < b0; b++) {
                music.renderBlock(block, MusicSynth.BLOCK_FRAMES);
                System.arraycopy(block, 0, pcm, pos, block.length);
                pos += block.length;
            }
            music.inCombat = true;
            for (int b = 0; b < b1; b++) {
                music.renderBlock(block, MusicSynth.BLOCK_FRAMES);
                System.arraycopy(block, 0, pcm, pos, block.length);
                pos += block.length;
            }

            double rms = calcRms(pcm);
            System.out.printf("AUDIO_DUMP %s/music_explore_to_combat.wav RMS=%.2f%n", outDir, rms);
            writeStereoWav(outDir.resolve("music_explore_to_combat.wav"), pcm, totalFrames);
        }

        for (int k = 0; k < SfxGen.COUNT; k++) {
            int vars = SfxGen.variantsFor(k);
            for (int v = 0; v < vars; v++) {
                byte[] wav = SfxGen.buildWav(k, v);
                Files.write(outDir.resolve("sfx_" + k + "_" + v + ".wav"), wav);
            }
        }
    }

    private static double calcRms(short[] pcm) {
        double sumSq = 0.0;
        for (int i = 0; i < pcm.length; i++) {
            sumSq += (double) pcm[i] * pcm[i];
        }
        return Math.sqrt(sumSq / pcm.length);
    }

    private static void writeStereoWav(Path path, short[] pcm, int frames) throws Exception {
        byte[] wav = new byte[44 + frames * 4];
        Dsp.writeWavHeader(wav, 2, frames);
        for (int i = 0; i < pcm.length; i++) {
            Dsp.putShortLE(wav, 44 + i * 2, pcm[i]);
        }
        Files.write(path, wav);
    }
}
