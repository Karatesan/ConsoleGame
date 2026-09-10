package com.archon.verb;

public final class HelpVerb extends FreeVerb {
    @Override public String name() { return "help"; }
    @Override public String help() { return "help [verb] — syntax. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        String v = c.inv.arg(0);
        if (v == null) {
            c.say(CommandManual.renderIndex());
        } else {
            String manual = CommandManual.lookup(v);
            c.say(manual == null ? "no such verb: " + v : manual);
        }
        return ExitCode.SUCCESS;
    }
}