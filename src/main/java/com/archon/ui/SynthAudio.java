package com.archon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * In-memory procedural retro PCM synth SFX bank. Zero external audio assets.
 * Starts muted so headless tests never touch Gdx.audio; init() enables playback.
 */
public final class SynthAudio {
    public static final int KEY = 0, SUBMIT = 1, REJECT = 2, HIT = 3, SHOOT = 4, SPLASH = 5, ZAP = 6, BOOM = 7, COUNT = 8;
    private static final int SAMPLE_RATE = 22050;

    private final Sound[] sounds = new Sound[COUNT];
    public boolean muted = true;

    public void init() {
        if (Gdx.audio == null) return;
        for (int k = 0; k < COUNT; k++) {
            final byte[] wav = buildWav(k);
            sounds[k] = Gdx.audio.newSound(new FileHandle("sfx_" + k + ".wav") {
                @Override
                public InputStream read() {
                    return new ByteArrayInputStream(wav);
                }
            });
        }
        muted = false;
    }

    public void play(int kind, float volume) {
        if (muted || kind < 0 || kind >= COUNT || sounds[kind] == null) return;
        sounds[kind].play(volume);
    }

    public void dispose() {
        for (int i = 0; i < COUNT; i++) {
            if (sounds[i] != null) {
                sounds[i].dispose();
                sounds[i] = null;
            }
        }
    }

    /** Generates a 44-byte RIFF/WAVE header + 16-bit mono 22.05kHz PCM waveform. */
    public static byte[] buildWav(int kind) {
        float dur = switch (kind) {
            case KEY -> 0.018f;
            case SUBMIT -> 0.075f;
            case REJECT -> 0.150f;
            case HIT -> 0.120f;
            case SHOOT -> 0.110f;
            case SPLASH -> 0.150f;
            case ZAP -> 0.190f;
            case BOOM -> 0.300f;
            default -> throw new IllegalArgumentException("Unknown sound kind: " + kind);
        };
        int samples = (int) (SAMPLE_RATE * dur);
        int dataBytes = samples * 2;
        byte[] out = new byte[44 + dataBytes];

        // RIFF header
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putIntLE(out, 4, 36 + dataBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putIntLE(out, 16, 16);
        putShortLE(out, 20, 1);
        putShortLE(out, 22, 1);
        putIntLE(out, 24, SAMPLE_RATE);
        putIntLE(out, 28, SAMPLE_RATE * 2);
        putShortLE(out, 32, 2);
        putShortLE(out, 34, 16);
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putIntLE(out, 40, dataBytes);

        int rng = 0x9E3779B9 ^ (kind * 0x45D9F3B);
        double phase = 0.0;
        for (int i = 0; i < samples; i++) {
            float u = i / (float) samples;
            float env = 1f - u;
            rng ^= rng << 13;
            rng ^= rng >>> 17;
            rng ^= rng << 5;
            float noise = ((rng & 0xFFFF) / 32768f) - 1f;

            double s = 0.0;
            switch (kind) {
                case KEY -> {
                    phase += 2.0 * Math.PI * 1400.0 / SAMPLE_RATE;
                    float click = i < 16 ? (1f - i / 16f) * 0.45f : 0f;
                    s = (Math.sin(phase) * env * env * 0.55 + click * noise) * 0.35;
                }
                case SUBMIT -> {
                    double freq = u < 0.5f ? 540.0 : 810.0;
                    phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    double stepEnv = u < 0.5f ? (1f - u * 0.6f) : (1f - (u - 0.5f) * 2f);
                    s = Math.sin(phase) * stepEnv * 0.45;
                }
                case REJECT -> {
                    phase += 2.0 * Math.PI * 145.0 / SAMPLE_RATE;
                    double frac = (phase / (2.0 * Math.PI)) % 1.0;
                    double saw = 2.0 * frac - 1.0;
                    s = saw * env * 0.45;
                }
                case HIT -> {
                    double freq = 190.0 - 140.0 * u;
                    phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    float burst = u < 0.25f ? (1f - u * 4f) * 0.5f : 0f;
                    s = (Math.sin(phase) * env * 0.75 + noise * burst) * 0.70;
                }
                case SHOOT -> {
                    double freq = 420.0 - 220.0 * u;
                    phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    s = (Math.sin(phase) * 0.35 + noise * 0.55) * env * 0.55;
                }
                case SPLASH -> {
                    double wobble = 440.0 + 180.0 * Math.sin(2.0 * Math.PI * 35.0 * (i / (double) SAMPLE_RATE));
                    phase += 2.0 * Math.PI * wobble / SAMPLE_RATE;
                    s = (Math.sin(phase) * 0.5 + noise * 0.35) * env * 0.50;
                }
                case ZAP -> {
                    double freq = 1200.0 - 880.0 * u + 160.0 * Math.sin(2.0 * Math.PI * 65.0 * (i / (double) SAMPLE_RATE));
                    phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    double sq = Math.sin(phase) >= 0 ? 1.0 : -1.0;
                    s = (sq * 0.45 + noise * 0.35) * env * 0.55;
                }
                case BOOM -> {
                    double freq = 85.0 - 57.0 * u;
                    phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    s = (Math.sin(phase) * 0.65 + noise * 0.55) * (env * env * 0.8 + env * 0.2) * 0.75;
                }
            }
            int pcm = Math.max(-32767, Math.min(32767, (int) Math.round(s * 32767.0)));
            putShortLE(out, 44 + i * 2, pcm);
        }
        return out;
    }

    private static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
        b[off + 2] = (byte) ((v >>> 16) & 0xFF);
        b[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    private static void putShortLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
    }
}
