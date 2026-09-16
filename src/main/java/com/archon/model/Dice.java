package com.archon.model;

import java.util.Random;

/** Injected so tests are fully deterministic. */
public interface Dice {
    boolean chance(int percent);
    int between(int lo, int hi);

    final class Seeded implements Dice {
        private final Random r;

        public Seeded(long seed) {
            this.r = new Random(seed);
        }

        @Override
        public boolean chance(int percent) {
            validatePercent(percent);
            return r.nextInt(100) < percent;
        }

        @Override
        public int between(int lo, int hi) {
            validateRange(lo, hi);
            return lo + r.nextInt(hi - lo + 1);
        }
    }

    final class Always implements Dice {
        private final boolean hit;

        public Always(boolean hit) {
            this.hit = hit;
        }

        @Override
        public boolean chance(int percent) {
            validatePercent(percent);
            return hit;
        }

        @Override
        public int between(int lo, int hi) {
            validateRange(lo, hi);
            return hit ? hi : lo;
        }
    }

    static void validatePercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }
    }

    static void validateRange(int lo, int hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("lo must be less than or equal to hi");
        }
    }
}