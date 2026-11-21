package com.archon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * In-memory procedural PCM audio engine (44.1 kHz):
 * - 16 spatialized mono SFX kinds x up to 4 baked variants (inharmonic modal synthesis,
 *   dispersion Karplus-Strong, phase-distorted plasma arcs, formant psionics, crystal bell
 *   harmonics, and LP-damped Schroeder reverb with arcane shimmer)
 * - Seamless 4.0 s stereo cavern/brazier ambient bed + synchronized 16.0 s exploration & combat stems
 * Zero external audio assets; zero allocations during play(). Starts muted for headless tests.
 */
public final class SynthAudio {
    public static final int KEY = 0, SUBMIT = 1, REJECT = 2, HIT = 3, SHOOT = 4, SPLASH = 5,
            ZAP = 6, BOOM = 7, CRIT = 8, FIRE = 9, SIZZLE = 10, SWING = 11, STEP = 12,
            PSIONIC = 13, SHATTER = 14, HEAL = 15, COUNT = 16;
    public static final int VARIANTS = 4;
    public static final int COMBAT_VARIANTS = 3;

    private static final int SAMPLE_RATE = 44100;
    private static final float AMBIENT_VOL = 0.14f;
    private static final float THEME_VOL = 0.22f;
    private static final float COMBAT_VOL = 0.34f;
    private static final long MIN_RETRIGGER_NS = 22_000_000L;
    // Mutually prime comb/allpass lengths tuned for a wider, smoother stone chamber
    private static final int[] COMB_LEN = {1559, 1621, 1493, 1427};
    private static final int[] AP_LEN = {227, 557, 373};

    private final Sound[][] sounds = new Sound[COUNT][];
    private final int[] variantCursor = new int[COUNT];
    private final long[] lastPlayNs = new long[COUNT];
    private Sound ambient, theme;
    private long ambientId = -1L, themeId = -1L;
    private final Sound[] combatTracks = new Sound[COMBAT_VARIANTS];
    private final long[] combatIds = {-1L, -1L, -1L};
    private int activeCombatVar = 0;
    private boolean inCombat = false;
    private float combatMix = 0f;
    private int playRng = 0x51A7F3B9;
    public boolean muted = true;
    public boolean musicMuted = false;

    public void init() {
        if (Gdx.audio == null) return;
        long mix = System.nanoTime() ^ (System.currentTimeMillis() * 0x9E3779B97F4A7C15L);
        playRng ^= (int) (mix ^ (mix >>> 32));
        if (playRng == 0) playRng = 0x51A7F3B9;
        playRng ^= playRng << 13; playRng ^= playRng >>> 17; playRng ^= playRng << 5;
        playRng ^= playRng << 13; playRng ^= playRng >>> 17; playRng ^= playRng << 5;
        activeCombatVar = (playRng >>> 1) % COMBAT_VARIANTS;
        for (int k = 0; k < COUNT; k++) {
            int n = variantsFor(k);
            sounds[k] = new Sound[n];
            for (int v = 0; v < n; v++) {
                final byte[] wav = buildWav(k, v);
                sounds[k][v] = loadWav("sfx_" + k + "_" + v + ".wav", wav);
            }
        }
        ambient = loadWav("amb_arena.wav", buildAmbientWav());
        theme = loadWav("theme_dungeon.wav", buildThemeWav());
        for (int v = 0; v < COMBAT_VARIANTS; v++) {
            combatTracks[v] = loadWav("mus_combat_" + v + ".wav", buildCombatWav(v));
        }
        muted = false;
        ambientId = ambient.loop(AMBIENT_VOL);
        // Start exploration theme and all combat stems together so vertical loops stay beat-locked
        float expVol = musicMuted ? 0f : THEME_VOL * (1f - combatMix);
        themeId = theme.loop(expVol);
        for (int v = 0; v < COMBAT_VARIANTS; v++) {
            float cVol = (!musicMuted && v == activeCombatVar) ? (COMBAT_VOL * combatMix) : 0f;
            combatIds[v] = combatTracks[v].loop(cVol);
        }
    }

    public void setCombat(boolean combat) {
        if (this.inCombat == combat) return;
        this.inCombat = combat;
        if (combat) {
            playRng ^= playRng << 13; playRng ^= playRng >>> 17; playRng ^= playRng << 5;
            int step = 1 + ((playRng >>> 1) % (COMBAT_VARIANTS - 1));
            activeCombatVar = (activeCombatVar + step) % COMBAT_VARIANTS;
            applyMusicVolumes();
        }
    }

    public void setCombat(boolean combat, int variant) {
        this.inCombat = combat;
        if (combat) {
            this.activeCombatVar = Math.max(0, Math.min(COMBAT_VARIANTS - 1, variant));
        }
        applyMusicVolumes();
    }

    public void updateMusic(float dt) {
        float target = inCombat ? 1f : 0f;
        if (combatMix == target) return;
        float speed = inCombat ? 1.6f : 0.9f;
        if (combatMix < target) combatMix = Math.min(target, combatMix + dt * speed);
        else combatMix = Math.max(target, combatMix - dt * speed);
        applyMusicVolumes();
    }

    public boolean toggleMute() {
        muted = !muted;
        applyMusicVolumes();
        return muted;
    }

    public boolean toggleMusic() {
        musicMuted = !musicMuted;
        applyMusicVolumes();
        return musicMuted;
    }

    private void applyMusicVolumes() {
        if (ambient != null && ambientId != -1L) {
            ambient.setVolume(ambientId, muted ? 0f : AMBIENT_VOL);
        }
        boolean playMusic = !muted && !musicMuted;
        if (theme != null && themeId != -1L) {
            theme.setVolume(themeId, playMusic ? (THEME_VOL * (1f - combatMix)) : 0f);
        }
        for (int v = 0; v < COMBAT_VARIANTS; v++) {
            if (combatTracks[v] != null && combatIds[v] != -1L) {
                float cVol = (playMusic && v == activeCombatVar) ? (COMBAT_VOL * combatMix) : 0f;
                combatTracks[v].setVolume(combatIds[v], cVol);
            }
        }
    }

    public void play(int kind, float volume) {
        play(kind, volume, 1f, 0f);
    }

    public void play(int kind, float volume, float pitch, float pan) {
        if (muted || kind < 0 || kind >= COUNT || sounds[kind] == null) return;
        long now = System.nanoTime();
        if (kind != KEY && now - lastPlayNs[kind] < MIN_RETRIGGER_NS) return;
        lastPlayNs[kind] = now;

        Sound[] pool = sounds[kind];
        int n = pool.length;
        int idx = 0;
        if (n > 1) {
            playRng ^= playRng << 13;
            playRng ^= playRng >>> 17;
            playRng ^= playRng << 5;
            int step = 1 + ((playRng >>> 1) % (n - 1));
            idx = (variantCursor[kind] + step) % n;
            variantCursor[kind] = idx;
        }
        if (pool[idx] != null) pool[idx].play(volume, pitch, pan);
    }

    public void dispose() {
        for (int k = 0; k < COUNT; k++) {
            if (sounds[k] == null) continue;
            for (int v = 0; v < sounds[k].length; v++) {
                if (sounds[k][v] != null) {
                    sounds[k][v].dispose();
                    sounds[k][v] = null;
                }
            }
            sounds[k] = null;
        }
        if (ambient != null) {
            ambient.stop();
            ambient.dispose();
            ambient = null;
            ambientId = -1L;
        }
        if (theme != null) {
            theme.stop();
            theme.dispose();
            theme = null;
            themeId = -1L;
        }
        for (int v = 0; v < COMBAT_VARIANTS; v++) {
            if (combatTracks[v] != null) {
                combatTracks[v].stop();
                combatTracks[v].dispose();
                combatTracks[v] = null;
                combatIds[v] = -1L;
            }
        }
    }

    private static Sound loadWav(String name, byte[] wav) {
        return Gdx.audio.newSound(new FileHandle(name) {
            @Override
            public InputStream read() {
                return new ByteArrayInputStream(wav);
            }
        });
    }

    public static int variantsFor(int kind) {
        return switch (kind) {
            case SUBMIT, REJECT, PSIONIC, HEAL -> 1;
            default -> VARIANTS;
        };
    }

    public static byte[] buildWav(int kind) {
        return buildWav(kind, 0);
    }

    /** Generates a 44-byte RIFF/WAVE header + 16-bit mono 44.1 kHz PCM waveform with Schroeder reverb. */
    public static byte[] buildWav(int kind, int variant) {
        float dur = switch (kind) {
            case KEY -> 0.036f;
            case SUBMIT -> 0.360f;
            case REJECT -> 0.240f;
            case HIT -> 0.340f;
            case SHOOT -> 0.280f;
            case SPLASH -> 0.480f;
            case ZAP -> 0.460f;
            case BOOM -> 0.920f;
            case CRIT -> 0.720f;
            case FIRE -> 0.640f;
            case SIZZLE -> 0.480f;
            case SWING -> 0.190f;
            case STEP -> 0.150f;
            case PSIONIC -> 0.720f;
            case SHATTER -> 0.560f;
            case HEAL -> 0.780f;
            default -> throw new IllegalArgumentException("Unknown sound kind: " + kind);
        };
        int samples = (int) (SAMPLE_RATE * dur);
        byte[] out = new byte[44 + samples * 2];
        writeWavHeader(out, 1, samples);

        int rng = 0x9E3779B9 ^ (kind * 0x45D9F3B) ^ (variant * 0x27D4EB2D);
        double varPitch = 1.0 + (variant - 1.5) * 0.042;
        double[] svf = new double[8]; // 4 independent TPT SVF filter pairs

        // Schroeder Freeverb state (4 LP-damped combs + 3 allpass diffusers)
        float[][] combBuf = new float[4][];
        int[] combIdx = new int[4];
        double[] combStore = new double[4];
        for (int c = 0; c < 4; c++) combBuf[c] = new float[COMB_LEN[c]];
        float[][] apBuf = {new float[AP_LEN[0]], new float[AP_LEN[1]], new float[AP_LEN[2]]};
        int[] apIdx = new int[3];

        // Karplus-Strong bowstring buffer for SHOOT with allpass stiffness dispersion
        int ksLen = 178 + variant * 14;
        double[] ks = new double[ksLen];
        double ksAllpass = 0.0;
        if (kind == SHOOT) {
            double mean = 0.0, prevN = 0.0;
            for (int j = 0; j < ksLen; j++) {
                rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
                double n = ((rng & 0xFFFF) / 32768.0) - 1.0;
                // Pluck profile: triangular window + filtered burst for realistic string release
                double window = 1.0 - Math.abs(2.0 * j / ksLen - 1.0);
                ks[j] = (0.65 * n + 0.35 * prevN) * (0.4 + 0.6 * window);
                prevN = n;
                mean += ks[j];
            }
            mean /= ksLen;
            for (int j = 0; j < ksLen; j++) ks[j] -= mean;
        }
        int ksIdx = 0;

        // Precomputed physical modal resonator partials (inharmonic real-material ratios)
        double[] mFreq = null, mTau = null, mAmp = null, mOn = null;
        int mCount = 0;
        switch (kind) {
            case KEY -> {
                // Crisp mechanical switch + dark wood keycap thock
                mCount = 4;
                mFreq = new double[]{185 * varPitch, 420 * varPitch, 1340 * varPitch, 2850 * varPitch};
                mTau = new double[]{0.008, 0.005, 0.0028, 0.0016};
                mAmp = new double[]{0.48, 0.32, 0.22, 0.16};
                mOn = new double[]{0.0, 0.0, 0.001, 0.0};
            }
            case REJECT -> {
                // Heavy iron-bound wooden lock / warded door thud (two-stage knock)
                mCount = 6;
                mFreq = new double[]{118, 179, 294, 92, 141, 232};
                mTau = new double[]{0.032, 0.022, 0.014, 0.042, 0.026, 0.016};
                mAmp = new double[]{0.52, 0.34, 0.18, 0.58, 0.36, 0.20};
                mOn = new double[]{0.0, 0.0, 0.0, 0.078, 0.078, 0.078};
            }
            case HIT -> {
                // Visceral shield/armor/flesh impact (inharmonic drum + metallic transient)
                mCount = 7;
                mFreq = new double[]{
                        112 * varPitch, 178 * varPitch, 264 * varPitch,
                        485 * varPitch, 790 * varPitch, 1290 / varPitch, 2140 * varPitch
                };
                mTau = new double[]{0.055, 0.045, 0.032, 0.024, 0.018, 0.012, 0.008};
                mAmp = new double[]{0.50, 0.38, 0.28, 0.20, 0.16, 0.12, 0.09};
                mOn = new double[]{0.0, 0.0, 0.0, 0.001, 0.001, 0.002, 0.0};
            }
            case CRIT -> {
                // Forged steel blade resonance (closely-spaced beating doublets + high ring)
                mCount = 10;
                mFreq = new double[]{
                        145 * varPitch, 238 * varPitch,
                        582 * varPitch, 591 * varPitch,     // beating steel doublet 1
                        1085 * varPitch, 1098 * varPitch,   // beating steel doublet 2
                        1740 * varPitch, 2480 / varPitch,
                        3390 * varPitch, 4620
                };
                mTau = new double[]{0.09, 0.08, 0.22, 0.20, 0.18, 0.16, 0.13, 0.095, 0.065, 0.040};
                mAmp = new double[]{0.42, 0.32, 0.26, 0.24, 0.22, 0.20, 0.17, 0.14, 0.11, 0.08};
                mOn = new double[]{0.0, 0.0, 0.002, 0.002, 0.003, 0.003, 0.004, 0.004, 0.005, 0.005};
            }
            case STEP -> {
                // Dungeon flagstone boot heel-toe
                mCount = 5;
                mFreq = new double[]{84 * varPitch, 142 * varPitch, 236, 390, 680 * varPitch};
                mTau = new double[]{0.026, 0.020, 0.014, 0.009, 0.006};
                mAmp = new double[]{0.50, 0.36, 0.22, 0.14, 0.08};
                mOn = new double[]{0.0, 0.0, 0.0, 0.018, 0.018};
            }
            case SHATTER -> {
                // Crystalline / alchemical glass fracture + cascading floor shards
                mCount = 12;
                mFreq = new double[]{
                        1820 * varPitch, 2690 / varPitch, 3840 * varPitch, 5120,
                        2240 / varPitch, 3310 * varPitch, 4680 / varPitch, 6180,
                        2950 * varPitch, 4190, 5540 * varPitch, 7120
                };
                mTau = new double[]{
                        0.065, 0.055, 0.042, 0.030,
                        0.058, 0.046, 0.035, 0.026,
                        0.050, 0.038, 0.028, 0.022
                };
                mAmp = new double[]{
                        0.32, 0.28, 0.22, 0.16,
                        0.26, 0.22, 0.18, 0.13,
                        0.20, 0.17, 0.14, 0.11
                };
                mOn = new double[]{
                        0.000, 0.001, 0.003, 0.006,
                        0.018, 0.027, 0.039, 0.052,
                        0.072, 0.095, 0.124, 0.158
                };
            }
        }

        double phase = 0.0, phase2 = 0.0, phase3 = 0.0, turb = 0.0, turb2 = 0.0;
        double crackleEnv = 0.0, crackleAmp = 0.0, crackleFreq = 1800.0, cracklePhase = 0.0;
        double b1Phase = 0.0, b1Freq = 520.0, b1Env = 0.0;
        double b2Phase = 0.0, b2Freq = 840.0, b2Env = 0.0;
        double zapHoldFreq = 320.0, zapSubPhase = 0.0;

        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double fadeOut = Math.min(1.0, (samples - 1 - i) / (SAMPLE_RATE * 0.012));
            rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
            double noise = ((rng & 0xFFFF) / 32768.0) - 1.0;

            // Dual-rate 1/f-like turbulent modulation
            turb += 0.0032 * (noise - turb);
            turb2 += 0.0007 * (noise - turb2);
            double turbMod = Math.max(0.20, Math.min(1.85, 0.92 + turb * 4.8 + turb2 * 2.5));

            double dry = 0.0;
            double reverbSend = 0.0;

            switch (kind) {
                case KEY -> {
                    double click = svf(noise * Math.exp(-t / 0.0011), 3400.0, 1.2, svf, 0, 1);
                    double modes = modal(t, mFreq, mTau, mAmp, mOn, mCount);
                    dry = (click * 0.48 + modes * 0.72) * Math.min(1.0, t / 0.0003) * Math.exp(-t / 0.016);
                }
                case SUBMIT -> {
                    // Warm arcane rune confirmation (root -> fifth -> octave crystal chime)
                    double n1 = crystalChime(t, 0.000, 523.25, 0.14); // C5
                    double n2 = crystalChime(t, 0.048, 783.99, 0.16); // G5
                    double n3 = crystalChime(t, 0.096, 1046.50, 0.21); // C6
                    dry = (n1 * 0.38 + n2 * 0.42 + n3 * 0.52) * 0.65;
                    reverbSend = dry * 0.45;
                }
                case REJECT -> {
                    double lt = t < 0.078 ? t : t - 0.078;
                    double knock = svf(noise * Math.exp(-lt / 0.0045), 360.0, 1.1, svf, 0, 0);
                    double modes = modal(t, mFreq, mTau, mAmp, mOn, mCount);
                    dry = softClip((modes * 0.75 + knock * 0.48) * 1.15) * 0.76;
                    reverbSend = dry * 0.24;
                }
                case HIT -> {
                    // Separate low sub-thud from high transient snap to prevent intermodulation mud
                    phase += 2.0 * Math.PI * (46.0 + 135.0 * Math.exp(-t / 0.018)) / SAMPLE_RATE;
                    double subPunch = Math.sin(phase) * Math.exp(-t / 0.050);
                    double snap = svf(noise * Math.exp(-t / 0.005), 2100.0 * varPitch, 1.1, svf, 0, 1);
                    double bodyNoise = svf(noise * Math.exp(-t / 0.028), 640.0 * Math.exp(-t / 0.04) + 160.0, 0.85, svf, 2, 0);
                    double modes = modal(t, mFreq, mTau, mAmp, mOn, mCount);
                    double low = softClip((subPunch * 0.80 + bodyNoise * 0.45) * 1.35);
                    double high = (snap * 0.58 + modes * 0.62) * Math.min(1.0, t / 0.0006);
                    dry = (low * 0.62 + high * 0.68) * 0.82;
                    reverbSend = dry * 0.30;
                }
                case SHOOT -> {
                    // Dispersion Karplus-Strong bowstring + wooden recurve limb resonance + arrow fletching whoosh
                    int prevIdx = ksIdx == 0 ? ksLen - 1 : ksIdx - 1;
                    double avg = 0.5 * (ks[ksIdx] + ks[prevIdx]) * 0.993;
                    // First-order allpass filter inside loop adds realistic string stiffness
                    double stiff = 0.35 * (avg - ksAllpass) + ks[prevIdx];
                    ksAllpass = stiff;
                    ks[ksIdx] = avg;
                    if (++ksIdx == ksLen) ksIdx = 0;

                    double bowBody = svf(stiff, 1450.0 * Math.exp(-t / 0.07) + 240.0, 0.9, svf, 0, 0);
                    double twangSnap = svf(noise * Math.exp(-t / 0.0035), 2800.0, 1.3, svf, 4, 1);
                    double fletchAir = svf(noise, 3200.0 * Math.exp(-t / 0.065) + 550.0, 1.6, svf, 2, 1)
                            * Math.min(1.0, t / 0.005) * Math.exp(-t / 0.055);
                    dry = (softClip(bowBody * 1.25) * 0.65 + twangSnap * 0.42 + fletchAir * 0.78) * 0.80;
                    reverbSend = dry * 0.22;
                }
                case SPLASH -> {
                    // Plunge cavity thump + turbulent water spray + FM rising air bubbles
                    phase += 2.0 * Math.PI * (68.0 + 95.0 * Math.exp(-t / 0.022)) / SAMPLE_RATE;
                    double plunge = Math.sin(phase) * Math.exp(-t / 0.045) * 0.52;
                    double slap = svf(noise * Math.exp(-t / 0.018), 950.0, 0.9, svf, 0, 1);
                    double spray = svf(noise, (2200.0 - 1500.0 * Math.min(1.0, t / 0.32)) * turbMod, 1.4, svf, 2, 1)
                            * Math.min(1.0, t / 0.008) * Math.exp(-t / 0.13);
                    if ((i == 480 || i == 1950 || i == 4200 || i == 7100)
                            || (b1Env < 0.015 && (rng & 0x1FF) == 0 && t < 0.34)) {
                        b1Freq = 380.0 + ((rng >>> 8) & 0x3FF) * 0.75;
                        b1Env = 0.95;
                    }
                    if ((i == 1150 || i == 3100 || i == 5600 || i == 8800)
                            || (b2Env < 0.015 && (rng & 0x1FF) == 1 && t < 0.36)) {
                        b2Freq = 540.0 + ((rng >>> 12) & 0x3FF) * 0.95;
                        b2Env = 0.88;
                    }
                    // Real water bubbles accelerate exponentially in pitch right as they pinch off
                    b1Freq *= 1.0 + 18.5 / SAMPLE_RATE;
                    b2Freq *= 1.0 + 21.0 / SAMPLE_RATE;
                    b1Phase += 2.0 * Math.PI * b1Freq / SAMPLE_RATE;
                    b2Phase += 2.0 * Math.PI * b2Freq / SAMPLE_RATE;
                    b1Env *= 0.9981;
                    b2Env *= 0.9983;
                    double bubbles = Math.sin(b1Phase) * b1Env * 0.28 + Math.sin(b2Phase) * b2Env * 0.24;
                    dry = (plunge + slap * 0.42 + spray * 0.72 + bubbles) * 0.76;
                    reverbSend = dry * 0.38;
                }
                case ZAP -> {
                    // MAGIC: High-voltage jagged plasma arc (no smooth laser slide!)
                    // 1. Stepped erratic arc frequency (jumps every ~3.5ms as plasma finds new paths)
                    if (i % 155 == 0) {
                        double stepRand = ((rng >>> 8) & 0xFF) / 255.0;
                        zapHoldFreq = (130.0 + 420.0 * stepRand * stepRand) * varPitch;
                    }
                    double arcFreq = zapHoldFreq * (1.0 + 0.25 * turb);
                    phase += 2.0 * Math.PI * arcFreq / SAMPLE_RATE;
                    phase2 += 2.0 * Math.PI * (arcFreq * 2.73) / SAMPLE_RATE;
                    zapSubPhase += 2.0 * Math.PI * (60.0 + 140.0 * Math.exp(-t / 0.035)) / SAMPLE_RATE;

                    // Self-phase-modulation creates jagged sawtooth-like plasma harmonics
                    double plasmaWave = Math.sin(phase + 0.72 * Math.sin(phase) + 1.35 * Math.sin(phase2));
                    double arcBody = svf(plasmaWave, 1400.0 + 2200.0 * turbMod, 2.2, svf, 0, 1)
                            * Math.exp(-t / 0.14);

                    // 2. Multi-stage high-voltage whip snaps (initial leader + secondary restrikes)
                    if (i == 0 || i == (int) (0.038 * SAMPLE_RATE) || i == (int) (0.085 * SAMPLE_RATE)
                            || (t < 0.26 && (rng & 0x3FF) < 3)) {
                        crackleEnv = 1.0;
                        crackleAmp = 0.55 + 0.45 * (((rng >>> 10) & 0xFF) / 255.0);
                        crackleFreq = 2400.0 + 3200.0 * (((rng >>> 16) & 0xFF) / 255.0);
                    }
                    crackleEnv *= 0.991;
                    double whipSnap = svf(noise * crackleEnv * crackleAmp, crackleFreq, 2.6, svf, 2, 1);
                    double sizzleAir = svf(noise, 4800.0, 1.1, svf, 4, 2) * crackleEnv * 0.42;
                    double thunderPunch = Math.sin(zapSubPhase) * Math.exp(-t / 0.045) * 0.48;

                    dry = (thunderPunch + arcBody * 0.58 + whipSnap * 0.75 + sizzleAir) * 0.78;
                    reverbSend = dry * 0.48;
                }
                case BOOM -> {
                    // Clean band-split explosion: sub-bass shockwave + mid-band fire roar + high rocky debris
                    double shockSnap = svf(noise * Math.exp(-t / 0.006), 1400.0, 0.9, svf, 4, 1);
                    phase += 2.0 * Math.PI * (24.0 + 86.0 * Math.exp(-t / 0.055)) / SAMPLE_RATE;
                    double subWave = softClip(Math.sin(phase) * 1.45) * Math.exp(-t / 0.24);

                    double blastEnv = Math.min(1.0, t / 0.008) * Math.exp(-t / 0.25);
                    double roarCutoff = (1350.0 * Math.exp(-t / 0.18) + 85.0) * (0.80 + 0.35 * turbMod);
                    double roar = softClip(svf(noise, roarCutoff, 0.85, svf, 0, 0) * blastEnv * 1.40);

                    if (t > 0.030 && t < 0.58 && (rng & 0x7FF) < 4) {
                        double r = ((rng >>> 12) & 0xFF) / 255.0;
                        crackleAmp = 0.30 + 0.70 * r * r;
                        crackleEnv = 1.0;
                        crackleFreq = 900.0 + 1800.0 * r;
                    }
                    crackleEnv *= 0.992;
                    double debris = svf(noise * crackleEnv * crackleAmp, crackleFreq, 1.5, svf, 2, 1);

                    dry = (subWave * 0.62 + roar * 0.64 + shockSnap * 0.55 + debris * 0.48) * 0.82;
                    reverbSend = dry * 0.55;
                }
                case CRIT -> {
                    // Devastating armor-piercing strike + singing steel blade resonance
                    phase += 2.0 * Math.PI * (36.0 + 128.0 * Math.exp(-t / 0.024)) / SAMPLE_RATE;
                    double subImpact = softClip(Math.sin(phase) * 1.35) * Math.exp(-t / 0.085);
                    double metalSlash = svf(noise * Math.exp(-t / 0.012), 2900.0 * varPitch, 1.8, svf, 0, 1);
                    double modes = modal(t, mFreq, mTau, mAmp, mOn, mCount);
                    dry = (subImpact * 0.62 + metalSlash * 0.58 + modes * 0.72) * 0.80;
                    reverbSend = (metalSlash * 0.4 + modes * 0.85) * 0.52;
                }
                case FIRE -> {
                    // MAGIC: Pyromancy ignition pouf + turbulent convection drafts + resonant ember pops
                    // 1. Initial gas ignition thump (first 75ms)
                    phase += 2.0 * Math.PI * (52.0 + 75.0 * Math.exp(-t / 0.032)) / SAMPLE_RATE;
                    double ignitePouf = Math.sin(phase) * Math.exp(-t / 0.065) * 0.55;

                    // 2. Band-split convection drafts (low body + mid whoosh + high flame lick)
                    double env = Math.min(1.0, t / 0.028) * Math.exp(-Math.max(0.0, t - 0.035) / 0.18);
                    double flutter = 0.65 + 0.70 * Math.max(0.0, 0.5 + turb * 4.2);
                    double lowDraft = svf(noise, 185.0 * (0.75 + 0.45 * turbMod), 0.8, svf, 0, 0) * env * flutter;
                    double midFlame = svf(noise, (480.0 + 720.0 * Math.sin(Math.PI * Math.min(1.0, t / 0.38))) * turbMod,
                            1.25, svf, 2, 1) * env;
                    double highLick = svf(noise, 2800.0 * turbMod, 1.1, svf, 4, 2) * env * 0.32;

                    // 3. Resonant wood/plasma ember snaps (4ms–8ms pitch-dropping resonant pops, not 1-sample clicks)
                    if (t > 0.012 && t < 0.52 && crackleEnv < 0.08 && (rng & 0x3FF) < 4) {
                        double r = ((rng >>> 12) & 0xFF) / 255.0;
                        crackleAmp = 0.28 + 0.72 * r * r;
                        crackleEnv = 1.0;
                        crackleFreq = 1100.0 + 2400.0 * r;
                    }
                    crackleEnv *= 0.993; // ~6.5 ms resonant decay
                    crackleFreq *= 0.9985; // rapid pitch drop as gas pocket expands
                    cracklePhase += 2.0 * Math.PI * crackleFreq / SAMPLE_RATE;
                    double emberPop = (Math.sin(cracklePhase) * 0.55 + svf(noise, crackleFreq, 2.4, svf, 6, 1) * 0.65)
                            * crackleEnv * crackleAmp;

                    dry = (ignitePouf + softClip(lowDraft * 1.25) * 0.62 + midFlame * 0.68 + highLick + emberPop * 0.72) * 0.78;
                    reverbSend = dry * 0.34;
                }
                case SIZZLE -> {
                    // MAGIC: Alchemical acid / searing steam + micro-cavitation boiling bubbles
                    double env = Math.min(1.0, t / 0.010) * Math.exp(-t / 0.16);
                    double steamSweep = (4200.0 - 2200.0 * Math.min(1.0, t / 0.38)) * (0.85 + 0.25 * turbMod);
                    double steamJet = svf(noise, steamSweep, 1.35, svf, 0, 1) * env;
                    double hissAir = svf(noise, 6400.0, 0.9, svf, 2, 2) * env * 0.38;

                    // Rapid upward-chirping acid micro-bubbles
                    if (t < 0.38 && b1Env < 0.05 && (rng & 0xFF) < 5) {
                        b1Freq = 1200.0 + ((rng >>> 8) & 0x7FF) * 1.4;
                        b1Env = 0.45 + 0.55 * (((rng >>> 16) & 0xFF) / 255.0);
                    }
                    b1Freq *= 1.0 + 38.0 / SAMPLE_RATE;
                    b1Phase += 2.0 * Math.PI * b1Freq / SAMPLE_RATE;
                    b1Env *= 0.994;
                    double acidFizz = Math.sin(b1Phase + 0.8 * Math.sin(b1Phase * 2.1)) * b1Env * 0.36 * Math.exp(-t / 0.20);

                    dry = (steamJet * 0.65 + hissAir + acidFizz) * 0.76;
                    reverbSend = dry * 0.28;
                }
                case SWING -> {
                    // Dual-band weapon cut through air (low body displacement + high blade edge whistle)
                    double u = Math.min(1.0, t / 0.165);
                    double env = Math.pow(Math.sin(Math.PI * u), 1.45);
                    double bodyCut = (260.0 + 780.0 * Math.sin(Math.PI * u)) * varPitch;
                    double edgeCut = (1100.0 + 2600.0 * Math.pow(Math.sin(Math.PI * u), 1.8)) * varPitch;
                    double lowWhoosh = svf(noise, bodyCut, 1.35, svf, 0, 1) * env;
                    double bladeWhistle = svf(noise, edgeCut, 3.2, svf, 2, 1) * env * 0.48;
                    dry = (lowWhoosh * 0.78 + bladeWhistle) * 0.88;
                    reverbSend = dry * 0.18;
                }
                case STEP -> {
                    double grit = svf(noise * Math.exp(-t / 0.014), 1350.0 * varPitch, 1.25, svf, 0, 1);
                    double modes = modal(t, mFreq, mTau, mAmp, mOn, mCount);
                    dry = (modes * 0.72 + grit * 0.38) * Math.min(1.0, t / 0.0012) * 0.76;
                    reverbSend = dry * 0.26;
                }
                case PSIONIC -> {
                    // MAGIC: Eldritch Mind-Warp / Astral Resonance
                    // 1. Sub-bass gravity bend + detuned 3-oscillator vocal-formant choir + crystalline FM shimmer
                    double env = Math.min(1.0, t / 0.035) * Math.exp(-Math.max(0.0, t - 0.06) / 0.24);
                    double vibrato = Math.sin(2.0 * Math.PI * (6.2 + 3.5 * t) * t);
                    double baseFreq = 220.0 + 68.0 * Math.sin(Math.PI * Math.min(1.0, t / 0.48)) + 7.5 * vibrato;

                    // Three detuned carriers with non-integer metallic/astral modulator (1 : 1.4142)
                    phase += 2.0 * Math.PI * baseFreq / SAMPLE_RATE;
                    phase2 += 2.0 * Math.PI * (baseFreq * 1.007 + 1.8) / SAMPLE_RATE;
                    phase3 += 2.0 * Math.PI * (baseFreq * 1.4142) / SAMPLE_RATE;
                    zapSubPhase += 2.0 * Math.PI * (78.0 - 34.0 * Math.min(1.0, t / 0.45)) / SAMPLE_RATE;

                    double fmIndex = 1.65 * Math.exp(-t / 0.32) + 0.35;
                    double modWave = fmIndex * Math.sin(phase3);
                    double astralChoir = 0.38 * Math.sin(phase + modWave)
                            + 0.38 * Math.sin(phase2 - modWave * 0.85)
                            + 0.28 * Math.sin(phase * 2.0 + Math.sin(phase2));

                    // Dual formant bandpass filters ("Ahhh" -> "Oooo" vocal resonance)
                    double f1 = 520.0 + 640.0 * Math.sin(Math.PI * Math.min(1.0, t / 0.42));
                    double f2 = 1480.0 + 1150.0 * Math.cos(Math.PI * 0.85 * Math.min(1.0, t / 0.50));
                    double formant1 = svf(astralChoir, f1, 2.4, svf, 0, 1);
                    double formant2 = svf(astralChoir, f2, 3.0, svf, 2, 1);

                    // High crystalline harmonic halo (A5 -> E6 shimmer)
                    double halo = Math.sin(phase * 4.0 + 0.6 * Math.sin(phase3 * 2.0))
                            * Math.min(1.0, t / 0.08) * Math.exp(-t / 0.28) * 0.22;
                    double subWarp = Math.sin(zapSubPhase) * Math.exp(-t / 0.22) * 0.36;

                    dry = (subWarp + formant1 * 0.68 + formant2 * 0.52 + halo) * env * 0.82;
                    reverbSend = (formant1 * 0.5 + formant2 * 0.7 + halo * 1.4) * env * 0.68;
                }
                case SHATTER -> {
                    phase += 2.0 * Math.PI * (58.0 + 130.0 * Math.exp(-t / 0.012)) / SAMPLE_RATE;
                    double impactCrack = Math.sin(phase) * Math.exp(-t / 0.020) * 0.42;
                    double glassBurst = svf(noise * Math.exp(-t / 0.045), 3600.0, 1.3, svf, 0, 2);
                    double shards = modal(t, mFreq, mTau, mAmp, mOn, mCount);
                    dry = (impactCrack + glassBurst * 0.48 + shards * 0.68) * 0.78 * Math.min(1.0, t / 0.0006);
                    reverbSend = (glassBurst * 0.3 + shards * 0.75) * 0.52;
                }
                case HEAL -> {
                    // MAGIC: Radiant Lydian Crystal Choir (G4 -> B4 -> D5 -> F#5 -> A5) + Angelic Shimmer Dust
                    double c1 = crystalChime(t, 0.000, 392.00, 0.26); // G4
                    double c2 = crystalChime(t, 0.055, 493.88, 0.27); // B4
                    double c3 = crystalChime(t, 0.110, 587.33, 0.29); // D5
                    double c4 = crystalChime(t, 0.165, 739.99, 0.32); // F#5 (Lydian magic interval)
                    double c5 = crystalChime(t, 0.220, 880.00, 0.38); // A5

                    // Warm breathy pad swell underneath the crystal bells
                    double swellEnv = Math.min(1.0, t / 0.14) * Math.exp(-Math.max(0.0, t - 0.14) / 0.28);
                    double pad = (Math.sin(2.0 * Math.PI * 196.0 * t) * 0.25
                            + Math.sin(2.0 * Math.PI * 293.66 * t + 0.3 * Math.sin(2.0 * Math.PI * 4.5 * t)) * 0.22)
                            * swellEnv;

                    // High sparkling "pixie/aura dust" filtered noise
                    double sparkleMod = 0.55 + 0.45 * Math.sin(2.0 * Math.PI * 19.0 * t);
                    double auraSparkle = svf(noise, 5200.0 + 1800.0 * Math.sin(2.0 * Math.PI * 3.2 * t), 2.8, svf, 0, 1)
                            * swellEnv * sparkleMod * 0.18;

                    dry = ((c1 * 0.32 + c2 * 0.34 + c3 * 0.36 + c4 * 0.40 + c5 * 0.44) + pad + auraSparkle) * 0.62;
                    reverbSend = dry * 0.64;
                }
            }

            double wet = schroeder(reverbSend, combBuf, combIdx, combStore, apBuf, apIdx);
            double s = (dry * 0.88 + wet * 0.38) * fadeOut;
            int pcm = Math.max(-32767, Math.min(32767, (int) Math.round(s * 32767.0)));
            putShortLE(out, 44 + i * 2, pcm);
        }
        return out;
    }

    /** Generates a seamless 4.0 s stereo 44.1 kHz stone-cavern + distant brazier ambience loop. */
    public static byte[] buildAmbientWav() {
        int frames = SAMPLE_RATE * 4;
        int xfade = SAMPLE_RATE / 2;
        int total = frames + xfade;
        float[] bufL = new float[total];
        float[] bufR = new float[total];

        int rngL = 0x13579BDF, rngR = 0x2468ACE1;
        double[] svfL = new double[4], svfR = new double[4];
        double pinkL = 0.0, pinkR = 0.0, crackEnv = 0.0, crackPan = 0.5, crackFreq = 1800.0;

        for (int i = 0; i < total; i++) {
            double t = i / (double) SAMPLE_RATE;
            rngL ^= rngL << 13; rngL ^= rngL >>> 17; rngL ^= rngL << 5;
            rngR ^= rngR << 13; rngR ^= rngR >>> 17; rngR ^= rngR << 5;
            double nL = ((rngL & 0xFFFF) / 32768.0) - 1.0;
            double nR = ((rngR & 0xFFFF) / 32768.0) - 1.0;

            // Decorrelated dark stone air draft
            pinkL += 0.015 * (nL - pinkL);
            pinkR += 0.015 * (nR - pinkR);
            double airL = svf(pinkL * 4.2, 195.0 + 45.0 * Math.sin(2.0 * Math.PI * 0.25 * t), 0.80, svfL, 0, 0);
            double airR = svf(pinkR * 4.2, 195.0 + 45.0 * Math.cos(2.0 * Math.PI * 0.25 * t), 0.80, svfR, 0, 0);

            // Subtle faint brazier crackles scattered in L/R space
            if ((rngL & 0x1FFF) < 3) {
                crackEnv = 0.35 + 0.65 * (((rngR >>> 8) & 0xFF) / 255.0);
                crackPan = 0.20 + 0.60 * (((rngR >>> 16) & 0xFF) / 255.0);
                crackFreq = 1400.0 + 1400.0 * (((rngL >>> 12) & 0xFF) / 255.0);
            }
            crackEnv *= 0.990;
            double crk = svf(nL * crackEnv, crackFreq, 1.8, svfL, 2, 1) * 0.32;
            // Subtle fifth cavern resonance (55 Hz A1 + 82.5 Hz E2)
            double drone = Math.sin(2.0 * Math.PI * 55.0 * t) * 0.040 + Math.sin(2.0 * Math.PI * 82.5 * t) * 0.022;

            bufL[i] = (float) (airL * 0.72 + crk * (1.0 - crackPan) + drone);
            bufR[i] = (float) (airR * 0.72 + crk * crackPan + drone);
        }

        // Equal-power overlap crossfade for zero-click seamless loop
        byte[] out = new byte[44 + frames * 4];
        writeWavHeader(out, 2, frames);
        for (int i = 0; i < frames; i++) {
            double l = bufL[i], r = bufR[i];
            if (i < xfade) {
                double ang = (i / (double) xfade) * (Math.PI * 0.5);
                double wHead = Math.sin(ang), wTail = Math.cos(ang);
                l = bufL[i] * wHead + bufL[frames + i] * wTail;
                r = bufR[i] * wHead + bufR[frames + i] * wTail;
            }
            int pL = Math.max(-32767, Math.min(32767, (int) Math.round(l * 32767.0)));
            int pR = Math.max(-32767, Math.min(32767, (int) Math.round(r * 32767.0)));
            putShortLE(out, 44 + i * 4, pL);
            putShortLE(out, 44 + i * 4 + 2, pR);
        }
        return out;
    }

    /**
     * Generates a seamless 16.0 s (4-bar @ 60 BPM) stereo 44.1 kHz Dark Dungeon Theme loop
     * in D Harmonic Minor / Phrygian (Dm -> BbM7 -> Gm -> A7b9) to layer with buildAmbientWav().
     */
    public static byte[] buildThemeWav() {
        int frames = SAMPLE_RATE * 16;      // 16.0s = 4 bars at 60 BPM (1.0s per beat)
        int xfade = SAMPLE_RATE * 2;        // 2.0s equal-power wrap-around for reverb/note tails
        int total = frames + xfade;
        float[] bufL = new float[total];
        float[] bufR = new float[total];

        int rngL = 0x13579BDF, rngR = 0x2468ACE1;
        double[] svfL = new double[2], svfR = new double[2];

        // Stereo cathedral delay lines (prime lengths ~310ms and ~440ms for wide ping-pong echoes)
        float[] echoL = new float[13697];
        float[] echoR = new float[19423];
        int eIdxL = 0, eIdxR = 0;
        double echoLpL = 0.0, echoLpR = 0.0;

        // 4-Bar Dark Progression (D minor -> Bb major -> G minor -> A Phrygian Dominant)
        // Integer-cycle sum across 16s (< 2 cents nudge) keeps phase locked across the 2s loop wrap
        final double[] bassRoots = {36.75, 58.25, 49.00, 55.00};
        final double[] bassFifths = {55.00, 87.25, 73.50, 82.50};
        final double[] choirNotes1 = {293.66, 293.66, 293.66, 261.63}; // D4 -> D4 -> D4 -> C4
        final double[] choirNotes2 = {349.23, 349.23, 349.23, 329.63}; // F4 -> F4 -> F4 -> E4

        // 16-step dark harp/lute ostinato in clean D Natural Minor
        final double[] arpFreqs = {
                146.83, 220.00, 293.66, 349.23, // Bar 1 (Dm):  D3, A3, D4, F4
                116.54, 174.61, 293.66, 349.23, // Bar 2 (Bb):  Bb2, F3, D4, F4
                98.00,  196.00, 233.08, 293.66, // Bar 3 (Gm):  G2, G3, Bb3, D4
                110.00, 164.81, 220.00, 261.63  // Bar 4 (Am):  A2, E3, A3, C4
        };
        final double[] arpPans = {
                0.28, 0.72, 0.35, 0.68,
                0.25, 0.75, 0.32, 0.70,
                0.30, 0.65, 0.25, 0.74,
                0.35, 0.68, 0.28, 0.76
        };

        double bassPhase1 = 0.0, bassPhase2 = 0.0, subPhase = 0.0;

        for (int i = 0; i < total; i++) {
            double t = i / (double) SAMPLE_RATE;
            double loopT = t % 16.0;
            int bar = ((int) (loopT / 4.0)) & 3;
            double barT = loopT - bar * 4.0;

            rngL ^= rngL << 13; rngL ^= rngL >>> 17; rngL ^= rngL << 5;
            rngR ^= rngR << 13; rngR ^= rngR >>> 17; rngR ^= rngR << 5;
            double nL = ((rngL & 0xFFFF) / 32768.0) - 1.0;
            double nR = ((rngR & 0xFFFF) / 32768.0) - 1.0;

            // --- 1. BOWED DARK CELLO / CONTRABASS DRONE ---
            double bowEnv = Math.sin(Math.PI * (barT / 4.0));
            bowEnv = 0.25 + 0.75 * bowEnv * bowEnv;
            double vibrato = 1.0 + 0.0035 * Math.sin(2.0 * Math.PI * 4.5 * t);
            double bFreq1 = bassRoots[bar] * vibrato;
            double bFreq2 = bassFifths[bar] * (2.0 - vibrato);

            bassPhase1 += 2.0 * Math.PI * bFreq1 / SAMPLE_RATE;
            bassPhase2 += 2.0 * Math.PI * bFreq2 / SAMPLE_RATE;
            subPhase += 2.0 * Math.PI * bassRoots[bar] / SAMPLE_RATE;

            double rawCelloL = Math.sin(bassPhase1 + 0.55 * Math.sin(bassPhase1)) * 0.65
                    + Math.sin(bassPhase2 + 0.40 * Math.sin(bassPhase2)) * 0.35;
            double rawCelloR = Math.sin(bassPhase1 + 0.50 * Math.cos(bassPhase1)) * 0.65
                    + Math.sin(bassPhase2 - 0.40 * Math.sin(bassPhase2)) * 0.35;

            double bowCutoff = 140.0 + 190.0 * bowEnv + 35.0 * Math.sin(2.0 * Math.PI * 0.25 * t);
            double celloL = svf(rawCelloL, bowCutoff, 1.15, svfL, 0, 0) * bowEnv * 0.20;
            double celloR = svf(rawCelloR, bowCutoff, 1.15, svfR, 0, 0) * bowEnv * 0.20;
            double subDrone = Math.sin(subPhase) * bowEnv * 0.11;

            // --- 2. HAUNTING LUTE / HARP ARPEGGIO & GHOSTLY CHOIR ---
            double harpL = 0.0, harpR = 0.0;
            int curStep = (int) Math.floor(t);
            for (int s = Math.max(0, curStep - 3); s <= curStep; s++) {
                double noteAge = t - s;
                int noteIdx = s & 15;
                double pluck = darkPluck(noteAge, arpFreqs[noteIdx]);
                double pan = arpPans[noteIdx];
                harpL += pluck * (1.0 - pan);
                harpR += pluck * pan;
            }

            double choirEnv = Math.pow(Math.sin(Math.PI * (barT / 4.0)), 1.8) * 0.045;
            double c1 = choirNotes1[bar], c2 = choirNotes2[bar];
            double choirL = (Math.sin(2.0 * Math.PI * c1 * t + 0.08 * Math.sin(2.0 * Math.PI * 3.8 * t))
                    + 0.7 * Math.sin(2.0 * Math.PI * (c2 * 1.002) * t)) * choirEnv;
            double choirR = (Math.sin(2.0 * Math.PI * (c1 * 0.998) * t - 0.08 * Math.sin(2.0 * Math.PI * 3.6 * t))
                    + 0.7 * Math.sin(2.0 * Math.PI * c2 * t)) * choirEnv;

            // --- 3. DISTANT RITUAL FRAME DRUM (let ghost tail ring past bar boundary) ---
            double ghostAge = (bar % 2 == 1) ? barT - 3.0 : (t >= 4.0 ? barT + 1.0 : -1.0);
            double drum = ritualDrum(barT, nL) * 0.14 + ritualDrum(ghostAge, nR) * 0.06;

            // --- 4. STEREO PING-PONG CATHEDRAL REVERB ---
            double sendL = harpL * 0.65 + choirL * 0.90 + celloL * 0.25 + drum * 0.45;
            double sendR = harpR * 0.65 + choirR * 0.90 + celloR * 0.25 + drum * 0.45;

            double delL = echoL[eIdxL];
            double delR = echoR[eIdxR];
            echoLpL = delL * 0.45 + echoLpL * 0.55;
            echoLpR = delR * 0.45 + echoLpR * 0.55;

            echoL[eIdxL] = (float) (sendL + echoLpR * 0.56);
            echoR[eIdxR] = (float) (sendR + echoLpL * 0.56);
            if (++eIdxL == echoL.length) eIdxL = 0;
            if (++eIdxR == echoR.length) eIdxR = 0;

            double mixL = celloL + subDrone + drum + harpL * 0.28 + choirL + echoLpL * 0.38;
            double mixR = celloR + subDrone + drum + harpR * 0.28 + choirR + echoLpR * 0.38;

            bufL[i] = (float) softClip(mixL);
            bufR[i] = (float) softClip(mixR);
        }

        byte[] out = new byte[44 + frames * 4];
        writeWavHeader(out, 2, frames);
        for (int i = 0; i < frames; i++) {
            double l = bufL[i], r = bufR[i];
            if (i < xfade) {
                double ang = (i / (double) xfade) * (Math.PI * 0.5);
                double wHead = Math.sin(ang), wTail = Math.cos(ang);
                l = bufL[i] * wHead + bufL[frames + i] * wTail;
                r = bufR[i] * wHead + bufR[frames + i] * wTail;
            }
            int pL = Math.max(-32767, Math.min(32767, (int) Math.round(l * 32767.0)));
            int pR = Math.max(-32767, Math.min(32767, (int) Math.round(r * 32767.0)));
            putShortLE(out, 44 + i * 4, pL);
            putShortLE(out, 44 + i * 4 + 2, pR);
        }
        return out;
    }

    /** Plucked dark dungeon lute/harp string with warm decaying overtones and subtle chorus. */
    private static double darkPluck(double dt, double freq) {
        if (dt < 0.0 || dt > 3.6) return 0.0;
        double attack = Math.min(1.0, dt / 0.008);
        double brightIndex = 1.35 * Math.exp(-dt / 0.14) + 0.12 * Math.exp(-dt / 1.1);
        double mod = brightIndex * Math.sin(2.0 * Math.PI * (freq * 2.0) * dt);
        double str1 = Math.sin(2.0 * Math.PI * freq * dt + mod);
        double str2 = Math.sin(2.0 * Math.PI * (freq * 1.0018 + 0.45) * dt - mod * 0.6);
        double harm3 = 0.22 * Math.exp(-dt / 0.35) * Math.sin(2.0 * Math.PI * (freq * 3.0) * dt);
        return (0.48 * str1 + 0.42 * str2 + harm3) * attack * Math.exp(-dt / 1.25);
    }

    /** Deep reverberant dungeon ritual tom / war-drum impact. */
    private static double ritualDrum(double dt, double noise) {
        if (dt < 0.0 || dt > 1.2) return 0.0;
        double attack = Math.min(1.0, dt / 0.005);
        double phase = 2.0 * Math.PI * (44.0 * dt + 1.45 * (1.0 - Math.exp(-dt / 0.028)));
        double body = Math.sin(phase) * Math.exp(-dt / 0.32);
        double skinThud = noise * Math.exp(-dt / 0.018) * 0.22;
        return (body + skinThud) * attack;
    }

    /**
     * Generates a seamless 16.0 s stereo 44.1 kHz Dark Combat Soundtrack loop (120 BPM = 8 bars, 128 16th-steps)
     * locked to the exact same D Phrygian / Harmonic Minor tonality and duration as buildThemeWav().
     *
     * @param variant 0 = Relentless String Ostinato, 1 = Heavy Doom/Brass Siege, 2 = Frantic Ritual/Boss Polyrhythm
     */
    public static byte[] buildCombatWav(int variant) {
        int frames = SAMPLE_RATE * 16;      // 16.0s = 8 bars at 120 BPM (0.125s per 16th-note step)
        int xfade = SAMPLE_RATE * 2;        // 2.0s equal-power wrap-around for reverb/drum tails
        int total = frames + xfade;
        float[] bufL = new float[total];
        float[] bufR = new float[total];

        int rngL = 0x7F4A7C15 ^ (variant * 0x1F123BB5);
        int rngR = 0x9E3779B9 ^ (variant * 0x51A7F3B9);
        double[] svfL = new double[6], svfR = new double[6];

        // Tighter stereo combat chamber delay (~185ms and ~250ms so fast rhythms stay punchy)
        float[] echoL = new float[8161];
        float[] echoR = new float[11027];
        int eIdxL = 0, eIdxR = 0;
        double echoLpL = 0.0, echoLpR = 0.0;

        // 4-Section Harmonic Roots in clean D Natural Minor (0.25 Hz multiples for exact 16s loop phase-lock)
        // Var 0: Dm -> Bb -> Gm -> Am | Var 1: Dm -> C -> Bb -> Am | Var 2: Dm -> F -> Gm -> Am
        final double[][] bassProg = {
                {36.75, 58.25, 49.00, 55.00},
                {36.75, 32.75, 58.25, 55.00},
                {36.75, 43.75, 49.00, 55.00}
        };

        // 12-step (1/6 s per step = 6 steps/sec, exact midpoint between 4 and 8) D Natural Minor melodies
        final double[][][] melodies = {
                {
                        {146.83, 0.0, 220.00, 174.61, 146.83, 220.00, 196.00, 174.61, 146.83, 220.00, 196.00, 220.00},
                        {116.54, 0.0, 174.61, 233.08, 174.61, 220.00, 196.00, 174.61, 116.54, 174.61, 196.00, 220.00},
                        {98.00,  0.0, 146.83, 233.08, 196.00, 146.83, 174.61, 164.81, 98.00,  146.83, 174.61, 196.00},
                        {110.00, 0.0, 164.81, 220.00, 261.63, 220.00, 196.00, 164.81, 110.00, 164.81, 196.00, 164.81}
                },
                {
                        {146.83, 0.0, 220.00, 174.61, 0.0, 164.81, 146.83, 0.0, 220.00, 174.61, 164.81, 0.0},
                        {130.81, 0.0, 196.00, 164.81, 0.0, 146.83, 130.81, 0.0, 196.00, 164.81, 146.83, 0.0},
                        {116.54, 0.0, 174.61, 146.83, 0.0, 130.81, 116.54, 0.0, 174.61, 146.83, 130.81, 0.0},
                        {110.00, 0.0, 164.81, 196.00, 0.0, 164.81, 110.00, 0.0, 164.81, 196.00, 164.81, 0.0}
                },
                {
                        {146.83, 0.0, 174.61, 220.00, 174.61, 196.00, 174.61, 146.83, 174.61, 220.00, 196.00, 0.0},
                        {174.61, 0.0, 220.00, 261.63, 220.00, 233.08, 220.00, 174.61, 220.00, 261.63, 233.08, 0.0},
                        {196.00, 0.0, 233.08, 293.66, 233.08, 261.63, 233.08, 196.00, 233.08, 293.66, 261.63, 0.0},
                        {220.00, 0.0, 261.63, 329.63, 261.63, 293.66, 261.63, 220.00, 261.63, 329.63, 293.66, 196.00}
                }
        };

        // 12-step War-Drum & Snare patterns per 2.0s bar (0, 3, 6, 9 land on 0.5s beats)
        final int[][] kickPat = {
                {2, 0, 0, 1, 0, 2, 2, 0, 0, 1, 0, 1},
                {2, 0, 0, 0, 0, 1, 2, 0, 0, 0, 1, 0},
                {2, 0, 1, 0, 2, 0, 1, 0, 2, 0, 1, 1}
        };
        final int[][] snarePat = {
                {0, 0, 0, 2, 0, 0, 0, 0, 0, 2, 0, 1},
                {0, 0, 0, 2, 0, 0, 0, 0, 0, 2, 1, 1},
                {0, 0, 1, 0, 2, 0, 0, 1, 0, 2, 1, 0}
        };

        int v = Math.max(0, Math.min(2, variant));
        double spiccatoPhase = 0.0, spiccatoPhase2 = 0.0, hornPhase = 0.0, hornPhase2 = 0.0;
        final double stepDur = 1.0 / 6.0;

        for (int i = 0; i < total; i++) {
            double t = i / (double) SAMPLE_RATE;
            double loopT = t % 16.0;
            int section = ((int) (loopT / 4.0)) & 3;
            double secT = loopT - section * 4.0;

            int stepIdx = (int) Math.floor(t * 6.0); // 6 steps/sec (exact midpoint between 4 and 8)
            double stepT = t - stepIdx * stepDur;
            int patStep = stepIdx % 12;

            rngL ^= rngL << 13; rngL ^= rngL >>> 17; rngL ^= rngL << 5;
            rngR ^= rngR << 13; rngR ^= rngR >>> 17; rngR ^= rngR << 5;
            double nL = ((rngL & 0xFFFF) / 32768.0) - 1.0;
            double nR = ((rngR & 0xFFFF) / 32768.0) - 1.0;

            double rootFreq = bassProg[v][section];

            // --- 1. DRIVING SPICCATO WAR-CELLOS ---
            boolean accent = (patStep == 0 || patStep == 3 || patStep == 5 || patStep == 6 || patStep == 9);
            double bowAttack = Math.min(1.0, stepT / 0.009);
            double bowDecay = Math.exp(-stepT / (accent ? 0.135 : 0.075));
            double bowEnv = bowAttack * bowDecay * (accent ? 1.0 : 0.48);

            double celloFreq = rootFreq * 2.0;
            spiccatoPhase += 2.0 * Math.PI * celloFreq / SAMPLE_RATE;
            spiccatoPhase2 += 2.0 * Math.PI * (celloFreq * 1.5 + 0.25) / SAMPLE_RATE;

            double rosin = nL * Math.exp(-stepT / 0.015) * (accent ? 0.24 : 0.10);
            double rawSpiccato = Math.sin(spiccatoPhase + 0.56 * Math.sin(spiccatoPhase)) * 0.72
                    + Math.sin(spiccatoPhase2 + 0.40 * Math.sin(spiccatoPhase2)) * 0.28 + rosin;
            double spiccatoCut = 340.0 + (accent ? 860.0 : 380.0) * bowEnv;
            double spiccato = svf(rawSpiccato, spiccatoCut, 1.25, svfL, 0, 0) * bowEnv * 0.26;

            // --- 2. HEROIC LOW BRASS / WAR-HORN PEDAL ---
            double hornWindow = (v == 1)
                    ? Math.pow(Math.sin(Math.PI * (secT / 4.0)), 1.0)
                    : Math.pow(Math.max(0.0, (secT - 1.2) / 2.8), 1.3) * Math.sin(Math.PI * (secT / 4.0));
            double hornFreq = rootFreq * 2.0;
            hornPhase += 2.0 * Math.PI * hornFreq / SAMPLE_RATE;
            hornPhase2 += 2.0 * Math.PI * (hornFreq * 1.5 + 0.25) / SAMPLE_RATE;
            double hornHarm = (0.42 + 0.45 * hornWindow) * Math.sin(hornPhase);
            double rawHorn = Math.sin(hornPhase + hornHarm) * 0.65
                    + Math.sin(hornPhase2 + hornHarm * 0.55) * 0.35;
            double horn = svf(rawHorn, 220.0 + 520.0 * hornWindow, 1.1, svfR, 0, 0) * hornWindow * 0.24;

            // --- 3. VARIANT-SPECIFIC COMBAT LEAD (Strings / War-Brass / Battle Dulcimer) ---
            double leadL = 0.0, leadR = 0.0;
            for (int s = Math.max(0, stepIdx - 5); s <= stepIdx; s++) {
                int noteSec = (s / 24) & 3;
                int noteStep = s % 12;
                double f = melodies[v][noteSec][noteStep];
                if (f <= 0.0) continue;
                double dt = t - s * stepDur;
                double note = combatLead(dt, f, v);
                double pan = ((s & 1) == 0) ? 0.36 : 0.64;
                leadL += note * (1.0 - pan);
                leadR += note * pan;
            }
            leadL = svf(leadL, v == 1 ? 1400.0 : 2800.0, 0.85, svfL, 4, 0);
            leadR = svf(leadR, v == 1 ? 1400.0 : 2800.0, 0.85, svfR, 4, 0);

            // --- 4. HEAVY WAR-DRUMS & MARCHING SNARE ---
            double drumL = 0.0, drumR = 0.0, snareRaw = 0.0;
            for (int s = Math.max(0, stepIdx - 3); s <= stepIdx; s++) {
                double dt = t - s * stepDur;
                int pat = s % 12;
                int kType = kickPat[v][pat];
                if (kType > 0) {
                    double wDrum = warDrum(dt, nL, kType == 2);
                    drumL += wDrum * (kType == 2 ? 0.40 : 0.22);
                    drumR += wDrum * (kType == 2 ? 0.40 : 0.26);
                }
                int sType = snarePat[v][pat];
                if (sType > 0) {
                    snareRaw += warSnare(dt, nR, sType == 2);
                }
            }
            double snare = svf(snareRaw, 1850.0, 1.1, svfL, 2, 1) * 0.28 + snareRaw * 0.14;
            drumL += snare * 0.92;
            drumR += snare * 1.08;

            // --- 5. STEREO CHAMBER REVERB ---
            double sendL = leadL * 0.55 + spiccato * 0.35 + horn * 0.50 + drumL * 0.40;
            double sendR = leadR * 0.55 + spiccato * 0.35 + horn * 0.50 + drumR * 0.40;

            double delL = echoL[eIdxL];
            double delR = echoR[eIdxR];
            echoLpL = delL * 0.52 + echoLpL * 0.48;
            echoLpR = delR * 0.52 + echoLpR * 0.48;
            echoL[eIdxL] = (float) (sendL + echoLpR * 0.46);
            echoR[eIdxR] = (float) (sendR + echoLpL * 0.46);
            if (++eIdxL == echoL.length) eIdxL = 0;
            if (++eIdxR == echoR.length) eIdxR = 0;

            double mixL = spiccato * 0.92 + horn * 0.80 + leadL * 0.42 + drumL + echoLpL * 0.34;
            double mixR = spiccato * 0.82 + horn * 0.90 + leadR * 0.42 + drumR + echoLpR * 0.34;

            bufL[i] = (float) softClip(mixL);
            bufR[i] = (float) softClip(mixR);
        }

        byte[] out = new byte[44 + frames * 4];
        writeWavHeader(out, 2, frames);
        for (int i = 0; i < frames; i++) {
            double l = bufL[i], r = bufR[i];
            if (i < xfade) {
                double ang = (i / (double) xfade) * (Math.PI * 0.5);
                double wHead = Math.sin(ang), wTail = Math.cos(ang);
                l = bufL[i] * wHead + bufL[frames + i] * wTail;
                r = bufR[i] * wHead + bufR[frames + i] * wTail;
            }
            int pL = Math.max(-32767, Math.min(32767, (int) Math.round(l * 32767.0)));
            int pR = Math.max(-32767, Math.min(32767, (int) Math.round(r * 32767.0)));
            putShortLE(out, 44 + i * 4, pL);
            putShortLE(out, 44 + i * 4 + 2, pR);
        }
        return out;
    }

    /** Variant-specific combat lead voice: 0=bowed war-strings, 1=heavy brass horn, 2=bright battle dulcimer. */
    private static double combatLead(double dt, double freq, int variant) {
        if (dt < 0.0 || dt > 1.1) return 0.0;
        if (variant == 1) {
            // Bold brass horn melody with warm sub-octave reinforcement
            double attack = Math.min(1.0, dt / 0.028);
            double env = attack * Math.exp(-dt / 0.46);
            double brass = (0.45 + 0.35 * env) * Math.sin(2.0 * Math.PI * freq * dt);
            double core = Math.sin(2.0 * Math.PI * freq * dt + brass);
            double sub = 0.45 * Math.sin(2.0 * Math.PI * (freq * 0.5) * dt);
            return (core * 0.72 + sub) * env;
        }
        if (variant == 2) {
            // Crisp dual-course hammered battle dulcimer with octave shimmer
            double attack = Math.min(1.0, dt / 0.003);
            double bite = 0.95 * Math.exp(-dt / 0.050) + 0.14 * Math.exp(-dt / 0.32);
            double mod = bite * Math.sin(2.0 * Math.PI * (freq * 2.0) * dt);
            double s1 = Math.sin(2.0 * Math.PI * freq * dt + mod);
            double s2 = Math.sin(2.0 * Math.PI * (freq * 2.002) * dt - mod * 0.4) * 0.42;
            return (s1 * 0.72 + s2) * attack * Math.exp(-dt / 0.30);
        }
        // Variant 0: fierce bowed string section (octave ensemble + warm bow harmonic)
        double attack = Math.min(1.0, dt / 0.018);
        double env = attack * Math.exp(-dt / 0.38);
        double bowMod = 0.52 * Math.sin(2.0 * Math.PI * freq * dt);
        double lo = Math.sin(2.0 * Math.PI * freq * dt + bowMod);
        double hi = Math.sin(2.0 * Math.PI * (freq * 2.0015) * dt + 0.35 * Math.sin(2.0 * Math.PI * (freq * 2.0) * dt));
        return (lo * 0.65 + hi * 0.40) * env;
    }

    /** Punchy war-tom / siege-drum with skin slap transient. */
    private static double warDrum(double dt, double noise, boolean heavy) {
        if (dt < 0.0 || dt > 0.45) return 0.0;
        double attack = Math.min(1.0, dt / 0.0025);
        double baseFreq = heavy ? 46.0 : 72.0;
        double sweep = heavy ? 2.10 : 1.45;
        double phase = 2.0 * Math.PI * (baseFreq * dt + sweep * (1.0 - Math.exp(-dt / 0.020)));
        double sub = Math.sin(phase) * Math.exp(-dt / (heavy ? 0.21 : 0.12));
        double slap = noise * Math.exp(-dt / 0.014) * (heavy ? 0.34 : 0.24);
        return (sub + slap) * attack;
    }

    /** Tight military/tribal snare & shield strike for driving combat backbeats. */
    private static double warSnare(double dt, double noise, boolean heavy) {
        if (dt < 0.0 || dt > 0.20) return 0.0;
        double attack = Math.min(1.0, dt / 0.0012);
        double shellPhase = 2.0 * Math.PI * (105.0 * dt + 1.2 * (1.0 - Math.exp(-dt / 0.012)));
        double shell = Math.sin(shellPhase) * Math.exp(-dt / 0.032) * 0.52;
        double rattle = noise * Math.exp(-dt / (heavy ? 0.048 : 0.024)) * (heavy ? 0.85 : 0.48);
        return (shell + rattle) * attack * (heavy ? 1.0 : 0.58);
    }

    private static double schroeder(double in, float[][] combBuf, int[] combIdx, double[] combStore,
                                    float[][] apBuf, int[] apIdx) {
        double acc = 0.0;
        for (int c = 0; c < 4; c++) {
            float[] buf = combBuf[c];
            int idx = combIdx[c];
            double out = buf[idx];
            combStore[c] = out * 0.64 + combStore[c] * 0.36;
            buf[idx] = (float) (in + combStore[c] * 0.81);
            combIdx[c] = idx + 1 == buf.length ? 0 : idx + 1;
            acc += out;
        }
        acc *= 0.25;
        for (int a = 0; a < 3; a++) {
            float[] buf = apBuf[a];
            int idx = apIdx[a];
            double b = buf[idx];
            double out = -acc + b;
            buf[idx] = (float) (acc + b * 0.5);
            apIdx[a] = idx + 1 == buf.length ? 0 : idx + 1;
            acc = out;
        }
        return acc;
    }

    private static double modal(double t, double[] freq, double[] tau, double[] amp, double[] on, int count) {
        double sum = 0.0;
        for (int m = 0; m < count; m++) {
            double dt = t - on[m];
            if (dt < 0.0) continue;
            // Slight initial pitch envelope simulates nonlinear tension at the moment of impact
            double pitchBend = 1.0 + 0.018 * Math.exp(-dt / 0.004);
            sum += amp[m] * Math.sin(2.0 * Math.PI * freq[m] * pitchBend * dt)
                    * Math.min(1.0, dt / 0.0008) * Math.exp(-dt / tau[m]);
        }
        return sum;
    }

    /**
     * Physical crystal-glass / celesta chime:
     * Combines chorused unison fundamentals, a 2.756x inharmonic bell strike partial,
     * and a delicate 3.0x glassy harmonic.
     */
    private static double crystalChime(double t, double start, double freq, double tau) {
        double dt = t - start;
        if (dt < 0.0) return 0.0;
        double attack = Math.min(1.0, dt / 0.004);
        double env = attack * Math.exp(-dt / tau);
        // Inharmonic metallic bell transient (2.756x ratio decays rapidly into pure crystal tone)
        double bellMod = 0.65 * Math.exp(-dt / 0.032) * Math.sin(2.0 * Math.PI * (freq * 2.756) * dt);
        // Chorused dual-oscillator fundamental (+1.6 Hz shimmer beat) + 3rd harmonic air
        double f1 = Math.sin(2.0 * Math.PI * freq * dt + bellMod);
        double f2 = Math.sin(2.0 * Math.PI * (freq * 1.0025 + 1.4) * dt - bellMod * 0.5);
        double overtone = 0.24 * Math.exp(-dt / (tau * 0.45)) * Math.sin(2.0 * Math.PI * (freq * 3.0) * dt);
        return (0.52 * f1 + 0.48 * f2 + overtone) * env;
    }

    /** Gentle asymmetric soft-clipper that preserves high-frequency clarity better than raw tanh. */
    private static double softClip(double x) {
        return x / (1.0 + 0.45 * Math.abs(x));
    }

    /** Zero-delay-feedback (Simper TPT) 2-pole SVF. mode: 0=LP, 1=BP, 2=HP. */
    private static double svf(double x, double cutoffHz, double q, double[] st, int off, int mode) {
        double clamped = Math.max(40.0, Math.min(18000.0, cutoffHz));
        double g = Math.tan(Math.PI * clamped / SAMPLE_RATE);
        double k = 1.0 / Math.max(0.5, q);
        double a1 = 1.0 / (1.0 + g * (g + k));
        double a2 = g * a1;
        double a3 = g * a2;
        double v3 = x - st[off + 1];
        double v1 = a1 * st[off] + a2 * v3;
        double v2 = st[off + 1] + a2 * st[off] + a3 * v3;
        st[off] = 2.0 * v1 - st[off];
        st[off + 1] = 2.0 * v2 - st[off + 1];
        return switch (mode) {
            case 1 -> v1;
            case 2 -> x - k * v1 - v2;
            default -> v2;
        };
    }

    private static void writeWavHeader(byte[] out, int channels, int frames) {
        int blockAlign = channels * 2;
        int dataBytes = frames * blockAlign;
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putIntLE(out, 4, 36 + dataBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putIntLE(out, 16, 16);
        putShortLE(out, 20, 1);
        putShortLE(out, 22, channels);
        putIntLE(out, 24, SAMPLE_RATE);
        putIntLE(out, 28, SAMPLE_RATE * blockAlign);
        putShortLE(out, 32, blockAlign);
        putShortLE(out, 34, 16);
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putIntLE(out, 40, dataBytes);
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