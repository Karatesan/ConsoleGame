package com.archon.verb;

public final class MapVerb implements Verb {

    @Override
    public ExitCode execute(VerbContext c) {
        c.bus.redraw();
        return ExitCode.SUCCESS;
    }
}