package com.archon.exec;

public record EconomyRules(
        int roundAp,
        int untaxedLines,
        int lineTax,
        int breakPenalty
) {
    public EconomyRules {
        if (roundAp <= 0 || untaxedLines < 0 || lineTax < 0 || breakPenalty < 0) {
            throw new IllegalArgumentException(
                    "round AP must be positive; other economy values must not be negative");
        }
    }

    public static EconomyRules prototype() {
        return new EconomyRules(6, 1, 1, 1);
    }

    public int taxForNextLine(int linesUsed) {
        return nonNegative(linesUsed, "lines used") < untaxedLines ? 0 : lineTax;
    }

    public int interruptedCharge(int stageCost) {
        return nonNegative(stageCost, "stage cost") / 2;
    }

    public int breakCharge(int allocation, int availableAp) {
        return (int) Math.min((long) nonNegative(allocation, "allocation") + breakPenalty,
                nonNegative(availableAp, "available AP"));
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}