package com.archon.model;

import java.util.Random;

/** Injected so tests are fully deterministic. */
public interface Dice {
    record Roll(boolean hit, int roll, int target) {}

    Roll check(int percent);

    default boolean chance(int percent) {
        return check(percent).hit();
    }

    int between(int lo, int hi);

    final class Seeded implements Dice {
        private final Random r;

        public Seeded(long seed) {
            this.r = new Random(seed);
        }

        @Override
        public Roll check(int percent) {
            validatePercent(percent);
            int roll = 1 + r.nextInt(100);
            return new Roll(roll <= percent, roll, percent);
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
        public Roll check(int percent) {
            validatePercent(percent);
            return new Roll(hit, hit ? Math.min(1, percent) : Math.max(100, percent + 1), percent);
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