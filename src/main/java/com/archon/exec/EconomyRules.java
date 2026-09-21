package com.archon.exec;

/** Immutable numerical economy rules, selected when a game is assembled. */
public record EconomyRules(
        int roundAp,
        int untaxedLines,
        int lineTax,
        int breakPenalty
) {
    public EconomyRules {
        if (roundAp <= 0) {
            throw new IllegalArgumentException("round AP must be positive");
        }
        if (untaxedLines < 0) {
            throw new IllegalArgumentException("untaxed lines must not be negative");
        }
        if (lineTax < 0) {
            throw new IllegalArgumentException("line tax must not be negative");
        }
        if (breakPenalty < 0) {
            throw new IllegalArgumentException("break penalty must not be negative");
        }
    }

    public static EconomyRules prototype() {
        return new EconomyRules(5, 1, 1, 1);
    }

    public int taxForNextLine(int linesUsed) {
        return linesUsed < untaxedLines ? 0 : lineTax;
    }

    public int interruptedCharge(int stageCost) {
        return stageCost / 2;
    }

    public int breakCharge(int allocation, int availableAp) {
        return (int) Math.min((long) allocation + breakPenalty, availableAp);
    }
}