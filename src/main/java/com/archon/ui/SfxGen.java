package com.archon.ui;

/** Procedural mono 44.1 kHz SFX generators (zero libGDX dependencies). */
public final class SfxGen {
    public static final int KEY = 0, SUBMIT = 1, REJECT = 2, HIT = 3, SHOOT = 4, SPLASH = 5,
            ZAP = 6, BOOM = 7, CRIT = 8, FIRE = 9, SIZZLE = 10, SWING = 11, STEP = 12,
            PSIONIC = 13, SHATTER = 14, HEAL = 15,
            HIT_PROP = 16, STING_KILL = 17, STING_DEATH = 18, STING_BLOOD = 19, SIPHON = 20,
            BLOOD_BOLT = 21, COUNT = 22;
    public static final int VARIANTS = 4;

    private static final int SR = Dsp.SAMPLE_RATE;

    private SfxGen() {}

    public static int variantsFor(int kind) {
        return switch (kind) {
            case SUBMIT, REJECT, PSIONIC, HEAL, STING_KILL, STING_DEATH, STING_BLOOD -> 1;
            case CRIT -> 3;
            default -> VARIANTS;
        };
    }

    public static byte[] buildWav(int kind) {
        return buildWav(kind, 0);
    }

    /** Generates a 44-byte RIFF/WAVE header + 16-bit mono 44.1 kHz PCM waveform with FDN8 reverb. */
    public static byte[] buildWav(int kind, int variant) {
        int rng = 0x9E3779B9 ^ (kind * 0x45D9F3B) ^ (variant * 0x27D4EB2D);
        double varPitch = 1.0 + (variant - 1.5) * 0.042;
        double vNorm = variant / 3.0;

        float dur = switch (kind) {
            case KEY -> 0.036f;
            case STEP -> 0.300f;
            case SWING -> 0.340f;
            case SIPHON -> 0.360f;
            case HIT_PROP -> 0.400f;
            case REJECT -> 0.420f;
            case SHOOT -> 0.440f;
            case HIT -> 0.520f;
            case SUBMIT -> 0.560f;
            case SPLASH, SIZZLE -> 0.680f;
            case STING_KILL -> 0.760f;
            case ZAP, SHATTER -> 0.780f;
            case FIRE -> 0.860f;
            case BLOOD_BOLT -> 0.740f;
            case PSIONIC, STING_BLOOD -> 0.950f;
            case CRIT -> 0.960f;
            case HEAL -> 1.020f;
            case BOOM -> 1.220f;
            case STING_DEATH -> 1.650f;
            default -> throw new IllegalArgumentException("Unknown sound kind: " + kind);
        };
        int samples = (int) (SR * dur);
        double[] dry = new double[samples];
        double[] send = kind == KEY ? null : new double[samples];
        double revRt60 = 0.9;
        double revBright = 0.45;

        switch (kind) {
            case KEY -> { genKey(dry, rng, varPitch); return encodeMono(dry, null, 0.0, 0.0); }
            case SUBMIT -> genSubmit(dry, send);
            case REJECT -> { genReject(dry, send, rng); revRt60 = 0.45; revBright = 0.40; }
            case HIT -> { genHit(dry, send, rng, varPitch, vNorm); revRt60 = 0.45; revBright = 0.40; }
            case HIT_PROP -> { genHitProp(dry, send, rng, varPitch, vNorm); revRt60 = 0.45; revBright = 0.40; }
            case SHOOT -> { genShoot(dry, send, variant, rng); revRt60 = 0.45; revBright = 0.40; }
            case SPLASH -> genSplash(dry, send, rng, varPitch, vNorm);
            case ZAP -> genZap(dry, send, rng, varPitch, vNorm);
            case BOOM -> genBoom(dry, send, rng, vNorm);
            case CRIT -> genCrit(dry, send, variant, rng, varPitch);
            case FIRE -> genFire(dry, send, variant, rng, vNorm);
            case SIZZLE -> genSizzle(dry, send, variant, rng, vNorm);
            case SWING -> { genSwing(dry, send, rng, varPitch, vNorm); revRt60 = 0.45; revBright = 0.40; }
            case STEP -> { genStep(dry, send, rng, varPitch); revRt60 = 0.45; revBright = 0.40; }
            case PSIONIC -> genPsionic(dry, send);
            case SHATTER -> genShatter(dry, send, rng, varPitch);
            case HEAL -> genHeal(dry, send, rng);
            case STING_KILL -> genStingKill(dry, send);
            case STING_DEATH -> genStingDeath(dry, send, rng);
            case STING_BLOOD -> { genStingBlood(dry, send, rng); revRt60 = 0.85; revBright = 0.38; }
            case SIPHON -> { genSiphon(dry, send, rng, varPitch, vNorm); revRt60 = 0.35; revBright = 0.35; }
            case BLOOD_BOLT -> { genBloodBolt(dry, send, rng, varPitch, vNorm); revRt60 = 1.0; revBright = 0.50; }
        }
        return encodeMono(dry, send, revRt60, revBright);
    }

    private static byte[] encodeMono(double[] dry, double[] send, double revRt60, double revBright) {
        int samples = dry.length;
        byte[] out = new byte[44 + samples * 2];
        Dsp.writeWavHeader(out, 1, samples);

        Dsp.Reverb rv = null;
        if (send != null) {
            rv = new Dsp.Reverb(0.012, revRt60);
            rv.bright = revBright;
        }

        double fadeDenom = SR * 0.012;
        for (int i = 0; i < samples; i++) {
            double fadeOut = Math.min(1.0, (samples - 1 - i) / fadeDenom);
            double wet = 0.0;
            if (rv != null) {
                rv.step(send[i], send[i]);
                wet = (rv.outL + rv.outR) * 0.5;
            }
            double s = (dry[i] * 0.88 + wet * 0.38) * fadeOut;
            int pcm = Math.max(-32767, Math.min(32767, (int) Math.round(s * 32767.0)));
            Dsp.putShortLE(out, 44 + i * 2, pcm);
        }
        return out;
    }

    private static void genKey(double[] dry, int rng, double varPitch) {
        double[] mFreq = {185 * varPitch, 420 * varPitch, 1340 * varPitch, 2850 * varPitch};
        double[] mTau = {0.008, 0.005, 0.0028, 0.0016};
        double[] mAmp = {0.48, 0.32, 0.22, 0.16};
        double[] mOn = {0.0, 0.0, 0.001, 0.0};
        double[] svf = new double[2];
        double g = Dsp.svfG(3400.0), k = 1.0 / 1.2;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double click = Dsp.svfFixed(Dsp.noise(rng) * Math.exp(-t / 0.0011), g, k, svf, 0, 1);
            double modes = Dsp.modal(t, mFreq, mTau, mAmp, mOn, 4);
            dry[i] = (click * 0.48 + modes * 0.72) * Math.min(1.0, t / 0.0003) * Math.exp(-t / 0.016);
        }
    }

    private static void genSubmit(double[] dry, double[] send) {
        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            double n1 = Dsp.crystalChime(t, 0.000, 523.25, 0.14);
            double n2 = Dsp.crystalChime(t, 0.048, 783.99, 0.16);
            double n3 = Dsp.crystalChime(t, 0.096, 1046.50, 0.21);
            double d = (n1 * 0.38 + n2 * 0.42 + n3 * 0.52) * 0.65;
            dry[i] = d;
            send[i] = d * 0.45;
        }
    }

    private static void genReject(double[] dry, double[] send, int rng) {
        double[] mFreq = {118, 179, 294, 92, 141, 232};
        double[] mTau = {0.032, 0.022, 0.014, 0.042, 0.026, 0.016};
        double[] mAmp = {0.52, 0.34, 0.18, 0.58, 0.36, 0.20};
        double[] mOn = {0.0, 0.0, 0.0, 0.078, 0.078, 0.078};
        double[] svf = new double[2];
        double g = Dsp.svfG(360.0), k = 1.0 / 1.1;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double lt = t < 0.078 ? t : t - 0.078;
            double knock = Dsp.svfFixed(Dsp.noise(rng) * Math.exp(-lt / 0.0045), g, k, svf, 0, 0);
            double modes = Dsp.modal(t, mFreq, mTau, mAmp, mOn, 6);
            double d = Dsp.softClip((modes * 0.75 + knock * 0.48) * 1.15) * 0.76;
            dry[i] = d;
            send[i] = d * 0.24;
        }
    }

    private static void genHit(double[] dry, double[] send, int rng, double varPitch, double vNorm) {
        double hiBoost = 0.85 + 0.35 * vNorm;
        double decayScale = 0.80 + 0.45 * (1.0 - vNorm);
        double[] mFreq = {
                112 * varPitch, 178 * varPitch, 264 * varPitch,
                485 * varPitch * (0.94 + 0.12 * vNorm), 790 * varPitch,
                1290 / varPitch, 2140 * varPitch * hiBoost
        };
        double[] mTau = {
                0.055 * decayScale, 0.045 * decayScale, 0.032 * decayScale,
                0.024, 0.018, 0.012, 0.008 * (0.8 + 0.4 * vNorm)
        };
        double[] mAmp = {0.50, 0.38, 0.28, 0.20, 0.16 * hiBoost, 0.12 * hiBoost, 0.09 * hiBoost};
        double[] mOn = {0.0, 0.0, 0.0, 0.001, 0.001, 0.002, 0.0};
        double[] svf = new double[4];
        double gSnap = Dsp.svfG((1800.0 + 800.0 * vNorm) * varPitch);
        double kSnap = 1.0 / (1.0 + 0.35 * vNorm);
        double subBase = 40.0 + 18.0 * (1.0 - vNorm);
        double subDrop = 125.0 + 30.0 * vNorm;
        double subDropTau = 0.016 + 0.005 * (1.0 - vNorm);
        double subDecay = 0.044 + 0.018 * (1.0 - vNorm);
        double snapTau = 0.004 + 0.002 * vNorm;
        double phase = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            phase += 2.0 * Math.PI * (subBase + subDrop * Math.exp(-t / subDropTau)) / SR;
            double subPunch = Math.sin(phase) * Math.exp(-t / subDecay);
            double snap = Dsp.svfFixed(noise * Math.exp(-t / snapTau), gSnap, kSnap, svf, 0, 1);
            double bodyNoise = Dsp.svf(noise * Math.exp(-t / 0.028),
                    (580.0 + 160.0 * vNorm) * Math.exp(-t / 0.04) + 150.0, 0.85, svf, 2, 0);
            double modes = Dsp.modal(t, mFreq, mTau, mAmp, mOn, 7);
            double low = Dsp.softClip((subPunch * (0.86 - 0.14 * vNorm) + bodyNoise * 0.45) * 1.35);
            double high = (snap * (0.50 + 0.18 * vNorm) + modes * (0.58 + 0.12 * vNorm)) * Math.min(1.0, t / 0.0006);
            double d = (low * 0.62 + high * 0.68) * 0.82;
            dry[i] = d;
            send[i] = d * (0.26 + 0.08 * vNorm);
        }
    }

    private static void genHitProp(double[] dry, double[] send, int rng, double varPitch, double vNorm) {
        double woodScale = 0.80 + 0.45 * vNorm;
        double[] mFreq = {
                138 * varPitch, 226 * varPitch, 385 * varPitch,
                670 * varPitch, 1140 / varPitch, 1760 * varPitch
        };
        double[] mTau = {0.030 * woodScale, 0.022 * woodScale, 0.016 * woodScale, 0.012, 0.009, 0.006};
        double[] mAmp = {0.54, 0.40, 0.28, 0.20, 0.14, 0.09};
        double[] mOn = {0.0, 0.0, 0.0, 0.001, 0.001, 0.002};
        double[] svf = new double[4];
        double gKnock = Dsp.svfG((950.0 + 500.0 * vNorm) * varPitch), kKnock = 1.0 / 1.35;
        double gBody = Dsp.svfG(290.0 * varPitch), kBody = 1.0 / 0.9;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            double knockNoise = Dsp.svfFixed(noise * Math.exp(-t / 0.007), gKnock, kKnock, svf, 0, 1);
            double bodyThud = Dsp.svfFixed(noise * Math.exp(-t / 0.018), gBody, kBody, svf, 2, 0);
            double modes = Dsp.modal(t, mFreq, mTau, mAmp, mOn, 6);
            double d = Dsp.softClip((modes * 0.78 + knockNoise * 0.56 + bodyThud * 0.36) * 1.15)
                    * Math.min(1.0, t / 0.0005) * 0.80;
            dry[i] = d;
            send[i] = d * 0.22;
        }
    }

    private static void genShoot(double[] dry, double[] send, int variant, int rng) {
        int ksLen = 178 + variant * 14;
        double[] ks = new double[ksLen];
        double mean = 0.0, prevN = 0.0;
        for (int j = 0; j < ksLen; j++) {
            rng = Dsp.xorshift(rng);
            double n = Dsp.noise(rng);
            double window = 1.0 - Math.abs(2.0 * j / ksLen - 1.0);
            ks[j] = (0.65 * n + 0.35 * prevN) * (0.4 + 0.6 * window);
            prevN = n;
            mean += ks[j];
        }
        mean /= ksLen;
        for (int j = 0; j < ksLen; j++) ks[j] -= mean;

        double[] svf = new double[6];
        double gTwang = Dsp.svfG(2800.0), kTwang = 1.0 / 1.3;
        double ksAllpass = 0.0;
        int ksIdx = 0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            int prevIdx = ksIdx == 0 ? ksLen - 1 : ksIdx - 1;
            double avg = 0.5 * (ks[ksIdx] + ks[prevIdx]) * 0.993;
            // Intentional post-filter dispersion coloration (preserve SHOOT character)
            double stiff = 0.35 * (avg - ksAllpass) + ks[prevIdx];
            ksAllpass = stiff;
            ks[ksIdx] = avg;
            if (++ksIdx == ksLen) ksIdx = 0;

            double bowTail = Math.exp(-Math.max(0.0, t - 0.16) / 0.045);
            double bowBody = Dsp.svf(stiff, 1450.0 * Math.exp(-t / 0.07) + 240.0, 0.9, svf, 0, 0) * bowTail;
            double twangSnap = Dsp.svfFixed(noise * Math.exp(-t / 0.0035), gTwang, kTwang, svf, 4, 1);
            double fletchAir = Dsp.svf(noise, 3200.0 * Math.exp(-t / 0.065) + 550.0, 1.6, svf, 2, 1)
                    * Math.min(1.0, t / 0.005) * Math.exp(-t / 0.055);
            double d = (Dsp.softClip(bowBody * 1.25) * 0.65 + twangSnap * 0.42 + fletchAir * 0.78) * 0.80;
            dry[i] = d;
            send[i] = d * 0.22;
        }
    }

    private static void genSplash(double[] dry, double[] send, int rng, double varPitch, double vNorm) {
        int[] b1At = new int[4], b2At = new int[4];
        for (int b = 0; b < 4; b++) {
            rng = Dsp.xorshift(rng);
            b1At[b] = 350 + b * 2200 + ((rng >>> 1) % 2100);
            rng = Dsp.xorshift(rng);
            b2At[b] = 350 + b * 2200 + ((rng >>> 1) % 2100);
        }
        int b1Cur = 0, b2Cur = 0;

        double[] svf = new double[4];
        double gSlap = Dsp.svfG(950.0 * varPitch), kSlap = 1.0 / 0.9;
        double plungeBase = 58.0 + 24.0 * vNorm;
        double sprayTop = 1900.0 + 600.0 * vNorm;
        double phase = 0.0, turb = 0.0, turb2 = 0.0;
        double b1Phase = 0.0, b1Freq = 520.0, b1Env = 0.0;
        double b2Phase = 0.0, b2Freq = 840.0, b2Env = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            turb += 0.0032 * (noise - turb);
            turb2 += 0.0007 * (noise - turb2);
            double turbMod = Math.max(0.20, Math.min(1.85, 0.92 + turb * 4.8 + turb2 * 2.5));

            phase += 2.0 * Math.PI * (plungeBase + 95.0 * Math.exp(-t / 0.022)) / SR;
            double plunge = Math.sin(phase) * Math.exp(-t / 0.045) * 0.52;
            double slap = Dsp.svfFixed(noise * Math.exp(-t / 0.018), gSlap, kSlap, svf, 0, 1);
            double spray = Dsp.svf(noise, (sprayTop - 1300.0 * Math.min(1.0, t / 0.32)) * turbMod, 1.4, svf, 2, 1)
                    * Math.min(1.0, t / 0.008) * Math.exp(-t / 0.13);

            boolean trig1 = b1Cur < 4 && i == b1At[b1Cur];
            if (trig1) b1Cur++;
            if (trig1 || (b1Env < 0.015 && (rng & 0x1FF) == 0 && t < 0.36)) {
                b1Freq = (380.0 + ((rng >>> 8) & 0x3FF) * 0.75) * varPitch;
                b1Env = 0.95;
            }
            boolean trig2 = b2Cur < 4 && i == b2At[b2Cur];
            if (trig2) b2Cur++;
            if (trig2 || (b2Env < 0.015 && (rng & 0x1FF) == 1 && t < 0.38)) {
                b2Freq = (540.0 + ((rng >>> 12) & 0x3FF) * 0.95) * varPitch;
                b2Env = 0.88;
            }
            b1Freq *= 1.0 + 18.5 / SR;
            b2Freq *= 1.0 + 21.0 / SR;
            b1Phase += 2.0 * Math.PI * b1Freq / SR;
            b2Phase += 2.0 * Math.PI * b2Freq / SR;
            b1Env *= 0.9981;
            b2Env *= 0.9983;
            double bubbles = Math.sin(b1Phase) * b1Env * 0.28 + Math.sin(b2Phase) * b2Env * 0.24;
            double d = (plunge + slap * 0.42 + spray * 0.72 + bubbles) * 0.76;
            dry[i] = d;
            send[i] = d * 0.38;
        }
    }

    private static void genZap(double[] dry, double[] send, int rng, double varPitch, double vNorm) {
        double[] svf = new double[8];
        double gAir = Dsp.svfG(4600.0 + 1200.0 * vNorm), kAir = 1.0 / 0.95;
        double phase = 0.0, phase2 = 0.0, phase3 = 0.0, subPhase = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            if (t < 0.014) {
                double u = t / 0.014;
                double pullEnv = Math.pow(u, 1.5);
                phase += 2.0 * Math.PI * (85.0 + 125.0 * u) / SR;
                double pullAir = Dsp.svf(noise, 1600.0 + 4600.0 * u, 1.6, svf, 0, 1);
                dry[i] = (Math.sin(phase) * 0.34 + pullAir * 0.38) * pullEnv * 0.74;
                send[i] = pullAir * pullEnv * 0.22;
            } else {
                double rt = t - 0.014;
                double punchFreq = (44.0 + 6.0 * vNorm) + (111.0 + 19.0 * vNorm) * Math.exp(-rt / 0.032);
                phase += 2.0 * Math.PI * punchFreq / SR;
                double subWave = Math.sin(phase) * Math.exp(-rt / (0.21 + 0.03 * (1.0 - vNorm)));
                double harm2 = Math.sin(phase * 2.0) * 0.38 * Math.exp(-rt / 0.13);
                double harm3 = Math.sin(phase * 3.0) * 0.14 * Math.exp(-rt / 0.065);
                double lowRumble = Dsp.svf(noise, 380.0 * Math.exp(-rt / 0.08) + 95.0, 0.95, svf, 2, 0)
                        * Math.exp(-rt / 0.16) * 0.55;
                double lowBus = Dsp.softClip((subWave + harm2 + harm3 + lowRumble) * 1.45);

                double pressureWhip = Dsp.svf(noise * Math.exp(-rt / 0.028),
                        (2600.0 + 500.0 * vNorm) * Math.exp(-rt / 0.04) + 720.0, 1.5, svf, 4, 1);
                double airHigh = Dsp.svfFixed(noise * (Math.exp(-rt / 0.007) * 0.85 + Math.exp(-rt / 0.09) * 0.22),
                        gAir, kAir, svf, 6, 2);

                double shimmerFreq = (880.0 + 90.0 * vNorm) * (0.5 + 0.5 * Math.exp(-rt / 0.075)) * varPitch;
                phase2 += 2.0 * Math.PI * shimmerFreq / SR;
                phase3 += 2.0 * Math.PI * (shimmerFreq * 1.503) / SR;
                subPhase += 2.0 * Math.PI * (shimmerFreq * 1.004) / SR;
                double psionicSheen = (0.42 * Math.sin(phase2) + 0.36 * Math.sin(subPhase)
                        + 0.28 * Math.sin(phase3)) * Math.exp(-rt / 0.11);

                double highBus = airHigh * 0.52 + pressureWhip * 0.58 + psionicSheen * 0.44;
                double att = Math.min(1.0, rt / 0.0012);
                dry[i] = (lowBus * 0.72 + highBus * 0.62) * att * 0.90;
                send[i] = highBus * 0.62 + lowBus * 0.16;
            }
        }
    }

    private static void genBoom(double[] dry, double[] send, int rng, double vNorm) {
        double[] svf = new double[6];
        double gSnap = Dsp.svfG(1400.0), kSnap = 1.0 / 0.9;
        double subBase = 22.0 + 8.0 * vNorm;
        double roarTop = 1100.0 + 500.0 * vNorm;
        double debrisStart = 0.025 + 0.020 * vNorm;
        double phase = 0.0, turb = 0.0, turb2 = 0.0;
        double crackleEnv = 0.0, crackleAmp = 0.0, crackleFreq = 1800.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            turb += 0.0032 * (noise - turb);
            turb2 += 0.0007 * (noise - turb2);
            double turbMod = Math.max(0.20, Math.min(1.85, 0.92 + turb * 4.8 + turb2 * 2.5));

            double shockSnap = Dsp.svfFixed(noise * Math.exp(-t / 0.006), gSnap, kSnap, svf, 4, 1);
            phase += 2.0 * Math.PI * (subBase + 86.0 * Math.exp(-t / 0.055)) / SR;
            double subWave = Dsp.softClip(Math.sin(phase) * 1.45) * Math.exp(-t / 0.24);

            double blastEnv = Math.min(1.0, t / 0.008) * Math.exp(-t / 0.25);
            double roarCutoff = (roarTop * Math.exp(-t / 0.18) + 85.0) * (0.80 + 0.35 * turbMod);
            double roar = Dsp.softClip(Dsp.svf(noise, roarCutoff, 0.85, svf, 0, 0) * blastEnv * 1.40);

            if (t > debrisStart && t < 0.58 && (rng & 0x7FF) < 4) {
                double r = ((rng >>> 12) & 0xFF) / 255.0;
                crackleAmp = 0.30 + 0.70 * r * r;
                crackleEnv = 1.0;
                crackleFreq = 900.0 + 1800.0 * r;
            }
            crackleEnv *= 0.992;
            double debris = Dsp.svf(noise * crackleEnv * crackleAmp, crackleFreq, 1.5, svf, 2, 1);

            double d = (subWave * 0.62 + roar * 0.64 + shockSnap * 0.55 + debris * 0.48) * 0.82;
            dry[i] = d;
            send[i] = d * 0.55;
        }
    }

    private static void genCrit(double[] dry, double[] send, int variant, int rng, double varPitch) {
        double tierSpread = 1.0 + variant * 0.45;
        double tierSustain = 0.85 + variant * 0.35;
        double[] mFreq = {
                145 * varPitch, 238 * varPitch,
                582 * varPitch, (582 + 9 * tierSpread) * varPitch,
                1085 * varPitch, (1085 + 13 * tierSpread) * varPitch,
                1740 * varPitch, 2480 / varPitch,
                3390 * varPitch, 4620
        };
        double[] mTau = {
                0.09 * tierSustain, 0.08 * tierSustain, 0.22 * tierSustain, 0.20 * tierSustain,
                0.18 * tierSustain, 0.16 * tierSustain, 0.13 * tierSustain, 0.095, 0.065, 0.040
        };
        double[] mAmp = {0.42, 0.32, 0.26, 0.24, 0.22, 0.20, 0.17, 0.14, 0.11, 0.08};
        double[] mOn = {0.0, 0.0, 0.002, 0.002, 0.003, 0.003, 0.004, 0.004, 0.005, 0.005};
        double[] svf = new double[2];
        double gSlash = Dsp.svfG((2700.0 + 350.0 * variant) * varPitch), kSlash = 1.0 / 1.8;
        double phase = 0.0, phase2 = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            double subFreq = (38.0 - 5.0 * variant) + (120.0 + 25.0 * variant) * Math.exp(-t / (0.024 + 0.006 * variant));
            phase += 2.0 * Math.PI * subFreq / SR;
            double subImpact = Dsp.softClip(Math.sin(phase) * (1.25 + 0.2 * variant)) * Math.exp(-t / (0.065 + 0.030 * variant));
            double metalSlash = Dsp.svfFixed(noise * Math.exp(-t / (0.010 + 0.003 * variant)), gSlash, kSlash, svf, 0, 1);
            double modes = Dsp.modal(t, mFreq, mTau, mAmp, mOn, 10);
            double tierBell = variant >= 1 ? Dsp.crystalChime(t, 0.012, variant == 1 ? 587.33 : 440.0, 0.28) * (0.18 + 0.08 * variant) : 0.0;
            double bassDrop = 0.0;
            if (variant == 2) {
                phase2 += 2.0 * Math.PI * (73.42 * Math.exp(-t / 0.35) + 36.71) / SR;
                bassDrop = Math.sin(phase2) * Math.exp(-t / 0.26) * 0.38;
            }
            dry[i] = (subImpact * (0.50 + 0.16 * variant) + bassDrop + metalSlash * 0.58 + modes * (0.68 + 0.08 * variant) + tierBell) * 0.80;
            send[i] = (metalSlash * 0.4 + modes * 0.85 + tierBell * 1.1) * (0.48 + 0.06 * variant);
        }
    }

    private static void genFire(double[] dry, double[] send, int variant, int rng, double vNorm) {
        double[] svf = new double[8];
        double phase = 0.0, turb = 0.0, turb2 = 0.0;
        double crackleEnv = 0.0, crackleAmp = 0.0, crackleFreq = 1800.0, cracklePhase = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            turb += 0.0032 * (noise - turb);
            turb2 += 0.0007 * (noise - turb2);
            double turbMod = Math.max(0.20, Math.min(1.85, 0.92 + turb * 4.8 + turb2 * 2.5));

            phase += 2.0 * Math.PI * ((46.0 + 14.0 * vNorm) + 75.0 * Math.exp(-t / 0.032)) / SR;
            double ignitePouf = Math.sin(phase) * Math.exp(-t / 0.065) * 0.55;

            double env = Math.min(1.0, t / 0.028) * Math.exp(-Math.max(0.0, t - 0.035) / 0.18);
            double flutter = 0.65 + 0.70 * Math.max(0.0, 0.5 + turb * 4.2);
            double lowDraft = Dsp.svf(noise, (165.0 + 55.0 * vNorm) * (0.75 + 0.45 * turbMod), 0.8, svf, 0, 0) * env * flutter;
            double midFlame = Dsp.svf(noise, (480.0 + 720.0 * Math.sin(Math.PI * Math.min(1.0, t / 0.38))) * turbMod,
                    1.10 + 0.35 * vNorm, svf, 2, 1) * env;
            double highLick = Dsp.svf(noise, (2500.0 + 600.0 * vNorm) * turbMod, 1.1, svf, 4, 2) * env * 0.32;

            if (t > 0.012 && t < 0.52 && crackleEnv < 0.08 && (rng & 0x3FF) < (3 + variant)) {
                double r = ((rng >>> 12) & 0xFF) / 255.0;
                crackleAmp = 0.28 + 0.72 * r * r;
                crackleEnv = 1.0;
                crackleFreq = 1100.0 + 2400.0 * r;
            }
            crackleEnv *= 0.993;
            crackleFreq *= 0.9985;
            cracklePhase += 2.0 * Math.PI * crackleFreq / SR;
            double emberPop = (Math.sin(cracklePhase) * 0.55 + Dsp.svf(noise, crackleFreq, 2.4, svf, 6, 1) * 0.65)
                    * crackleEnv * crackleAmp;

            double d = (ignitePouf + Dsp.softClip(lowDraft * 1.25) * 0.62 + midFlame * 0.68 + highLick + emberPop * 0.72) * 0.78;
            dry[i] = d;
            send[i] = d * 0.34;
        }
    }

    private static void genSizzle(double[] dry, double[] send, int variant, int rng, double vNorm) {
        double[] svf = new double[4];
        double gHiss = Dsp.svfG(6400.0), kHiss = 1.0 / 0.9;
        double sweepTop = 3400.0 + 1600.0 * vNorm;
        double bubbleScale = 0.88 + 0.24 * vNorm;
        int rateThresh = 4 + variant;
        double turb = 0.0, turb2 = 0.0;
        double b1Phase = 0.0, b1Freq = 520.0, b1Env = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            turb += 0.0032 * (noise - turb);
            turb2 += 0.0007 * (noise - turb2);
            double turbMod = Math.max(0.20, Math.min(1.85, 0.92 + turb * 4.8 + turb2 * 2.5));

            double env = Math.min(1.0, t / 0.010) * Math.exp(-t / 0.16);
            double steamSweep = (sweepTop - 2200.0 * Math.min(1.0, t / 0.38)) * (0.85 + 0.25 * turbMod);
            double steamJet = Dsp.svf(noise, steamSweep, 1.35, svf, 0, 1) * env;
            double hissAir = Dsp.svfFixed(noise, gHiss, kHiss, svf, 2, 2) * env * 0.38;

            if (t < 0.38 && b1Env < 0.05 && (rng & 0xFF) < rateThresh) {
                b1Freq = (1200.0 + ((rng >>> 8) & 0x7FF) * 1.4) * bubbleScale;
                b1Env = 0.45 + 0.55 * (((rng >>> 16) & 0xFF) / 255.0);
            }
            b1Freq *= 1.0 + 38.0 / SR;
            b1Phase += 2.0 * Math.PI * b1Freq / SR;
            b1Env *= 0.994;
            double acidFizz = Math.sin(b1Phase + 0.8 * Math.sin(b1Phase * 2.1)) * b1Env * 0.36 * Math.exp(-t / 0.20);

            double d = (steamJet * 0.65 + hissAir + acidFizz) * 0.76;
            dry[i] = d;
            send[i] = d * 0.28;
        }
    }

    private static void genSwing(double[] dry, double[] send, int rng, double varPitch, double vNorm) {
        double[] svf = new double[4];
        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            if (t > 0.165) continue;
            double noise = Dsp.noise(rng);
            double u = t / 0.165;
            double env = Math.pow(Math.sin(Math.PI * u), 1.45);
            double bodyCut = ((230.0 + 70.0 * vNorm) + (700.0 + 220.0 * vNorm) * Math.sin(Math.PI * u)) * varPitch;
            double edgeCut = ((980.0 + 320.0 * vNorm) + (2300.0 + 700.0 * vNorm) * Math.pow(Math.sin(Math.PI * u), 1.8)) * varPitch;
            double lowWhoosh = Dsp.svf(noise, bodyCut, 1.10 + 0.50 * vNorm, svf, 0, 1) * env;
            double bladeWhistle = Dsp.svf(noise, edgeCut, 2.60 + 1.20 * vNorm, svf, 2, 1) * env * 0.48;
            double d = (lowWhoosh * 0.78 + bladeWhistle) * 0.88;
            dry[i] = d;
            send[i] = d * 0.18;
        }
    }

    private static void genStep(double[] dry, double[] send, int rng, double varPitch) {
        double[] mFreq = {84 * varPitch, 142 * varPitch, 236, 390, 680 * varPitch};
        double[] mTau = {0.026, 0.020, 0.014, 0.009, 0.006};
        double[] mAmp = {0.50, 0.36, 0.22, 0.14, 0.08};
        double[] mOn = {0.0, 0.0, 0.0, 0.018, 0.018};
        double[] svf = new double[2];
        double g = Dsp.svfG(1350.0 * varPitch), k = 1.0 / 1.25;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double grit = Dsp.svfFixed(Dsp.noise(rng) * Math.exp(-t / 0.014), g, k, svf, 0, 1);
            double modes = Dsp.modal(t, mFreq, mTau, mAmp, mOn, 5);
            double d = (modes * 0.72 + grit * 0.38) * Math.min(1.0, t / 0.0012) * 0.76;
            dry[i] = d;
            send[i] = d * 0.26;
        }
    }

    private static void genPsionic(double[] dry, double[] send) {
        double[] svf = new double[4];
        double phase = 0.0, phase2 = 0.0, phase3 = 0.0, subPhase = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            double env = Math.min(1.0, t / 0.035) * Math.exp(-Math.max(0.0, t - 0.06) / 0.24);
            double vibrato = Math.sin(2.0 * Math.PI * (6.2 + 3.5 * t) * t);
            double baseFreq = 220.0 + 68.0 * Math.sin(Math.PI * Math.min(1.0, t / 0.48)) + 7.5 * vibrato;

            phase += 2.0 * Math.PI * baseFreq / SR;
            phase2 += 2.0 * Math.PI * (baseFreq * 1.007 + 1.8) / SR;
            phase3 += 2.0 * Math.PI * (baseFreq * 1.4142) / SR;
            subPhase += 2.0 * Math.PI * (78.0 - 34.0 * Math.min(1.0, t / 0.45)) / SR;

            double fmIndex = 1.65 * Math.exp(-t / 0.32) + 0.35;
            double modWave = fmIndex * Math.sin(phase3);
            double astralChoir = 0.38 * Math.sin(phase + modWave)
                    + 0.38 * Math.sin(phase2 - modWave * 0.85)
                    + 0.28 * Math.sin(phase * 2.0 + Math.sin(phase2));

            double f1 = 520.0 + 640.0 * Math.sin(Math.PI * Math.min(1.0, t / 0.42));
            double f2 = 1480.0 + 1150.0 * Math.cos(Math.PI * 0.85 * Math.min(1.0, t / 0.50));
            double formant1 = Dsp.svf(astralChoir, f1, 2.4, svf, 0, 1);
            double formant2 = Dsp.svf(astralChoir, f2, 3.0, svf, 2, 1);

            double halo = Math.sin(phase * 4.0 + 0.6 * Math.sin(phase3 * 2.0))
                    * Math.min(1.0, t / 0.08) * Math.exp(-t / 0.28) * 0.22;
            double subWarp = Math.sin(subPhase) * Math.exp(-t / 0.22) * 0.36;

            dry[i] = (subWarp + formant1 * 0.68 + formant2 * 0.52 + halo) * env * 0.82;
            send[i] = (formant1 * 0.5 + formant2 * 0.7 + halo * 1.4) * env * 0.68;
        }
    }

    private static void genShatter(double[] dry, double[] send, int rng, double varPitch) {
        double[] mFreq = {
                1820 * varPitch, 2690 / varPitch, 3840 * varPitch, 5120,
                2240 / varPitch, 3310 * varPitch, 4680 / varPitch, 6180,
                2950 * varPitch, 4190, 5540 * varPitch, 7120
        };
        double[] mTau = {0.065, 0.055, 0.042, 0.030, 0.058, 0.046, 0.035, 0.026, 0.050, 0.038, 0.028, 0.022};
        double[] mAmp = {0.32, 0.28, 0.22, 0.16, 0.26, 0.22, 0.18, 0.13, 0.20, 0.17, 0.14, 0.11};
        double[] mOn = {0.000, 0.001, 0.003, 0.006, 0.018, 0.027, 0.039, 0.052, 0.072, 0.095, 0.124, 0.158};
        double[] svf = new double[2];
        double g = Dsp.svfG(3600.0), k = 1.0 / 1.3;
        double phase = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            phase += 2.0 * Math.PI * (58.0 + 130.0 * Math.exp(-t / 0.012)) / SR;
            double impactCrack = Math.sin(phase) * Math.exp(-t / 0.020) * 0.42;
            double glassBurst = Dsp.svfFixed(Dsp.noise(rng) * Math.exp(-t / 0.045), g, k, svf, 0, 2);
            double shards = Dsp.modal(t, mFreq, mTau, mAmp, mOn, 12);
            dry[i] = (impactCrack + glassBurst * 0.48 + shards * 0.68) * 0.78 * Math.min(1.0, t / 0.0006);
            send[i] = (glassBurst * 0.3 + shards * 0.75) * 0.52;
        }
    }

    private static void genHeal(double[] dry, double[] send, int rng) {
        double[] svf = new double[2];
        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double c1 = Dsp.crystalChime(t, 0.000, 392.00, 0.26);
            double c2 = Dsp.crystalChime(t, 0.055, 493.88, 0.27);
            double c3 = Dsp.crystalChime(t, 0.110, 587.33, 0.29);
            double c4 = Dsp.crystalChime(t, 0.165, 739.99, 0.32);
            double c5 = Dsp.crystalChime(t, 0.220, 880.00, 0.38);

            double swellEnv = Math.min(1.0, t / 0.14) * Math.exp(-Math.max(0.0, t - 0.14) / 0.28);
            double pad = (Math.sin(2.0 * Math.PI * 196.0 * t) * 0.25
                    + Math.sin(2.0 * Math.PI * 293.66 * t + 0.3 * Math.sin(2.0 * Math.PI * 4.5 * t)) * 0.22)
                    * swellEnv;
            double sparkleMod = 0.55 + 0.45 * Math.sin(2.0 * Math.PI * 19.0 * t);
            double auraSparkle = Dsp.svf(Dsp.noise(rng), 5200.0 + 1800.0 * Math.sin(2.0 * Math.PI * 3.2 * t), 2.8, svf, 0, 1)
                    * swellEnv * sparkleMod * 0.18;

            double d = ((c1 * 0.32 + c2 * 0.34 + c3 * 0.36 + c4 * 0.40 + c5 * 0.44) + pad + auraSparkle) * 0.62;
            dry[i] = d;
            send[i] = d * 0.64;
        }
    }

    private static void genStingKill(double[] dry, double[] send) {
        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            double anchor = darkPluck(t, 146.83) * 0.34;
            double k1 = Dsp.crystalChime(t, 0.000, 587.33, 0.20);
            double k2 = Dsp.crystalChime(t, 0.045, 880.00, 0.24);
            double d = (anchor + k1 * 0.44 + k2 * 0.50) * 0.68;
            dry[i] = d;
            send[i] = d * 0.55;
        }
    }

    /** Local pluck anchor kept independent of MusicSynth so Phase 3/4 music rewrites cannot alter STING_KILL. */
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

    private static void genStingDeath(double[] dry, double[] send, int rng) {
        double[] svf = new double[6];
        double gGong = Dsp.svfG(240.0), kGong = 1.0 / 1.4;
        double gLip = Dsp.svfG(850.0), kLip = 1.0 / 1.4;
        double p1 = 0.0, p2 = 0.0, p3 = 0.0;
        double dcIn = 0.0, dcSt = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            double env = Math.min(1.0, t / 0.045) * Math.exp(-Math.max(0.0, t - 0.08) / 0.48);
            double scoop = 1.0 - 0.014 * Math.exp(-t / 0.018);
            double sag = 1.0 - 0.04 * Math.min(1.0, t / 1.1);
            double dt1 = (36.71 * sag * scoop) / SR;
            double dt2 = (73.42 * scoop) / SR;
            double dt3 = (103.83 * scoop) / SR;

            p1 += dt1; if (p1 >= 1.0) p1 -= 1.0;
            p2 += dt2; if (p2 >= 1.0) p2 -= 1.0;
            p3 += dt3; if (p3 >= 1.0) p3 -= 1.0;

            double saw = Dsp.polyBlepSaw(p1, dt1) * 0.52
                    + Dsp.polyBlepSaw(p2, dt2) * 0.38
                    + Dsp.polyBlepSaw(p3, dt3) * 0.24;
            double lip = Dsp.svfFixed(noise, gLip, kLip, svf, 4, 1) * Math.exp(-t / 0.012) * 0.32;
            double shaped = Dsp.softClip((saw + lip) * 1.35 + 0.15 * saw * saw);
            double dc = shaped - dcIn + 0.998 * dcSt;
            dcIn = shaped;
            dcSt = dc;

            double horn = Dsp.svf(dc, 460.0 * Math.exp(-t / 0.42) + 85.0, 1.15, svf, 0, 0);
            double gongAir = Dsp.svfFixed(noise, gGong, kGong, svf, 2, 1) * Math.exp(-t / 0.25) * 0.22;
            double d = Dsp.softClip((horn * 0.92 + gongAir) * env * 1.20) * 0.80;
            dry[i] = d;
            send[i] = d * 0.60;
        }
    }

    /**
     * Blood Surge (channel -b): Phase A tight double sacrifice heartbeat + vein draw,
     * opening into Phase B clean D-minor fifth power swell (D2+A2+D3) + crisp D4->A4 +AP accent.
     */
    private static void genStingBlood(double[] dry, double[] send, int rng) {
        double[] svf = new double[6];
        double gChest = Dsp.svfG(220.0), kChest = 1.0 / 0.9;
        double phaseLub = 0.0, phaseDub = 0.0;
        double pD2 = 0.0, pA2 = 0.0, pD3 = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);

            phaseLub += 2.0 * Math.PI * (32.0 + 28.0 * Math.exp(-t / 0.018)) / SR;
            double lub = Math.sin(phaseLub) * Math.min(1.0, t / 0.004) * Math.exp(-t / 0.042);

            double dub = 0.0;
            if (t >= 0.095) {
                double dt2 = t - 0.095;
                phaseDub += 2.0 * Math.PI * (28.0 + 26.0 * Math.exp(-dt2 / 0.018)) / SR;
                dub = Math.sin(phaseDub) * Math.min(1.0, dt2 / 0.004) * Math.exp(-dt2 / 0.048);
            }
            double chestEnv = Math.exp(-t / 0.014) + (t >= 0.095 ? 0.85 * Math.exp(-(t - 0.095) / 0.016) : 0.0);
            double chest = Dsp.svfFixed(noise * chestEnv, gChest, kChest, svf, 0, 0) * 0.48;
            double pulse = Dsp.softClip((lub * 0.66 + dub * 0.72 + chest) * 1.25) * 0.78;

            double drawEnv = t < 0.18 ? Math.sin(Math.PI * (t / 0.18)) : 0.0;
            double vein = Dsp.svf(noise, 1100.0 + 1700.0 * Math.min(1.0, t / 0.18), 1.8, svf, 2, 1) * drawEnv * 0.14;

            double swell = 0.0;
            if (t >= 0.09) {
                double st = t - 0.09;
                double surgeEnv = Math.min(1.0, st / 0.085) * Math.exp(-Math.max(0.0, st - 0.12) / 0.24);
                pD2 += 2.0 * Math.PI * 73.42 / SR;
                pA2 += 2.0 * Math.PI * 110.00 / SR;
                pD3 += 2.0 * Math.PI * 146.83 / SR;
                double rawD2 = Math.sin(pD2) + 0.30 * Math.sin(2.0 * pD2) + 0.12 * Math.sin(3.0 * pD2);
                double rawA2 = Math.sin(pA2) + 0.25 * Math.sin(2.0 * pA2) + 0.10 * Math.sin(3.0 * pA2);
                double rawD3 = Math.sin(pD3) + 0.24 * Math.sin(2.0 * pD3) + 0.08 * Math.sin(3.0 * pD3);
                double chord = Dsp.softClip((rawD2 * 0.44 + rawA2 * 0.34 + rawD3 * 0.34) * 1.1);
                double sweepCut = 200.0 + 1700.0 * Math.min(1.0, st / 0.32) * Math.exp(-Math.max(0.0, st - 0.32) / 0.38);
                swell = Dsp.svf(chord, sweepCut, 1.15, svf, 4, 0) * surgeEnv;
            }
            double chime = Dsp.crystalChime(t, 0.14, 293.66, 0.18) * 0.22
                    + Dsp.crystalChime(t, 0.21, 440.00, 0.22) * 0.26;

            dry[i] = (pulse + vein + swell * 0.72 + chime) * 0.82;
            send[i] = (vein * 0.6 + swell * 0.75 + chime * 1.25) * 0.65;
        }
    }

    private static void genSiphon(double[] dry, double[] send, int rng, double varPitch, double vNorm) {
        double[] svf = new double[2];
        double cutTime = 0.235;
        double phase = 0.0, phase2 = 0.0, turb = 0.0, turb2 = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            if (t >= 0.255) break;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            turb += 0.0032 * (noise - turb);
            turb2 += 0.0007 * (noise - turb2);
            double turbMod = Math.max(0.20, Math.min(1.85, 0.92 + turb * 4.8 + turb2 * 2.5));

            double env = t <= cutTime
                    ? Math.pow(t / cutTime, 1.7)
                    : Math.max(0.0, 1.0 - (t - cutTime) / 0.020);
            double u = Math.min(1.0, t / cutTime);
            double vacCut = 220.0 + (1530.0 + 400.0 * vNorm) * Math.pow(u, 1.4);
            double vacNoise = Dsp.svf(noise, vacCut * (0.9 + 0.15 * turbMod), 2.4, svf, 0, 1);
            double humFreq = (105.0 + 15.0 * vNorm) * varPitch + 225.0 * Math.pow(u, 1.5);
            phase += 2.0 * Math.PI * humFreq / SR;
            phase2 += 2.0 * Math.PI * (humFreq * 1.4142) / SR;
            double psionicHum = Math.sin(phase + 0.55 * Math.sin(phase2));
            double d = Dsp.softClip((vacNoise * 0.72 + psionicHum * 0.52) * 1.15) * env * 0.80;
            dry[i] = d;
            send[i] = d * 0.22;
        }
    }

    /** Instantaneous terrifying 3-spike blood volley with distinct per-spike sweeps + visceral rupture. */
    private static void genBloodBolt(double[] dry, double[] send, int rng, double varPitch, double vNorm) {
        final double[] baseOn = {0.000, 0.006, 0.013};
        final double[] airHiBase = {2400.0, 3800.0, 5400.0}, airHiSpan = {500.0, 700.0, 1000.0};
        final double[] airLo = {420.0, 680.0, 1150.0}, airSweepDur = {0.105, 0.080, 0.060};
        final double[] decayBase = {0.082, 0.062, 0.048}, decaySpan = {0.020, 0.016, 0.016};
        final double[] f0Base = {390.0, 680.0, 1100.0}, f0Span = {110.0, 180.0, 320.0};
        final double[] ratio2 = {1.414, 1.335, 1.068}, ratio3 = {1.059, 1.587, 1.442};
        final double[] bendAmt = {0.44, 0.38, 0.32}, shriekHi = {1550.0, 2150.0, 2750.0};

        final double[] onsets = new double[3], airHi = new double[3], decay = new double[3];
        final double[] f0 = new double[3], amp = new double[3], atk = new double[3];
        final double[] ph1 = new double[3], ph2 = new double[3], ph3 = new double[3];
        for (int s = 0; s < 3; s++) {
            rng = Dsp.xorshift(rng);
            double j = (((rng & 0xFF) / 255.0) - 0.5) * 0.008;
            onsets[s] = Math.max(0.0, Math.min(0.018, baseOn[s] + j));
            airHi[s] = airHiBase[s] + airHiSpan[s] * (((rng >>> 8) & 0xFF) / 255.0);
            decay[s] = decayBase[s] + decaySpan[s] * (((rng >>> 16) & 0xFF) / 255.0);
            rng = Dsp.xorshift(rng);
            f0[s] = (f0Base[s] + f0Span[s] * ((rng & 0xFF) / 255.0)) * varPitch;
            amp[s] = 0.78 + 0.34 * (((rng >>> 8) & 0xFF) / 255.0);
            atk[s] = 0.0015 + 0.0025 * (((rng >>> 16) & 0xFF) / 255.0);
        }

        double[] svf = new double[8];
        double[] spikeSvf = new double[12];
        double gSnap = Dsp.svfG(2400.0 * varPitch), kSnap = 1.0 / 1.4;
        double gSplat = Dsp.svfG(1950.0), kSplat = 1.0 / 1.5;
        double turb = 0.0, turb2 = 0.0;
        double splatEnv = 0.0, splatFreq = 1600.0, splatPh = 0.0;

        for (int i = 0; i < dry.length; i++) {
            double t = i / (double) SR;
            rng = Dsp.xorshift(rng);
            double noise = Dsp.noise(rng);
            turb += 0.0032 * (noise - turb);
            turb2 += 0.0007 * (noise - turb2);
            double turbMod = Math.max(0.20, Math.min(1.85, 0.92 + turb * 4.8 + turb2 * 2.5));

            // 1. Visceral arterial rupture + brittle clot snap (0..55 ms)
            double rupture = 0.0;
            if (t < 0.055) {
                double u = t / 0.055;
                double tearFreq = u < 0.35 ? 280.0 + (u / 0.35) * 870.0 : 1150.0 - ((u - 0.35) / 0.65) * 910.0;
                double tearEnv = Math.min(1.0, t / 0.0018) * Math.exp(-t / 0.019) * (1.0 - u * u * u);
                double tear = Dsp.svf(noise, tearFreq * varPitch, 1.9, svf, 0, 1) * tearEnv;
                double snap = Dsp.svfFixed(noise * Math.exp(-t / 0.004), gSnap, kSnap, svf, 2, 1);
                rupture = tear * 0.72 + snap * 0.56;
            }

            // 2. Simultaneous 3-spike volley (low fang, mid razor blade, piercing needle)
            double volley = 0.0;
            for (int s = 0; s < 3; s++) {
                double dt = t - onsets[s];
                if (dt < 0.0 || dt > 0.24) continue;
                double airU = Math.min(1.0, dt / airSweepDur[s]);
                double airCut = airHi[s] * (1.0 - airU) + airLo[s] * airU;
                double airEnv = Math.min(1.0, dt / atk[s]) * Math.exp(-dt / decay[s]);
                double airSlice = Dsp.svf(noise, airCut * varPitch, 4.0, spikeSvf, s * 4, 1) * airEnv;

                double bend = 1.0 - bendAmt[s] * Math.min(1.0, dt / (airSweepDur[s] + 0.03));
                double instF = f0[s] * bend;
                ph1[s] += 2.0 * Math.PI * instF / SR;
                ph2[s] += 2.0 * Math.PI * (instF * ratio2[s]) / SR;
                ph3[s] += 2.0 * Math.PI * (instF * ratio3[s]) / SR;
                double rawShriek = Dsp.softClip((Math.sin(ph1[s]) * 0.52 + Math.sin(ph2[s]) * 0.34 + Math.sin(ph3[s]) * 0.28) * 1.45);
                double shriekCut = shriekHi[s] * (1.0 - 0.52 * airU);
                double shriekEnv = Math.min(1.0, dt / (atk[s] + 0.0012)) * Math.exp(-dt / (decay[s] * 0.82));
                double shriek = Dsp.svf(rawShriek, Math.max(420.0, Math.min(shriekHi[s] * 1.15, shriekCut)), 1.7, spikeSvf, s * 4 + 2, 1)
                        * shriekEnv * 0.36;

                volley += (airSlice * 0.85 + shriek) * amp[s];
            }

            // 3. Wet gore splatter burst (single pass per sample)
            double splat = 0.0;
            if (t >= 0.012 && t <= 0.125) {
                if (splatEnv < 0.10 && (rng & 0x3F) < 6) {
                    splatFreq = 1200.0 + 1600.0 * (((rng >>> 8) & 0xFF) / 255.0);
                    splatEnv = 0.85;
                }
                splat = Dsp.svfFixed(noise * splatEnv, gSplat, kSplat, svf, 6, 1) * 0.32;
            }
            splatEnv *= 0.990;
            splatFreq *= 1.0 + 25.0 / SR;
            splatPh += 2.0 * Math.PI * splatFreq / SR;
            splat += Math.sin(splatPh) * splatEnv * 0.10;

            // 4. Ominous turbulent low dread rumble (0.005..0.46s, no clean sine laser drop)
            double dread = 0.0;
            if (t >= 0.005 && t < 0.46) {
                double rd = t - 0.005;
                double dreadEnv = Math.min(1.0, rd / 0.030) * Math.exp(-rd / 0.16);
                double dreadCut = (78.0 + 18.0 * vNorm) * (0.72 + 0.42 * turbMod);
                dread = Dsp.softClip(Dsp.svf(noise, Math.max(55.0, Math.min(115.0, dreadCut)), 0.9, svf, 4, 0) * 2.2) * dreadEnv * 0.54;
            }

            double d = Dsp.softClip((rupture + volley * 0.56 + splat + dread) * 1.15) * 0.86;
            dry[i] = d;
            send[i] = d * 0.58;
        }
    }
}
