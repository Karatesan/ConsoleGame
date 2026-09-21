package com.archon.verb;

public final class GuardVerb implements Verb {

    @Override
    public ExitCode execute(VerbContext c) {
        c.thrall.setGuarded(true);
        c.say("Thrall raises its guard.");
        return ExitCode.SUCCESS;
    }
}