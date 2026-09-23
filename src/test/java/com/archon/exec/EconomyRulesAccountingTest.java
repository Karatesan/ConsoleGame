package com.archon.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class EconomyRulesAccountingTest {

    @ParameterizedTest
    @CsvSource({
            "0, 1, 1, 1",
            "-1, 1, 1, 1",
            "5, -1, 1, 1",
            "5, 1, -1, 1",
            "5, 1, 1, -1"
    })
    void rejectsInvalidRuleValues(
            int roundAp,
            int untaxedLines,
            int lineTax,
            int breakPenalty
    ) {
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyRules(roundAp, untaxedLines, lineTax, breakPenalty));
    }

    @Test
    void prototypeTaxIsFlatAfterTheFirstPaidLine() {
        EconomyRules rules = EconomyRules.prototype();

        assertEquals(0, rules.taxForNextLine(0));
        assertEquals(1, rules.taxForNextLine(1));
        assertEquals(1, rules.taxForNextLine(2));
        assertEquals(1, rules.taxForNextLine(Integer.MAX_VALUE));
    }

    @Test
    void customRulesControlTheUntaxedAllowanceAndTax() {
        EconomyRules rules = new EconomyRules(9, 2, 3, 4);

        assertEquals(0, rules.taxForNextLine(0));
        assertEquals(0, rules.taxForNextLine(1));
        assertEquals(3, rules.taxForNextLine(2));
        assertEquals(3, rules.taxForNextLine(20));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 0",
            "2, 1",
            "3, 1",
            "4, 2",
            "5, 2"
    })
    void interruptedChargeRoundsDown(int stageCost, int expected) {
        assertEquals(expected, EconomyRules.prototype().interruptedCharge(stageCost));
    }

    @Test
    void breakChargeAddsThePenaltyAndCapsAtAvailableAp() {
        EconomyRules rules = EconomyRules.prototype();

        assertEquals(3, rules.breakCharge(2, 5));
        assertEquals(4, rules.breakCharge(4, 4));
        assertEquals(0, rules.breakCharge(4, 0));
    }

    @Test
    void breakChargeDoesNotOverflowBeforeApplyingItsCap() {
        EconomyRules rules = new EconomyRules(
                Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE,
                rules.breakCharge(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void calculationMethodsRejectNegativeInputs(int value) {
        EconomyRules rules = EconomyRules.prototype();

        assertThrows(IllegalArgumentException.class, () -> rules.taxForNextLine(value));
        assertThrows(IllegalArgumentException.class, () -> rules.interruptedCharge(value));
        assertThrows(IllegalArgumentException.class, () -> rules.breakCharge(value, 5));
        assertThrows(IllegalArgumentException.class, () -> rules.breakCharge(1, value));
    }
}