package com.archon.verb;

public final class MapVerb extends FreeVerb {
    @Override public String name() { return "map"; }
    @Override public String help() { return "map — redraw grid. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        c.bus.redraw();
        return ExitCode.SUCCESS;
    }
}
