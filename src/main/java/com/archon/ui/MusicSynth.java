package com.archon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

/**
 * Constant-volume real-time 44.1 kHz stereo sampled orchestral music & cavern ambience engine.
 * - 3 adaptive tracks backed by CC0 VSCO 2 CE recordings (SampleBank):
 *   (0) Cathedral Harp, Cello & Strings, (1) Driving Cello Spiccato, Violin & Timpani,
 *   (2) War-March Cello Spiccato, French Horn & Taiko/Bass War Drums
 * - Shared FDN8 stone hall reverb, stereo bus compressor, and steady cavern ambient bed
 * - Only reacts to player HP loss via harmonic/timbral drama (never drops volume when killing enemies)
 */
public final class MusicSynth {
    public static final int TRACK_COUNT = 3;
    public static final int BLOCK_FRAMES = 1024;

    private static final int SR = Dsp.SAMPLE_RATE;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double INV_32768 = 1.0 / 32768.0;
    private static final double GAIN_STEP = 1.0 / (SR * 0.015);
    private static final double COMBAT_STEP = 1.0 / (SR * 1.5);
    private static final double MIX_STEP = 1.0 / (SR * 0.30);
    private static final double AMBIENT_VOL = 0.05;
    private static final double MUSIC_VOL = 0.13;
    private static final double COMP_THR = 0.36, COMP_RATIO = 3.0, COMP_MAKEUP = 1.35;
    private static final double COMP_ATT = 1.0 - Math.exp(-1.0 / (SR * 0.005));
    private static final double COMP_REL = 1.0 - Math.exp(-1.0 / (SR * 0.150));

    private static final int MAX_VOICES = 48, MAX_PENDING = 16;
    private static final int BUS_ALL = 0, BUS_EXPLORE = 1, BUS_COMBAT = 2,
            BUS_CELLO = 3, BUS_PAD = 4, BUS_HORN_PEDAL = 5;
    private static final int GRP_NONE = 0, GRP_BASS = 1, GRP_CELLO = 2, GRP_PAD = 3,
            GRP_HORN_PEDAL = 4, GRP_LEAD = 5, GRP_HORN_MEL = 6;
    private static final int NOTE_HARP = 0, NOTE_STRINGS = 1, NOTE_BRASS = 2, NOTE_SPIC = 3,
            NOTE_TIMPANI = 4, NOTE_WARDRUM = 5, NOTE_SNARE_HEAVY = 6, NOTE_SNARE_LIGHT = 7;

    // 4 compatible D-minor progressions (0=Dm, 1=Bb, 2=Gm, 3=F, 4=Am)
    private static final int[][] PROGRESSIONS = {
            {0, 1, 2, 4},
            {0, 3, 2, 4},
            {0, 3, 1, 4},
            {0, 2, 1, 4}
    };
    private static final double[] CHORD_ROOT = {36.75, 58.25, 49.00, 43.75, 55.00};
    private static final double[] TIMP_ROOT = {73.42, 58.27, 98.00, 87.31, 110.00};
    private static final double[] TIMP_FIFTH = {110.00, 87.31, 73.42, 65.41, 82.41};

    private static final double[][] CHORD_ARP = {
            {146.83, 220.00, 293.66, 349.23}, // Dm
            {116.54, 174.61, 293.66, 349.23}, // Bb
            {98.00,  196.00, 233.08, 293.66}, // Gm
            {130.81, 174.61, 220.00, 261.63}, // F
            {110.00, 164.81, 220.00, 261.63}  // Am (C4 becomes C#4 277.18 under harmonic tension)
    };
    private static final double[][] CHORD_SCALE = {
            {146.83, 174.61, 196.00, 220.00, 261.63, 293.66},
            {116.54, 146.83, 174.61, 196.00, 220.00, 233.08},
            {98.00,  146.83, 174.61, 196.00, 233.08, 293.66},
            {174.61, 196.00, 220.00, 261.63, 293.66, 329.63},
            {110.00, 164.81, 196.00, 220.00, 261.63, 329.63}
    };
    private static final double[] PAD_N1 = {293.66, 293.66, 293.66, 261.63, 261.63};
    private static final double[] PAD_N2 = {349.23, 349.23, 349.23, 349.23, 329.63};
    private static final double[] ARP_PANS = {0.28, 0.72, 0.35, 0.68};

    // Composed 1-2 bar phrases: Track 0 Harp (8-step), Track 1 Violin (24-step), Track 2 French Horn
    private static final int[] HARP_PAT_A = {0, -1, 1, 2, 3, -1, 2, 1};
    private static final int[] HARP_PAT_B = {0, 1, 2, -1, 3, 2, 1, -1};
    private static final int[] VIOLIN_PAT_A = {
            0, -1, -1, -1, -1, -1, 3, -1, -1, 2, -1, 3,
            5, -1, -1, -1, -1, -1, 3, -1, -1, -1, -1, -1
    };
    private static final int[] VIOLIN_PAT_B = {
            3, -1, -1, -1, -1, -1, 5, -1, -1, 3, -1, 2,
            1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };
    private static final double[][] HORN_MEL = {
            {146.83, 220.00, 174.61}, // Dm: D3 -> A3 -> F3
            {116.54, 174.61, 146.83}, // Bb: Bb2 -> F3 -> D3
            {146.83, 196.00, 174.61}, // Gm: D3 -> G3 -> F3
            {174.61, 220.00, 261.63}, // F:  F3 -> A3 -> C4
            {164.81, 220.00, 261.63}  // Am: E3 -> A3 -> C4 (C4 -> C#4 under tension)
    };
    // Grounded 12-step percussion patterns for Tracks 0, 1, 2 (0=rest, 1=fifth/light, 2=root/heavy)
    private static final int[][] TIMP_PAT = {
            {2, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
            {2, 0, 0, 1, 0, 2, 2, 0, 0, 1, 0, 1},
            {2, 0, 1, 0, 0, 2, 2, 0, 0, 1, 0, 2}
    };
    private static final int[][] SNARE_PAT = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 2, 0, 0, 0, 0, 0, 2, 1, 1},
            {0, 0, 0, 2, 0, 0, 0, 0, 0, 2, 0, 1}
    };

    // Lock-free controls
    volatile boolean muted = false;
    volatile boolean musicMuted = false;
    volatile boolean inCombat = false;
    volatile float targetTension = 0f;
    volatile int forcedTrack = -1;

    private AudioDevice device;
    private Thread thread;
    private volatile boolean running = false;
    private final short[] pcmBuf = new short[BLOCK_FRAMES * 2];

    // Gain, combat mix & tension state
    private double masterGain = 0.0, musicGain = 1.0, combatMix = 0.0, tension = 0.0;
    private double celloMix = 1.0, padGain = 0.45, hornPedalGain = 0.0;
    private final double[] busGains = new double[6];
    private boolean musicCleared = false;
    private int combatTrack = 1, lastShuffledBar = -1;

    // Master musical clock
    private double beatPos = 0.0;
    private int barIdx = 0;
    private int prevBarIdx = -1, prevEighth = -1, prevCStep = -1;
    private double prevBeatFrac = 1.0;

    // RNG, ambient & heartbeat state
    private int rngL = 0x13579BDF, rngR = 0x2468ACE1;
    private double pinkL = 0.0, pinkR = 0.0, crackEnv = 0.0, crackPan = 0.5;
    private double ambLfoP = 0.0, ambDrone1P = 0.0, ambDrone2P = 0.0;
    private final double[] svfAmbL = new double[4], svfAmbR = new double[4];
    private double gAirL = Dsp.svfG(195.0), gAirR = Dsp.svfG(195.0), gCrack = Dsp.svfG(1800.0);
    private final double kAir = 1.0 / 0.80, kCrack = 1.0 / 1.8, kHb = 1.0 / 0.9;
    private final double gHb = Dsp.svfG(140.0);
    private double hbLubAge = 10.0, hbDubAge = 10.0, hbLubP = 0.0, hbDubP = 0.0;
    private final double[] svfHbL = new double[2], svfHbR = new double[2];

    // Zero-allocation sampler voice pool
    private final short[][] vPcm = new short[MAX_VOICES][];
    private final boolean[] vActive = new boolean[MAX_VOICES];
    private final int[] vBus = new int[MAX_VOICES], vGroup = new int[MAX_VOICES];
    private final double[] vPos = new double[MAX_VOICES], vStep = new double[MAX_VOICES],
            vGain = new double[MAX_VOICES], vPan = new double[MAX_VOICES], vSend = new double[MAX_VOICES],
            vAge = new double[MAX_VOICES], vAtt = new double[MAX_VOICES],
            vRelStart = new double[MAX_VOICES], vRelLen = new double[MAX_VOICES];

    // Micro-timing humanization queue
    private final boolean[] pendActive = new boolean[MAX_PENDING];
    private final int[] pendKind = new int[MAX_PENDING], pendDelay = new int[MAX_PENDING];
    private final double[] pendFreq = new double[MAX_PENDING], pendPan = new double[MAX_PENDING],
            pendGain = new double[MAX_PENDING], pendMaxAge = new double[MAX_PENDING];

    // FDN8 hall reverb, bus compressor & DC blockers
    private final Dsp.Reverb hall = new Dsp.Reverb(0.032, 2.4);
    private double compEnv = 0.0, compGain = 1.0;
    private double dcSendInL = 0.0, dcSendStL = 0.0, dcSendInR = 0.0, dcSendStR = 0.0;
    private double dcMixInL = 0.0, dcMixStL = 0.0, dcMixInR = 0.0, dcMixStR = 0.0;

    static {
        // Eagerly validate & load CC0 VSCO 2 CE samples before audio thread starts
        if (SampleBank.BANKS.length != SampleBank.BANK_COUNT) {
            throw new IllegalStateException("Unexpected bank count");
        }
    }

    void seedRng(int seed) {
        int s = seed == 0 ? 0x51A7F3B9 : seed;
        rngL = Dsp.xorshift(s ^ 0x13579BDF);
        rngR = Dsp.xorshift(s ^ 0x2468ACE1);
        if (rngL == 0) rngL = 0x13579BDF;
        if (rngR == 0) rngR = 0x2468ACE1;
        combatTrack = 1 + ((rngL >>> 1) & 1);
    }

    public void start() {
        if (running || Gdx.audio == null) return;
        masterGain = 0.0;
        clearMusicState();
        device = Gdx.audio.newAudioDevice(SR, false);
        running = true;
        Thread t = new Thread(this::audioLoop, "archon-music");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY + 1);
        thread = t;
        t.start();
    }

    public void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        AudioDevice d = device;
        device = null;
        if (d != null) d.dispose();
    }

    private void audioLoop() {
        AudioDevice d = device;
        while (running && !Thread.currentThread().isInterrupted()) {
            renderBlock(pcmBuf, BLOCK_FRAMES);
            d.writeSamples(pcmBuf, 0, pcmBuf.length);
        }
    }

    /** Renders stereo interleaved 16-bit PCM frames headlessly with zero allocations. */
    public void renderBlock(short[] stereoOut, int frames) {
        double masterTarget = muted ? 0.0 : 1.0;
        double musicTarget = musicMuted ? 0.0 : 1.0;
        double goalTension = targetTension;
        int ft = forcedTrack;
        double combatTarget = (ft >= 0 && ft < TRACK_COUNT) ? (ft == 0 ? 0.0 : 1.0) : (inCombat ? 1.0 : 0.0);

        double tenStep = 0.8 / SR;
        double dt = 1.0 / SR;

        for (int i = 0; i < frames; i++) {
            masterGain = stepToward(masterGain, masterTarget, GAIN_STEP);
            musicGain = stepToward(musicGain, musicTarget, GAIN_STEP);
            combatMix = stepToward(combatMix, combatTarget, COMBAT_STEP);
            tension = stepToward(tension, goalTension, tenStep);

            double bpm = (62.0 + 8.0 * combatMix) * (1.0 + 0.08 * tension * tension);
            beatPos += (bpm / 60.0) / SR;
            if (beatPos >= 4.0) {
                beatPos -= 4.0;
                barIdx++;
            }

            if (masterGain == 0.0 && masterTarget == 0.0) {
                if (!musicCleared) {
                    clearMusicState();
                    musicCleared = true;
                }
                stereoOut[i * 2] = 0;
                stereoOut[i * 2 + 1] = 0;
                continue;
            }

            rngL = Dsp.xorshift(rngL);
            rngR = Dsp.xorshift(rngR);
            double nL = Dsp.noise(rngL);
            double nR = Dsp.noise(rngR);
            double antiDenorm = ((i & 1) == 0) ? 1e-24 : -1e-24;

            // --- 1. STEADY AMBIENT CAVERN BED ---
            ambLfoP = incPhase(ambLfoP, 0.25);
            ambDrone1P = incPhase(ambDrone1P, 73.50);
            ambDrone2P = incPhase(ambDrone2P, 36.75);
            if ((i & 63) == 0) {
                gAirL = Dsp.svfG(195.0 + 45.0 * Math.sin(TWO_PI * ambLfoP));
                gAirR = Dsp.svfG(195.0 + 45.0 * Math.cos(TWO_PI * ambLfoP));
            }
            pinkL += 0.015 * (nL - pinkL);
            pinkR += 0.015 * (nR - pinkR);
            double airL = Dsp.svfFixed(pinkL * 4.2, gAirL, kAir, svfAmbL, 0, 0);
            double airR = Dsp.svfFixed(pinkR * 4.2, gAirR, kAir, svfAmbR, 0, 0);
            if ((rngL & 0x1FFF) < 3) {
                crackEnv = 0.35 + 0.65 * (((rngR >>> 8) & 0xFF) / 255.0);
                crackPan = 0.20 + 0.60 * (((rngR >>> 16) & 0xFF) / 255.0);
                gCrack = Dsp.svfG(1400.0 + 1400.0 * (((rngL >>> 12) & 0xFF) / 255.0));
            }
            crackEnv *= 0.990;
            double crk = Dsp.svfFixed(nL * crackEnv, gCrack, kCrack, svfAmbL, 2, 1) * 0.32;
            double drone = Math.sin(TWO_PI * ambDrone1P) * 0.040 + Math.sin(TWO_PI * ambDrone2P) * 0.022;
            double ambL = (airL * 0.72 + crk * (1.0 - crackPan) + drone) * AMBIENT_VOL;
            double ambR = (airR * 0.72 + crk * crackPan + drone) * AMBIENT_VOL;

            if (musicGain == 0.0 && musicTarget == 0.0) {
                if (!musicCleared) {
                    clearMusicState();
                    musicCleared = true;
                }
                stereoOut[i * 2] = toShort(Dsp.softClip(ambL) * masterGain);
                stereoOut[i * 2 + 1] = toShort(Dsp.softClip(ambR) * masterGain);
                continue;
            }
            musicCleared = false;

            // --- 2. CLOCK, HARMONY, BAR-START PADS & NOTE SCHEDULING ---
            int cTrack = (ft == 1 || ft == 2) ? ft : combatTrack;
            int phraseIdx = barIdx >>> 2;
            int section = phraseIdx & 3;
            int barInPhrase = barIdx & 3;
            int chordIdx = PROGRESSIONS[phraseIdx & 3][barInPhrase];
            double barNorm = beatPos * 0.25;
            double phraseSwell = 0.90 + 0.20 * Math.sin(Math.PI * (barInPhrase + barNorm) / 4.0);

            if (barIdx != prevBarIdx) {
                prevBarIdx = barIdx;
                if (ft < 0 && combatTarget > 0.5 && barIdx > 0 && (barIdx & 15) == 0 && barIdx != lastShuffledBar) {
                    lastShuffledBar = barIdx;
                    combatTrack = combatTrack == 1 ? 2 : 1;
                    cTrack = combatTrack;
                }

                // Trigger real sustained contrabass, cello section, violin pad & horn pedal for this bar
                double padRel = combatMix < 0.5 ? 0.55 : 0.30;
                double padAtt = combatMix < 0.5 ? 0.38 : 0.20;
                releaseGroup(GRP_BASS, 0.30);
                releaseGroup(GRP_CELLO, padRel);
                releaseGroup(GRP_PAD, padRel);
                releaseGroup(GRP_HORN_PEDAL, 0.30);

                triggerSample(SampleBank.BASS_SUS, CHORD_ROOT[chordIdx], 0.50,
                        0.36 * phraseSwell, 0.15, 0.16, 4.2, 0.35, BUS_ALL, GRP_BASS);
                triggerSample(SampleBank.CELLO_SUS, CHORD_ROOT[chordIdx] * 2.0, 0.36,
                        0.42 * phraseSwell, 0.38, padAtt, 4.2, padRel, BUS_CELLO, GRP_CELLO);
                triggerSample(SampleBank.CELLO_SUS, CHORD_ROOT[chordIdx] * 3.0, 0.64,
                        0.34 * phraseSwell, 0.38, padAtt, 4.2, padRel, BUS_CELLO, GRP_CELLO);

                double p1 = colorPadNote(PAD_N1[chordIdx], chordIdx, true);
                double p2 = colorPadNote(PAD_N2[chordIdx], chordIdx, false);
                triggerSample(SampleBank.VIOLIN_SUS, p1, 0.34,
                        0.44 * phraseSwell, 0.45, padAtt, 4.0, padRel, BUS_PAD, GRP_PAD);
                triggerSample(SampleBank.VIOLIN_SUS, p2, 0.66,
                        0.38 * phraseSwell, 0.45, padAtt, 4.0, padRel, BUS_PAD, GRP_PAD);
                triggerSample(SampleBank.HORN_SUS, CHORD_ROOT[chordIdx] * 2.0, 0.50,
                        0.30 * phraseSwell, 0.36, 0.12, 4.0, 0.35, BUS_HORN_PEDAL, GRP_HORN_PEDAL);
            }

            int expStep = (int) beatPos;
            int eighthStep = (int) (beatPos * 2.0);
            int cStep = (int) (beatPos * 6.0);
            int patStep = cStep % 12;
            int barStep = cStep % 24;
            double beatFrac = beatPos - expStep;

            // Track 0: Expressive Concert Harp (composed 2-bar phrasing)
            if (eighthStep != prevEighth) {
                prevEighth = eighthStep;
                if (combatMix < 0.85) {
                    boolean sparseIntro = section == 0 && barInPhrase == 0;
                    int arpIdx = sparseIntro
                            ? ((eighthStep & 1) == 0 ? ((eighthStep >> 1) & 3) : -1)
                            : ((barInPhrase & 1) == 0 ? HARP_PAT_A : HARP_PAT_B)[eighthStep & 7];
                    if (arpIdx >= 0) {
                        double harpF = colorNote(CHORD_ARP[chordIdx][arpIdx], chordIdx) * 2.0;
                        double beatWeight = (eighthStep == 0 || eighthStep == 4) ? 1.15 : 0.88;
                        int delay = eighthStep == 0 ? 0 : 176 + ((rngL >>> 4) % 441);
                        scheduleNote(NOTE_HARP, harpF, ARP_PANS[arpIdx],
                                0.26 * beatWeight * humanVel(phraseSwell), 2.2, delay);
                    }
                }
            }

            // 12/8 Triplet Step Grid (Combat Spiccato, Monophonic 2-bar Melodies, Timpani, War Drums & Snare)
            if (cStep != prevCStep) {
                prevCStep = cStep;
                double rootTimp = TIMP_ROOT[chordIdx];
                double fifthTimp = TIMP_FIFTH[chordIdx];

                if (combatMix > 0.15 && cTrack == 1) {
                    boolean accent = patStep == 0 || patStep == 3 || patStep == 5 || patStep == 6 || patStep == 9;
                    double spGain = (accent ? 0.54 : 0.26) * humanVel(phraseSwell);
                    scheduleNote(NOTE_SPIC, CHORD_ROOT[chordIdx] * 2.0, 0.44, spGain, 0.45, ((rngL >>> 5) % 64));
                    if (accent) {
                        scheduleNote(NOTE_SPIC, CHORD_ROOT[chordIdx] * 3.0, 0.56, spGain * 0.72, 0.45, ((rngL >>> 6) % 64));
                    }
                    int deg = ((barInPhrase & 1) == 0 ? VIOLIN_PAT_A : VIOLIN_PAT_B)[barStep];
                    if (deg >= 0) {
                        double lf = colorNote(CHORD_SCALE[chordIdx][deg], chordIdx) * 2.0;
                        int delay = barStep == 0 ? 0 : ((rngR >>> 5) % 88);
                        scheduleNote(NOTE_STRINGS, lf, 0.42, 0.56 * humanVel(phraseSwell), 1.25, delay);
                    }
                } else if (combatMix > 0.15 && cTrack == 2) {
                    if (patStep == 0 || patStep == 5 || patStep == 6 || patStep == 11) {
                        int delay = patStep == 0 ? 0 : ((rngL >>> 5) % 64);
                        scheduleNote(NOTE_SPIC, CHORD_ROOT[chordIdx] * 2.0, 0.42,
                                0.48 * humanVel(phraseSwell), 0.45, delay);
                        scheduleNote(NOTE_SPIC, CHORD_ROOT[chordIdx] * 3.0, 0.58,
                                0.34 * humanVel(phraseSwell), 0.45, delay);
                    }
                    if ((barInPhrase & 1) == 0) {
                        if (barStep == 0 || barStep == 12 || barStep == 18) {
                            double hf = barStep == 0 ? HORN_MEL[chordIdx][0]
                                    : (barStep == 12 ? HORN_MEL[chordIdx][1] : colorNote(HORN_MEL[chordIdx][2], chordIdx));
                            double hold = barStep == 0 ? 1.55 : (barStep == 12 ? 1.00 : 0.95);
                            scheduleNote(NOTE_BRASS, hf, 0.56, 0.54 * humanVel(phraseSwell), hold,
                                    barStep == 0 ? 0 : ((rngR >>> 6) % 88));
                        }
                    } else if (barStep == 0 || barStep == 12) {
                        double hf = barStep == 0 ? HORN_MEL[chordIdx][1] : colorNote(HORN_MEL[chordIdx][2], chordIdx);
                        double hold = barStep == 0 ? 1.80 : 1.40;
                        scheduleNote(NOTE_BRASS, hf, 0.56, 0.54 * humanVel(phraseSwell), hold,
                                barStep == 0 ? 0 : ((rngR >>> 6) % 88));
                    }
                }

                // Real Timpani, Orchestral Bass/War Drum & Concert Snare
                int drumTrack = combatMix >= 0.5 ? cTrack : 0;
                int kType = TIMP_PAT[drumTrack][patStep];
                int sType = SNARE_PAT[drumTrack][patStep];
                boolean rollWindow = (barInPhrase == 3) && (section == 3 || tension >= 0.70)
                        && (cStep >= (tension >= 0.70 ? 16 : 20));
                if (rollWindow) {
                    if (drumTrack == 0) {
                        if ((cStep & 1) == 0) kType = (cStep >= 22) ? 2 : 1;
                    } else if ((cStep & 1) == 0) {
                        kType = 2;
                    } else {
                        sType = 2;
                    }
                }
                if (kType > 0) {
                    double timpF = kType == 2 ? rootTimp : fifthTimp;
                    double timpGain = drumTrack == 0 ? (kType == 2 ? 0.36 : 0.24)
                            : (kType == 2 ? 0.72 : 0.48);
                    int kDelay = combatMix >= 0.5
                            ? (patStep == 0 ? 0 : ((rngL >>> 2) % 88))
                            : (patStep == 0 ? ((rngL >>> 2) % 88) : 132 + ((rngL >>> 2) % 240));
                    scheduleNote(NOTE_TIMPANI, timpF, 0.50, timpGain * humanVel(phraseSwell), 1.60, kDelay);
                    if (kType == 2 && combatMix > 0.20) {
                        scheduleNote(NOTE_WARDRUM, 0.0, 0.50, 0.85 * humanVel(phraseSwell), 1.50, kDelay);
                    } else if (kType == 1 && drumTrack == 2 && combatMix > 0.30) {
                        scheduleNote(NOTE_WARDRUM, 0.0, 0.50, 0.42 * humanVel(phraseSwell), 1.50, kDelay);
                    }
                }
                if (sType > 0) {
                    double sGain = sType == 2 ? 0.70 : 0.44;
                    int sDelay = combatMix >= 0.5 ? ((rngR >>> 3) % 88) : 176 + ((rngR >>> 3) % 420);
                    scheduleNote(sType == 2 ? NOTE_SNARE_HEAVY : NOTE_SNARE_LIGHT,
                            0.0, 0.52, sGain * humanVel(phraseSwell), 0.55, sDelay);
                }
            }
            drainPendingNotes();

            if (beatFrac < prevBeatFrac) hbLubAge = 0.0;
            if (prevBeatFrac < 0.24 && beatFrac >= 0.24) hbDubAge = 0.0;
            prevBeatFrac = beatFrac;

            // --- 3. SMOOTHED BUS GAINS & REVERB TUNING ---
            double targetCello = 1.0 + ((cTrack == 1 ? 0.16 : 0.22) - 1.0) * combatMix;
            double targetPad = 0.45 + ((cTrack == 2 && section > 0 ? 0.26 : 0.0) - 0.45) * combatMix;
            double targetHornPedal = (cTrack == 2 ? 0.40 : (section >= 2 ? 0.28 : 0.0)) * combatMix;
            celloMix = stepToward(celloMix, targetCello, MIX_STEP);
            padGain = stepToward(padGain, targetPad, MIX_STEP);
            hornPedalGain = stepToward(hornPedalGain, targetHornPedal, MIX_STEP);

            busGains[BUS_ALL] = 1.0;
            busGains[BUS_EXPLORE] = 1.0 - combatMix;
            busGains[BUS_COMBAT] = combatMix;
            busGains[BUS_CELLO] = celloMix;
            busGains[BUS_PAD] = padGain;
            busGains[BUS_HORN_PEDAL] = hornPedalGain;

            if ((i & 63) == 0) {
                hall.setRt60(2.4 + (1.4 - 2.4) * combatMix);
                hall.bright = 0.32 + 0.08 * combatMix;
            }

            // --- 4. MIX ZERO-ALLOCATION SAMPLER VOICES ---
            double dryL = 0.0, dryR = 0.0, sendL = 0.0, sendR = 0.0;
            for (int v = 0; v < MAX_VOICES; v++) {
                if (!vActive[v]) continue;
                short[] pcm = vPcm[v];
                double pos = vPos[v];
                int i0 = (int) pos;
                if (i0 >= pcm.length - 1) {
                    vActive[v] = false;
                    continue;
                }
                double age = vAge[v];
                double relStart = vRelStart[v];
                double relLen = vRelLen[v];
                if (age >= relStart + relLen) {
                    vActive[v] = false;
                    continue;
                }
                double env = age < vAtt[v] ? age / vAtt[v] : 1.0;
                if (age > relStart) {
                    double r = 1.0 - (age - relStart) / relLen;
                    if (r < env) env = Math.max(0.0, r);
                }
                double frac = pos - i0;
                double s = (pcm[i0] + (pcm[i0 + 1] - pcm[i0]) * frac) * INV_32768;
                double amp = s * env * vGain[v] * busGains[vBus[v]];
                double pan = vPan[v];
                double l = amp * (1.0 - pan) * 2.0;
                double r = amp * pan * 2.0;
                dryL += l;
                dryR += r;
                double snd = vSend[v];
                sendL += l * snd;
                sendR += r * snd;
                vPos[v] = pos + vStep[v];
                vAge[v] = age + dt;
            }

            // --- 5. SUBTLE LOW-HP HEARTBEAT PULSE ---
            if (tension >= 0.65) {
                double hbAmt = Math.min(1.0, (tension - 0.65) / 0.35) * 0.14;
                double lub = 0.0, dub = 0.0;
                if (hbLubAge < 0.6) {
                    hbLubP = incPhase(hbLubP, 30.0 + 18.0 * Math.exp(-hbLubAge / 0.04));
                    lub = Math.sin(TWO_PI * hbLubP) * Math.min(1.0, hbLubAge / 0.008) * Math.exp(-hbLubAge / 0.095) * 0.42;
                    hbLubAge += dt;
                }
                if (hbDubAge < 0.6) {
                    hbDubP = incPhase(hbDubP, 27.0 + 14.0 * Math.exp(-hbDubAge / 0.035));
                    dub = Math.sin(TWO_PI * hbDubP) * Math.min(1.0, hbDubAge / 0.008) * Math.exp(-hbDubAge / 0.085) * 0.30;
                    hbDubAge += dt;
                }
                dryL += Dsp.svfFixed(lub * 1.05 + dub * 0.95, gHb, kHb, svfHbL, 0, 0) * hbAmt;
                dryR += Dsp.svfFixed(lub * 0.95 + dub * 1.05, gHb, kHb, svfHbR, 0, 0) * hbAmt;
            }

            // --- 6. FDN8 STONE HALL REVERB, BUS COMPRESSOR, DC BLOCKING & MASTER SUM ---
            double sL = sendL - dcSendInL + 0.998 * dcSendStL; dcSendInL = sendL; dcSendStL = sL;
            double sR = sendR - dcSendInR + 0.998 * dcSendStR; dcSendInR = sendR; dcSendStR = sR;

            hall.step(sL + antiDenorm, sR + antiDenorm);

            double wetRet = 0.45 + (0.25 - 0.45) * combatMix;
            double busL = dryL + hall.outL * wetRet;
            double busR = dryR + hall.outR * wetRet;
            double peak = Math.max(Math.abs(busL), Math.abs(busR));
            compEnv += (peak > compEnv ? COMP_ATT : COMP_REL) * (peak - compEnv);
            if ((i & 15) == 0) {
                compGain = compEnv > COMP_THR ? Math.pow(compEnv / COMP_THR, 1.0 / COMP_RATIO - 1.0) : 1.0;
            }
            double musL = busL * compGain * COMP_MAKEUP * MUSIC_VOL;
            double musR = busR * compGain * COMP_MAKEUP * MUSIC_VOL;
            double mixL = ambL + musL * musicGain;
            double mixR = ambR + musR * musicGain;

            double dcL = mixL - dcMixInL + 0.998 * dcMixStL; dcMixInL = mixL; dcMixStL = dcL;
            double dcR = mixR - dcMixInR + 0.998 * dcMixStR; dcMixInR = mixR; dcMixStR = dcR;

            stereoOut[i * 2] = toShort(Dsp.softClip(dcL) * masterGain);
            stereoOut[i * 2 + 1] = toShort(Dsp.softClip(dcR) * masterGain);
        }
    }

    /** Applies Harmonic Minor C#4 cadence tension over Am -> A7. */
    private double colorNote(double freq, int chordIdx) {
        if (tension >= 0.35 && chordIdx == 4 && Math.abs(freq - 261.63) < 2.0) {
            return 277.18;
        }
        return freq;
    }

    private double colorPadNote(double freq, int chordIdx, boolean isFirst) {
        if (tension >= 0.35 && chordIdx == 4 && isFirst) return 277.18;
        return freq;
    }

    private double humanVel(double phraseSwell) {
        rngL = Dsp.xorshift(rngL);
        double jit = 0.88 + 0.24 * (((rngL >>> 8) & 0xFF) / 255.0);
        return jit * phraseSwell;
    }

    private void scheduleNote(int kind, double freq, double pan, double gain, double maxAge, int delaySamples) {
        if (delaySamples <= 0) {
            dispatchNote(kind, freq, pan, gain, maxAge);
            return;
        }
        int slot = -1;
        for (int i = 0; i < MAX_PENDING; i++) {
            if (!pendActive[i]) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            dispatchNote(kind, freq, pan, gain, maxAge);
            return;
        }
        pendActive[slot] = true;
        pendKind[slot] = kind;
        pendDelay[slot] = delaySamples;
        pendFreq[slot] = freq;
        pendPan[slot] = pan;
        pendGain[slot] = gain;
        pendMaxAge[slot] = maxAge;
    }

    private void drainPendingNotes() {
        for (int i = 0; i < MAX_PENDING; i++) {
            if (!pendActive[i]) continue;
            if (--pendDelay[i] <= 0) {
                pendActive[i] = false;
                dispatchNote(pendKind[i], pendFreq[i], pendPan[i], pendGain[i], pendMaxAge[i]);
            }
        }
    }

    private void dispatchNote(int kind, double freq, double pan, double gain, double maxAge) {
        switch (kind) {
            case NOTE_HARP -> triggerSample(SampleBank.HARP, freq, pan, gain, 0.52, 0.007, maxAge, 0.25, BUS_EXPLORE, GRP_NONE);
            case NOTE_STRINGS -> {
                releaseGroup(GRP_LEAD, 0.09);
                triggerSample(SampleBank.VIOLIN_SUS, freq, pan, gain, 0.42, 0.030, maxAge, 0.22, BUS_COMBAT, GRP_LEAD);
            }
            case NOTE_BRASS -> {
                releaseGroup(GRP_HORN_MEL, 0.12);
                triggerSample(SampleBank.HORN_SUS, freq, pan, gain, 0.44, 0.055, maxAge, 0.28, BUS_COMBAT, GRP_HORN_MEL);
            }
            case NOTE_SPIC -> triggerSample(SampleBank.CELLO_SPIC, freq, pan, gain, 0.35, 0.002, maxAge, 0.12, BUS_COMBAT, GRP_NONE);
            case NOTE_TIMPANI -> triggerSample(SampleBank.TIMPANI, freq, pan, gain, 0.30, 0.001, maxAge, 0.20, BUS_ALL, GRP_NONE);
            case NOTE_WARDRUM -> triggerSample(SampleBank.WARDRUM, 0.0, pan, gain, 0.30, 0.001, maxAge, 0.20, BUS_COMBAT, GRP_NONE);
            case NOTE_SNARE_HEAVY -> triggerSample(SampleBank.SNARE_HEAVY, 0.0, pan, gain, 0.30, 0.001, maxAge, 0.08, BUS_ALL, GRP_NONE);
            case NOTE_SNARE_LIGHT -> triggerSample(SampleBank.SNARE_LIGHT, 0.0, pan, gain, 0.30, 0.001, maxAge, 0.08, BUS_ALL, GRP_NONE);
        }
    }

    private void triggerSample(int bankId, double targetHz, double pan, double gain, double send,
                               double attSec, double holdSec, double relSec, int bus, int group) {
        int slot = 0;
        double oldest = -1.0;
        for (int v = 0; v < MAX_VOICES; v++) {
            if (!vActive[v]) {
                slot = v;
                break;
            }
            if (vAge[v] > oldest) {
                oldest = vAge[v];
                slot = v;
            }
        }
        rngR = Dsp.xorshift(rngR);
        SampleBank.Bank b = SampleBank.BANKS[bankId];
        int idx = SampleBank.pick(bankId, targetHz, rngR);
        double root = b.rootHz[idx];
        double step;
        if (root > 0.0 && targetHz > 0.0) {
            step = Math.max(0.5, Math.min(2.0, targetHz / root));
        } else {
            step = 0.98 + 0.04 * (((rngR >>> 12) & 0xFF) / 255.0);
        }
        vActive[slot] = true;
        vPcm[slot] = b.pcm[idx];
        vBus[slot] = bus;
        vGroup[slot] = group;
        vPos[slot] = 0.0;
        vStep[slot] = step;
        vGain[slot] = gain;
        vPan[slot] = pan;
        vSend[slot] = send;
        vAge[slot] = 0.0;
        vAtt[slot] = Math.max(1e-4, attSec);
        vRelStart[slot] = holdSec;
        vRelLen[slot] = Math.max(1e-3, relSec);
    }

    private void releaseGroup(int group, double relSec) {
        for (int v = 0; v < MAX_VOICES; v++) {
            if (vActive[v] && vGroup[v] == group) {
                vRelStart[v] = Math.min(vRelStart[v], vAge[v]);
                vRelLen[v] = Math.max(1e-3, relSec);
            }
        }
    }

    private void clearMusicState() {
        hall.clear();
        compEnv = 0.0;
        compGain = 1.0;
        dcSendInL = 0.0; dcSendStL = 0.0; dcSendInR = 0.0; dcSendStR = 0.0;
        dcMixInL = 0.0; dcMixStL = 0.0; dcMixInR = 0.0; dcMixStR = 0.0;
        svfHbL[0] = 0.0; svfHbL[1] = 0.0;
        svfHbR[0] = 0.0; svfHbR[1] = 0.0;
        for (int v = 0; v < MAX_VOICES; v++) vActive[v] = false;
        for (int q = 0; q < MAX_PENDING; q++) pendActive[q] = false;
        prevBarIdx = -1;
        prevEighth = -1;
        prevCStep = -1;
        hbLubAge = 10.0;
        hbDubAge = 10.0;
    }

    private static double incPhase(double p, double freq) {
        double next = p + freq / SR;
        return next >= 1.0 ? next - (int) next : next;
    }

    private static double stepToward(double cur, double target, double maxDelta) {
        if (cur == target) return cur;
        return cur < target ? Math.min(target, cur + maxDelta) : Math.max(target, cur - maxDelta);
    }

    private static short toShort(double s) {
        return (short) Math.max(-32767, Math.min(32767, (int) Math.round(s * 32767.0)));
    }
}
