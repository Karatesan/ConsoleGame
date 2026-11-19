package com.archon.exec;

public final class RoundState {
    private final EconomyRules rules;
    private int ap;
    private int linesUsed = 0;
    private int roundNo = 1;

    public RoundState(EconomyRules rules) {
        this.rules = java.util.Objects.requireNonNull(rules, "rules");
        ap = rules.roundAp();
    }

    public EconomyRules rules() {
        return rules;
    }

    public int ap() {
        return ap;
    }

    public int linesUsed() {
        return linesUsed;
    }

    public int roundNo() {
        return roundNo;
    }

    public void spend(int amount) {
        if (amount < 0 || amount > ap) {
            throw new IllegalArgumentException("spending must be between 0 and " + ap + ": " + amount);
        }
        ap -= amount;
    }

    public void gain(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("gain must be non-negative: " + amount);
        }
        ap += amount;
    }

    public void countLine() {
        linesUsed = Math.incrementExact(linesUsed);
    }

    public void resetLinesUsed() {
        linesUsed = 0;
    }

    public int reset() {
        roundNo = Math.incrementExact(roundNo);
        int wasted = ap;
        ap = rules.roundAp();
        linesUsed = 0;
        return wasted;
    }
}