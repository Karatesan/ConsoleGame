package com.archon.model;

import java.util.Random;

/** Injected so tests are fully deterministic. */
public interface Dice {
    boolean chance(int percent);
    int between(int lo, int hi);

    final class Seeded implements Dice {
        private final Random r;
        public Seeded(long seed) { this.r = new Random(seed); }
        public boolean chance(int p) { return r.nextInt(100) < p; }
        public int between(int lo, int hi) { return lo + r.nextInt(Math.max(1, hi - lo + 1)); }
    }

    final class Always implements Dice {
        private final boolean hit;
        public Always(boolean hit) { this.hit = hit; }
        public boolean chance(int p) { return hit; }
        public int between(int lo, int hi) { return hit ? hi : lo; }
    }
}