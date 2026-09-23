package com.archon.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RoundStateAccountingTest {

    @Test
    void startsWithTheSuppliedRules() {
        EconomyRules rules = new EconomyRules(7, 2, 3, 4);
        RoundState round = new RoundState(rules);

        assertSame(rules, round.rules());
        assertEquals(7, round.ap());
        assertEquals(0, round.linesUsed());
        assertEquals(1, round.roundNo());
    }

    @Test
    void spendingReducesApWithoutCountingALine() {
        RoundState round = new RoundState(EconomyRules.prototype());

        round.spend(2);

        assertEquals(3, round.ap());
        assertEquals(0, round.linesUsed());
        assertEquals(1, round.roundNo());
    }

    @Test
    void permitsSpendingTheEntireBudgetAndThenZero() {
        RoundState round = new RoundState(EconomyRules.prototype());

        round.spend(5);
        round.spend(0);

        assertEquals(0, round.ap());
    }

    @Test
    void zeroSpendingPreservesAnUnspentBudget() {
        RoundState round = new RoundState(EconomyRules.prototype());

        round.spend(0);

        assertEquals(5, round.ap());
        assertEquals(0, round.linesUsed());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void rejectsNegativeSpendingWithoutMutation(int amount) {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.spend(2);
        round.countLine();

        assertThrows(IllegalArgumentException.class, () -> round.spend(amount));

        assertEquals(3, round.ap());
        assertEquals(1, round.linesUsed());
        assertEquals(1, round.roundNo());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, Integer.MAX_VALUE})
    void rejectsOverspendingWithoutMutation(int amount) {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.spend(2);
        round.countLine();

        assertThrows(IllegalArgumentException.class, () -> round.spend(amount));

        assertEquals(3, round.ap());
        assertEquals(1, round.linesUsed());
        assertEquals(1, round.roundNo());
    }

    @Test
    void rejectsPositiveSpendingAfterBudgetIsExhausted() {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.spend(5);

        assertThrows(IllegalArgumentException.class, () -> round.spend(1));

        assertEquals(0, round.ap());
    }

    @Test
    void resetReturnsUnusedApAndRestoresTheSuppliedBudget() {
        RoundState round = new RoundState(new EconomyRules(7, 2, 3, 4));
        round.spend(3);
        round.countLine();
        round.countLine();

        int unused = round.reset();

        assertEquals(4, unused);
        assertEquals(7, round.ap());
        assertEquals(0, round.linesUsed());
        assertEquals(2, round.roundNo());
    }
}