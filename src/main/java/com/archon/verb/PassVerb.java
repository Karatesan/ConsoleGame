package com.archon.verb;

public final class PassVerb implements Verb {

    @Override
    public ExitCode execute(VerbContext c) {
        c.say("Thrall falls still.");
        return ExitCode.SUCCESS;
    }
}