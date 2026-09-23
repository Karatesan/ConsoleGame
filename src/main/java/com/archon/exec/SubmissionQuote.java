package com.archon.exec;

public final class SubmissionQuote {
    private final boolean free;
    private final int allocation;
    private final int tax;
    private final int availableAp;
    private final EconomyRules rules;

    SubmissionQuote(boolean free, int allocation, RoundState round) {
        java.util.Objects.requireNonNull(round, "round");
        if (allocation < 0 || (free && allocation != 0)) {
            throw new IllegalArgumentException(
                    "allocation must be nonnegative and zero for free-only submissions");
        }

        this.free = free;
        this.allocation = allocation;
        this.rules = round.rules();
        this.tax = free ? 0 : rules.taxForNextLine(round.linesUsed());
        this.availableAp = round.ap();
    }

    public boolean free() {
        return free;
    }

    public int allocation() {
        return allocation;
    }

    public int tax() {
        return tax;
    }

    public int availableAp() {
        return availableAp;
    }

    public long requiredAp() {
        return (long) allocation + tax;
    }

    public boolean affordable() {
        return requiredAp() <= availableAp;
    }

    /**
     * Allocation forfeiture after commitment tax, excluding the tax itself.
     * An unaffordable submission cannot commit and has no settlement quote.
     */
    public int forfeiture() {
        if (!affordable()) {
            throw new IllegalStateException(
                    "an unaffordable submission cannot incur forfeiture");
        }
        return free ? 0 : rules.breakCharge(allocation, availableAp - tax);
    }
}