package com.archon.verb;

public interface Verb {

    /** Submission-time command-specific syntax and target checks. */
    default Check validateStructural(ValidationContext ctx) {
        return Check.ok();
    }

    /** Submission-time state checks for invocations in the first stage. */
    default Check validateState(ValidationContext ctx) {
        return Check.ok();
    }

    ExitCode execute(VerbContext ctx);
}