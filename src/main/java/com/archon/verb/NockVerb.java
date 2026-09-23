package com.archon.verb;

public final class NockVerb implements Verb {

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