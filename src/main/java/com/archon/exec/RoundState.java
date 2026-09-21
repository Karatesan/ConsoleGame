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

    public EconomyRules rules() { return rules; }
    public int ap() {return ap;}
    public int linesUsed() {return linesUsed;}
    public int roundNo() {return roundNo;}

    public void spend(int n)   { ap = Math.max(0, ap - n); }
    public void countLine()    { linesUsed++; }

    public int reset() {
        int wasted = ap;
        ap = rules.roundAp();
        linesUsed = 0;
        roundNo++;
        return wasted;
    }

    /** Test hooks. */
    public void setAp(int v) { ap = v; }
    public void setLinesUsed(int v) { linesUsed = v; }
}