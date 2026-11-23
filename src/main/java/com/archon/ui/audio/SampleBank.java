package com.archon.ui.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Flat zero-allocation-at-playback sample bank backed by CC0 VSCO 2 CE 44.1 kHz mono WAVs.
 */
final class
SampleBank {
    static final int TIMPANI = 0, WARDRUM = 1, SNARE_HEAVY = 2, SNARE_LIGHT = 3,
            HARP = 4, BASS_SUS = 5, CELLO_SUS = 6, CELLO_SPIC = 7,
            VIOLIN_SUS = 8, PIZZ = 9, HORN_SUS = 10, BANK_COUNT = 11;

    private static final String BASE = "/audio/vsco/";
    private static final String[] NAMES = {
            "timpani", "wardrum", "snare_heavy", "snare_light",
            "harp", "bass_sus", "cello_sus", "cello_spic",
            "violin_sus", "pizz", "horn_sus"
    };

    static final Bank[] BANKS = loadAll();

    static final class Bank {
        final short[][] pcm;
        final double[] rootHz;

        Bank(short[][] pcm, double[] rootHz) {
            this.pcm = pcm;
            this.rootHz = rootHz;
        }
    }

    private SampleBank() {}

    static int pick(int bankId, double targetHz, int rng) {
        Bank b = BANKS[bankId];
        int n = b.pcm.length;
        if (b.rootHz[0] <= 0.0 || targetHz <= 0.0) {
            return (rng >>> 4) % n;
        }
        double minDist = Double.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            double d = Math.abs(Math.log(targetHz / b.rootHz[k]));
            if (d < minDist) minDist = d;
        }
        int matches = 0;
        for (int k = 0; k < n; k++) {
            if (Math.abs(Math.abs(Math.log(targetHz / b.rootHz[k])) - minDist) < 1e-4) matches++;
        }
        int pickIdx = (rng >>> 4) % matches;
        for (int k = 0; k < n; k++) {
            if (Math.abs(Math.abs(Math.log(targetHz / b.rootHz[k])) - minDist) < 1e-4) {
                if (pickIdx-- == 0) return k;
            }
        }
        return 0;
    }

    private static Bank[] loadAll() {
        @SuppressWarnings("unchecked")
        ArrayList<short[]>[] pcms = new ArrayList[BANK_COUNT];
        @SuppressWarnings("unchecked")
        ArrayList<Double>[] roots = new ArrayList[BANK_COUNT];
        for (int i = 0; i < BANK_COUNT; i++) {
            pcms[i] = new ArrayList<>();
            roots[i] = new ArrayList<>();
        }

        try (InputStream in = SampleBank.class.getResourceAsStream(BASE + "samples.txt")) {
            if (in == null) throw new IllegalStateException("Missing resource: " + BASE + "samples.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                if (p.length != 3) throw new IllegalStateException("Bad samples.txt line: " + line);
                int id = bankId(p[0]);
                pcms[id].add(loadMono16(p[1]));
                roots[id].add(Double.parseDouble(p[2]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading sample manifest", e);
        }

        Bank[] out = new Bank[BANK_COUNT];
        for (int i = 0; i < BANK_COUNT; i++) {
            int n = pcms[i].size();
            if (n == 0) throw new IllegalStateException("Empty sample bank: " + NAMES[i]);
            double[] r = new double[n];
            for (int k = 0; k < n; k++) r[k] = roots[i].get(k);
            out[i] = new Bank(pcms[i].toArray(new short[n][]), r);
        }
        return out;
    }

    private static int bankId(String name) {
        for (int i = 0; i < BANK_COUNT; i++) {
            if (NAMES[i].equals(name)) return i;
        }
        throw new IllegalStateException("Unknown sample bank: " + name);
    }

    private static short[] loadMono16(String filename) throws IOException {
        byte[] raw;
        try (InputStream in = SampleBank.class.getResourceAsStream(BASE + filename)) {
            if (in == null) throw new IllegalStateException("Missing sample WAV: " + BASE + filename);
            raw = in.readAllBytes();
        }
        if (raw.length < 44 || raw[0] != 'R' || raw[1] != 'I' || raw[2] != 'F' || raw[3] != 'F'
                || raw[8] != 'W' || raw[9] != 'A' || raw[10] != 'V' || raw[11] != 'E') {
            throw new IllegalStateException("Invalid RIFF/WAVE header: " + filename);
        }
        int pos = 12, channels = 0, sampleRate = 0, bits = 0, format = 0;
        int dataOff = -1, dataLen = 0;
        while (pos + 8 <= raw.length) {
            int sz = u32LE(raw, pos + 4);
            if (raw[pos] == 'f' && raw[pos + 1] == 'm' && raw[pos + 2] == 't' && raw[pos + 3] == ' ') {
                format = u16LE(raw, pos + 8);
                channels = u16LE(raw, pos + 10);
                sampleRate = u32LE(raw, pos + 12);
                bits = u16LE(raw, pos + 22);
            } else if (raw[pos] == 'd' && raw[pos + 1] == 'a' && raw[pos + 2] == 't' && raw[pos + 3] == 'a') {
                dataOff = pos + 8;
                dataLen = sz;
                break;
            }
            pos += 8 + sz + (sz & 1);
        }
        if (dataOff < 0 || format != 1 || channels != 1 || sampleRate != Dsp.SAMPLE_RATE || bits != 16) {
            throw new IllegalStateException("Unsupported WAV format in " + filename);
        }
        int samples = Math.min(dataLen, raw.length - dataOff) / 2;
        short[] pcm = new short[samples];
        for (int i = 0; i < samples; i++) {
            int lo = raw[dataOff + i * 2] & 0xFF;
            int hi = raw[dataOff + i * 2 + 1];
            pcm[i] = (short) ((hi << 8) | lo);
        }
        return pcm;
    }

    private static int u16LE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static int u32LE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8)
                | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }
}
