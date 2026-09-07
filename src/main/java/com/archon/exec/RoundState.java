package com.archon.exec;

public final class RoundState {
    public static final int BASE_AP       = 5;
    public static final int LINE_TAX      = 1;
    public static final int FREE_LINES    = 1;
    public static final int BREAK_PENALTY = 1;

    private int ap = BASE_AP;
    private int linesUsed = 0;
    private int roundNo = 1;

    public int ap()        { return ap; }
    public int linesUsed() { return linesUsed; }
    public int roundNo()   { return roundNo; }

    public int taxForNextLine() { return linesUsed < FREE_LINES ? 0 : LINE_TAX; }

    public void spend(int n)   { ap = Math.max(0, ap - n); }
    public void countLine()    { linesUsed++; }

    public int reset() {
        int wasted = ap;
        ap = BASE_AP;
        linesUsed = 0;
        roundNo++;
        return wasted;
    }

    /** Test hooks. */
    public void setAp(int v) { ap = v; }
    public void setLinesUsed(int v) { linesUsed = v; }
}