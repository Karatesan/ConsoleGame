package com.archon.ui.audio;

import java.io.IOException;
import java.io.InputStream;

/** Static-cached loader for studio 44.1 kHz 16-bit mono PCM SFX WAVs (zero libGDX dependencies). */
public final class SfxGen {
    public static final int KEY = 0, SUBMIT = 1, REJECT = 2, HIT = 3, SHOOT = 4, SPLASH = 5,
            ZAP = 6, BOOM = 7, CRIT = 8, FIRE = 9, SIZZLE = 10, SWING = 11, STEP = 12,
            PSIONIC = 13, SHATTER = 14, HEAL = 15,
            HIT_PROP = 16, STING_KILL = 17, STING_DEATH = 18, STING_BLOOD = 19, SIPHON = 20,
            BLOOD_BOLT = 21, COUNT = 22;
    public static final int VARIANTS = 4;

    private static final String BASE = "/audio/sfx/";
    private static final String[] NAMES = {
            "key", "submit", "reject", "hit", "shoot", "splash",
            "zap", "boom", "crit", "fire", "sizzle", "swing", "step",
            "psionic", "shatter", "heal",
            "hit_prop", "sting_kill", "sting_death", "sting_blood", "siphon", "blood_bolt"
    };
    private static final byte[][][] WAVS = loadAll();

    private SfxGen() {}

    public static int variantsFor(int kind) {
        if (kind < 0 || kind >= COUNT) throw new IllegalArgumentException("Unknown sound kind: " + kind);
        return switch (kind) {
            case SUBMIT, REJECT, PSIONIC, HEAL, STING_KILL, STING_DEATH, STING_BLOOD -> 1;
            case CRIT -> 3;
            default -> VARIANTS;
        };
    }

    public static byte[] buildWav(int kind) {
        return buildWav(kind, 0);
    }

    /** Returns canonical 44-byte RIFF/WAVE + 16-bit mono 44.1 kHz PCM bytes for the given SFX and variant. */
    public static byte[] buildWav(int kind, int variant) {
        if (kind < 0 || kind >= COUNT) throw new IllegalArgumentException("Unknown sound kind: " + kind);
        byte[][] pool = WAVS[kind];
        if (variant < 0 || variant >= pool.length) {
            throw new IllegalArgumentException("Invalid variant " + variant + " for kind " + kind);
        }
        return pool[variant];
    }

    private static byte[][][] loadAll() {
        byte[][][] all = new byte[COUNT][][];
        for (int k = 0; k < COUNT; k++) {
            int n = variantsFor(k);
            all[k] = new byte[n][];
            for (int v = 0; v < n; v++) {
                all[k][v] = loadWav(BASE + NAMES[k] + "_" + v + ".wav");
            }
        }
        return all;
    }

    private static byte[] loadWav(String path) {
        byte[] raw;
        try (InputStream in = SfxGen.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing SFX resource: " + path);
            raw = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading SFX resource: " + path, e);
        }
        if (raw.length <= 44 || raw[0] != 'R' || raw[1] != 'I' || raw[2] != 'F' || raw[3] != 'F'
                || raw[8] != 'W' || raw[9] != 'A' || raw[10] != 'V' || raw[11] != 'E'
                || raw[12] != 'f' || raw[13] != 'm' || raw[14] != 't' || raw[15] != ' '
                || raw[36] != 'd' || raw[37] != 'a' || raw[38] != 't' || raw[39] != 'a'
                || u16LE(raw, 20) != 1 || u16LE(raw, 22) != 1
                || u32LE(raw, 24) != Dsp.SAMPLE_RATE || u16LE(raw, 34) != 16
                || u32LE(raw, 40) != raw.length - 44) {
            throw new IllegalStateException("Invalid canonical 44.1kHz mono16 WAV: " + path);
        }
        return raw;
    }

    private static int u16LE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static int u32LE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8)
                | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }
}
