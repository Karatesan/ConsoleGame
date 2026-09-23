package com.archon.exec;

import com.archon.command.Ast;
import com.archon.verb.Check;

import java.util.Objects;

public final class SubmissionChecks {
    public record Review(Check check, SubmissionQuote quote) {}

    private final Validator validator;
    private final ExecutionPolicy policy;
    private final RoundState round;

    public SubmissionChecks(Validator validator, ExecutionPolicy policy, RoundState round) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.round = Objects.requireNonNull(round, "round");
    }

    public Review review(Ast.Line line) {
        Check check = validator.validate(line);
        if (!check.valid()) {
            return new Review(check, null);
        }

        SubmissionQuote quote;
        try {
            quote = policy.quote(line, round);
        } catch (ArithmeticException exception) {
            return new Review(Check.invalid("submitted allocation exceeds the supported AP range", "submit less work"),
                    null);
        }

        if (!quote.affordable()) {
            return new Review(Check.blocked(
                    String.format("insufficient AP: %d AP tax + %d AP allocation = %d. Available: %d.", quote.tax(),
                            quote.allocation(), quote.requiredAp(), quote.availableAp()),
                    "reduce the submitted allocation"), quote);
        }
        return new Review(Check.ok(), quote);
    }
}