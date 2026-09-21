package com.archon.verb;

public final class NockVerb implements Verb {

    @Override
    public Check validateState(ValidationContext c) {
        if (c.thrall.isNocked()) {
            return Check.blocked("already nocked", null);
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (c.thrall.isNocked()) {
            c.say("already nocked");
            return ExitCode.BLOCKED;
        }

        c.thrall.nock();
        c.say("Arrow nocked.");
        return ExitCode.SUCCESS;
    }
}