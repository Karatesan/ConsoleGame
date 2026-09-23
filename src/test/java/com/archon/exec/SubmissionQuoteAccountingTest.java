package com.archon.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SubmissionQuoteAccountingTest {

    @Test
    void firstPaidSubmissionUsesTheUntaxedBudgetWithoutMutation() {
        RoundState round = new RoundState(EconomyRules.prototype());

        SubmissionQuote quote = new SubmissionQuote(false, 3, round);

        assertFalse(quote.free());
        assertEquals(3, quote.allocation());
        assertEquals(0, quote.tax());
        assertEquals(3L, quote.requiredAp());
        assertEquals(5, quote.availableAp());
        assertTrue(quote.affordable());
        assertEquals(4, quote.forfeiture());
        assertEquals(5, round.ap());
        assertEquals(0, round.linesUsed());
    }

    @Test
    void forfeitureUsesApRemainingAfterCommitmentTax() {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.countLine();

        SubmissionQuote quote = new SubmissionQuote(false, 4, round);

        assertEquals(1, quote.tax());
        assertEquals(5L, quote.requiredAp());
        assertTrue(quote.affordable());
        assertEquals(4, quote.forfeiture());

        round.spend(quote.tax());

        assertEquals(
                round.rules().breakCharge(quote.allocation(), round.ap()),
                quote.forfeiture());
    }

    @Test
    void unaffordableSubmissionHasNoForfeitureQuote() {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.countLine();

        SubmissionQuote quote = new SubmissionQuote(false, 5, round);

        assertFalse(quote.affordable());
        assertEquals(6L, quote.requiredAp());
        assertThrows(IllegalStateException.class, quote::forfeiture);
        assertEquals(5, round.ap());
        assertEquals(1, round.linesUsed());
    }

    @Test
    void freeSubmissionRemainsAffordableWithNoApAndNoTaxAllowance() {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.countLine();
        round.spend(5);

        SubmissionQuote quote = new SubmissionQuote(true, 0, round);

        assertTrue(quote.free());
        assertTrue(quote.affordable());
        assertEquals(0, quote.tax());
        assertEquals(0L, quote.requiredAp());
        assertEquals(0, quote.forfeiture());
        assertEquals(0, round.ap());
        assertEquals(1, round.linesUsed());
    }

    @Test
    void zeroAllocationDoesNotMakeAPaidSubmissionFree() {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.countLine();

        SubmissionQuote quote = new SubmissionQuote(false, 0, round);

        assertFalse(quote.free());
        assertEquals(1, quote.tax());
        assertEquals(1L, quote.requiredAp());
        assertEquals(1, quote.forfeiture());
    }

    @Test
    void customRulesDetermineTaxAndForfeiture() {
        RoundState round = new RoundState(new EconomyRules(11, 0, 2, 3));

        SubmissionQuote quote = new SubmissionQuote(false, 4, round);

        assertEquals(2, quote.tax());
        assertEquals(6L, quote.requiredAp());
        assertTrue(quote.affordable());
        assertEquals(7, quote.forfeiture());
    }

    @Test
    void requiredApDoesNotOverflowWhenAllocationAndTaxAreLarge() {
        RoundState round = new RoundState(new EconomyRules(
                Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0));

        SubmissionQuote quote = new SubmissionQuote(false, Integer.MAX_VALUE, round);

        assertEquals(2L * Integer.MAX_VALUE, quote.requiredAp());
        assertFalse(quote.affordable());
    }

    @Test
    void quoteDoesNotChangeWhenTheRoundChanges() {
        RoundState round = new RoundState(EconomyRules.prototype());
        round.countLine();
        SubmissionQuote quote = new SubmissionQuote(false, 4, round);

        round.spend(5);
        round.reset();

        assertEquals(5, quote.availableAp());
        assertEquals(1, quote.tax());
        assertEquals(4, quote.forfeiture());
        assertTrue(quote.affordable());
    }

    @Test
    void rejectsInvalidQuoteConstruction() {
        RoundState round = new RoundState(EconomyRules.prototype());

        assertThrows(IllegalArgumentException.class,
                () -> new SubmissionQuote(false, -1, round));
        assertThrows(IllegalArgumentException.class,
                () -> new SubmissionQuote(true, 1, round));
        assertThrows(NullPointerException.class,
                () -> new SubmissionQuote(false, 1, null));
    }
}