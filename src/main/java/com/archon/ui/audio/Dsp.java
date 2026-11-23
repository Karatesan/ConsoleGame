package com.archon.ui.audio;

/** Pure DSP math primitives and PCM/WAV helpers (zero libGDX dependencies). */
public final class Dsp {
    public static final int SAMPLE_RATE = 44100;

    private Dsp() {}

    static int xorshift(int x) {
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        return x;
    }

    static double noise(int rng) {
        return ((rng & 0xFFFF) / 32768.0) - 1.0;
    }

    /** Padé tanh soft-clipper clamped to [-3, 3] for strict (-1, 1) headroom with punch near zero. */
    static double softClip(double x) {
        if (x >= 3.0) return 1.0;
        if (x <= -3.0) return -1.0;
        double x2 = x * x;
        return x * (27.0 + x2) / (27.0 + 9.0 * x2);
    }

    static double svfG(double cutoffHz) {
        double clamped = Math.max(40.0, Math.min(SAMPLE_RATE * 0.45, cutoffHz));
        return Math.tan(Math.PI * clamped / SAMPLE_RATE);
    }

    /** Zero-delay-feedback (Simper TPT) 2-pole SVF with precomputed g and k=1/q. mode: 0=LP, 1=BP, 2=HP. */
    static double svfFixed(double x, double g, double k, double[] st, int off, int mode) {
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

    /** Zero-delay-feedback (Simper TPT) 2-pole SVF. mode: 0=LP, 1=BP, 2=HP. */
    static double svf(double x, double cutoffHz, double q, double[] st, int off, int mode) {
        return svfFixed(x, svfG(cutoffHz), 1.0 / Math.max(0.5, q), st, off, mode);
    }

    /** Bandlimited PolyBLEP sawtooth for wrapped phase in [0, 1) and dt = freq / SAMPLE_RATE. */
    static double polyBlepSaw(double phase, double dt) {
        double val = 2.0 * phase - 1.0;
        if (dt <= 0.0 || dt >= 0.5) return val;
        if (phase < dt) {
            double t = phase / dt;
            val -= (t + t - t * t - 1.0);
        } else if (phase > 1.0 - dt) {
            double t = (phase - 1.0) / dt;
            val -= (t * t + t + t + 1.0);
        }
        return val;
    }

    /** 8-channel modulated Walsh-Hadamard FDN reverb with stereo pre-delay and input allpass diffusion. */
    static final class Reverb {
        static final int[] LEN = {1433, 1601, 1867, 2053, 2281, 2437, 2683, 2953};
        static final int[] AP_L = {142, 379}, AP_R = {151, 397};
        static final double AP_G = 0.6, MOD_DEPTH = 8.0;
        static final double[] LFO_HZ = {0.31, 0.43, 0.57, 0.71};

        final double[][] line = new double[8][];
        final int[] w = new int[8];
        final double[] lp = new double[8], gain = new double[8], r = new double[8], lfo = new double[4];
        final double[] preL, preR;
        int preIdx;
        final double[][] ap = new double[4][];
        final int[] apIdx = new int[4];
        double bright = 0.35, outL, outR;

        Reverb(double preDelaySec, double rt60) {
            for (int c = 0; c < 8; c++) line[c] = new double[LEN[c] + 1];
            int pre = Math.max(1, (int) (preDelaySec * SAMPLE_RATE));
            preL = new double[pre];
            preR = new double[pre];
            ap[0] = new double[AP_L[0]];
            ap[1] = new double[AP_L[1]];
            ap[2] = new double[AP_R[0]];
            ap[3] = new double[AP_R[1]];
            setRt60(rt60);
        }

        void setRt60(double sec) {
            for (int c = 0; c < 8; c++) gain[c] = Math.pow(10.0, -3.0 * LEN[c] / (sec * SAMPLE_RATE));
        }

        private double allpass(int a, double x) {
            double[] b = ap[a];
            int i = apIdx[a];
            double d = b[i], v = x - AP_G * d;
            b[i] = v;
            apIdx[a] = i + 1 == b.length ? 0 : i + 1;
            return d + AP_G * v;
        }

        void step(double inL, double inR) {
            double dL = preL[preIdx], dR = preR[preIdx];
            preL[preIdx] = inL;
            preR[preIdx] = inR;
            preIdx = preIdx + 1 == preL.length ? 0 : preIdx + 1;
            dL = allpass(1, allpass(0, dL));
            dR = allpass(3, allpass(2, dR));

            for (int c = 0; c < 8; c++) {
                double[] b = line[c];
                int n = b.length;
                double delay = LEN[c];
                if (c < 4) {
                    lfo[c] += LFO_HZ[c] / SAMPLE_RATE;
                    if (lfo[c] >= 1.0) lfo[c] -= 1.0;
                    delay -= MOD_DEPTH * (1.0 + Math.sin(2.0 * Math.PI * lfo[c]));
                }
                double rp = w[c] - delay;
                if (rp < 0.0) rp += n;
                int i0 = (int) rp, i1 = i0 + 1 == n ? 0 : i0 + 1;
                double v = (b[i0] + (b[i1] - b[i0]) * (rp - i0)) * gain[c];
                lp[c] += bright * (v - lp[c]);
                r[c] = lp[c];
            }
            outL = (r[0] - r[2] + r[4] - r[6]) * 0.35;
            outR = (r[1] - r[3] + r[5] - r[7]) * 0.35;

            for (int h = 1; h < 8; h <<= 1) {
                for (int i = 0; i < 8; i += h << 1) {
                    for (int j = i; j < i + h; j++) {
                        double a = r[j], bb = r[j + h];
                        r[j] = a + bb;
                        r[j + h] = a - bb;
                    }
                }
            }
            for (int c = 0; c < 8; c++) {
                double[] b = line[c];
                b[w[c]] = r[c] * 0.35355339059327373 + ((c & 1) == 0 ? dL : dR) + 1e-24;
                w[c] = w[c] + 1 == b.length ? 0 : w[c] + 1;
            }
        }

        void clear() {
            for (int c = 0; c < 8; c++) {
                java.util.Arrays.fill(line[c], 0.0);
                w[c] = 0;
                lp[c] = 0.0;
                r[c] = 0.0;
            }
            for (int c = 0; c < 4; c++) {
                lfo[c] = 0.0;
                java.util.Arrays.fill(ap[c], 0.0);
                apIdx[c] = 0;
            }
            java.util.Arrays.fill(preL, 0.0);
            java.util.Arrays.fill(preR, 0.0);
            preIdx = 0;
            outL = 0.0;
            outR = 0.0;
        }
    }

    static double modal(double t, double[] freq, double[] tau, double[] amp, double[] on, int count) {
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
    static double crystalChime(double t, double start, double freq, double tau) {
        double dt = t - start;
        if (dt < 0.0) return 0.0;
        double attack = Math.min(1.0, dt / 0.004);
        double env = attack * Math.exp(-dt / tau);
        double bellMod = 0.65 * Math.exp(-dt / 0.032) * Math.sin(2.0 * Math.PI * (freq * 2.756) * dt);
        double f1 = Math.sin(2.0 * Math.PI * freq * dt + bellMod);
        double f2 = Math.sin(2.0 * Math.PI * (freq * 1.0025 + 1.4) * dt - bellMod * 0.5);
        double overtone = 0.24 * Math.exp(-dt / (tau * 0.45)) * Math.sin(2.0 * Math.PI * (freq * 3.0) * dt);
        double mallet = dt < 0.015
                ? (Math.sin(2.0 * Math.PI * freq * 5.404 * dt) * 0.6
                        + Math.sin(2.0 * Math.PI * freq * 8.93 * dt) * 0.4)
                        * Math.exp(-dt / 0.0018) * 0.14
                : 0.0;
        return (0.52 * f1 + 0.48 * f2 + overtone) * env + mallet;
    }

    public static void writeWavHeader(byte[] out, int channels, int frames) {
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

    public static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
        b[off + 2] = (byte) ((v >>> 16) & 0xFF);
        b[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    public static void putShortLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
    }
}
