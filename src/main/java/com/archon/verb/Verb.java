package com.archon.verb;

public interface Verb {

    /** Submission checks only; current inability to act belongs in execution. */
    default Check validateStructural(ValidationContext ctx) {
        return Check.ok();
    }

    ExitCode execute(VerbContext ctx);
}