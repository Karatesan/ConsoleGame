package com.archon.ui.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Audio coordinator (44.1 kHz):
 * - 22 spatialized mono SFX kinds x up to 4 studio variants (loaded by SfxGen from /audio/sfx/)
 * - Constant-volume streaming orchestral music & cavern ambience via MusicSynth (HP-loss tension only)
 * Zero heap allocations during play(). Starts muted for headless tests.
 */
public final class SynthAudio {
    public static final int KEY = SfxGen.KEY, SUBMIT = SfxGen.SUBMIT, REJECT = SfxGen.REJECT,
            HIT = SfxGen.HIT, SHOOT = SfxGen.SHOOT, SPLASH = SfxGen.SPLASH, ZAP = SfxGen.ZAP,
            BOOM = SfxGen.BOOM, CRIT = SfxGen.CRIT, FIRE = SfxGen.FIRE, SIZZLE = SfxGen.SIZZLE,
            SWING = SfxGen.SWING, STEP = SfxGen.STEP, PSIONIC = SfxGen.PSIONIC,
            SHATTER = SfxGen.SHATTER, HEAL = SfxGen.HEAL, HIT_PROP = SfxGen.HIT_PROP,
            STING_KILL = SfxGen.STING_KILL, STING_DEATH = SfxGen.STING_DEATH,
            STING_BLOOD = SfxGen.STING_BLOOD, SIPHON = SfxGen.SIPHON,
            BLOOD_BOLT = SfxGen.BLOOD_BOLT, COUNT = SfxGen.COUNT;
    public static final int VARIANTS = SfxGen.VARIANTS;
    public static final int TRACK_COUNT = MusicSynth.TRACK_COUNT;

    private static final long MIN_RETRIGGER_NS = 22_000_000L;
    private static final int MAX_DELAYED = 16;

    final MusicSynth music = new MusicSynth();
    private final Sound[][] sounds = new Sound[COUNT][];
    private final int[] variantCursor = new int[COUNT];
    private final long[] lastPlayNs = new long[COUNT];
    private final float[] delTimer = new float[MAX_DELAYED], delVol = new float[MAX_DELAYED],
            delPitch = new float[MAX_DELAYED], delPan = new float[MAX_DELAYED];
    private final int[] delKind = new int[MAX_DELAYED], delTier = new int[MAX_DELAYED];
    private int delCount = 0;

    private int playRng = 0x51A7F3B9;
    public boolean muted = true;
    public boolean musicMuted = false;

    public SynthAudio() {
        music.muted = true;
    }

    public void init() {
        if (Gdx.audio == null) return;
        long mix = System.nanoTime() ^ (System.currentTimeMillis() * 0x9E3779B97F4A7C15L);
        playRng ^= (int) (mix ^ (mix >>> 32));
        if (playRng == 0) playRng = 0x51A7F3B9;
        playRng = Dsp.xorshift(Dsp.xorshift(playRng));

        for (int k = 0; k < COUNT; k++) {
            int n = SfxGen.variantsFor(k);
            sounds[k] = new Sound[n];
            for (int v = 0; v < n; v++) {
                sounds[k][v] = loadWav("sfx_" + k + "_" + v + ".wav", SfxGen.buildWav(k, v));
            }
        }

        muted = false;
        music.muted = false;
        music.musicMuted = musicMuted;
        music.forcedTrack = -1;
        music.seedRng(playRng);
        music.start();
    }

    public void setCombat(boolean inCombat) {
        music.inCombat = inCombat;
    }

    public void setHpRatio(float hpRatio) {
        music.targetTension = clamp01(1f - hpRatio);
    }

    public void setTrack(int track) {
        music.forcedTrack = track;
    }

    public void updateMusic(float dt) {
        for (int i = 0; i < delCount; ) {
            delTimer[i] -= dt;
            if (delTimer[i] <= 0f) {
                int k = delKind[i], tier = delTier[i];
                float v = delVol[i], p = delPitch[i], pan = delPan[i];
                int last = --delCount;
                delKind[i] = delKind[last];
                delTier[i] = delTier[last];
                delTimer[i] = delTimer[last];
                delVol[i] = delVol[last];
                delPitch[i] = delPitch[last];
                delPan[i] = delPan[last];
                playInternal(k, tier, v, p, pan);
            } else {
                i++;
            }
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    public boolean toggleMute() {
        muted = !muted;
        music.muted = muted;
        return muted;
    }

    public boolean toggleMusic() {
        musicMuted = !musicMuted;
        music.musicMuted = musicMuted;
        return musicMuted;
    }

    public void play(int kind, float volume) {
        playInternal(kind, -1, volume, 1f, 0f);
    }

    public void play(int kind, float volume, float pitch, float pan) {
        playInternal(kind, -1, volume, pitch, pan);
    }

    public void playTier(int kind, int tier, float volume, float pitch, float pan) {
        playInternal(kind, tier, volume, pitch, pan);
    }

    private void playInternal(int kind, int tier, float volume, float pitch, float pan) {
        if (muted || kind < 0 || kind >= COUNT || sounds[kind] == null) return;
        long now = System.nanoTime();
        if (kind != KEY && kind != HIT && kind != HIT_PROP && now - lastPlayNs[kind] < MIN_RETRIGGER_NS) return;
        lastPlayNs[kind] = now;

        Sound[] pool = sounds[kind];
        int n = pool.length;
        int idx;
        if (tier >= 0) {
            idx = Math.max(0, Math.min(n - 1, tier));
        } else if (n > 1) {
            playRng = Dsp.xorshift(playRng);
            int step = 1 + ((playRng >>> 1) % (n - 1));
            idx = (variantCursor[kind] + step) % n;
            variantCursor[kind] = idx;
        } else {
            idx = 0;
        }
        playSample(kind, idx, volume, pitch, pan);
    }

    public void playDelayed(int kind, float delaySec, float volume, float pitch, float pan) {
        if (delaySec <= 0f) playInternal(kind, -1, volume, pitch, pan);
        else enqueueDelayed(kind, -1, delaySec, volume, pitch, pan);
    }

    public void playTierDelayed(int kind, int tier, float delaySec, float volume, float pitch, float pan) {
        if (delaySec <= 0f) playInternal(kind, tier, volume, pitch, pan);
        else enqueueDelayed(kind, tier, delaySec, volume, pitch, pan);
    }

    private void enqueueDelayed(int kind, int tier, float delaySec, float volume, float pitch, float pan) {
        if (muted || kind < 0 || kind >= COUNT || sounds[kind] == null || delCount >= MAX_DELAYED) return;
        int i = delCount++;
        delKind[i] = kind;
        delTier[i] = tier;
        delTimer[i] = delaySec;
        delVol[i] = volume;
        delPitch[i] = pitch;
        delPan[i] = pan;
    }

    private void playSample(int kind, int idx, float volume, float pitch, float pan) {
        Sound s = sounds[kind][idx];
        if (s == null) return;
        if (kind != KEY && kind != SUBMIT && kind != REJECT) {
            playRng = Dsp.xorshift(playRng);
            float volJit = 0.93f + ((playRng & 0xFF) / 255f) * 0.14f;
            volume = Math.min(1.0f, volume * volJit);
            if (kind != HEAL && kind != STING_KILL && kind != STING_DEATH && kind != STING_BLOOD) {
                float pitchJit = 0.97f + (((playRng >>> 8) & 0xFF) / 255f) * 0.06f;
                pitch *= pitchJit;
            }
        }
        s.play(volume, pitch, pan);
    }

    public void dispose() {
        music.stop();
        delCount = 0;
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
    }

    private static Sound loadWav(String name, byte[] wav) {
        return Gdx.audio.newSound(new FileHandle(name) {
            @Override
            public InputStream read() {
                return new ByteArrayInputStream(wav);
            }
        });
    }
}
