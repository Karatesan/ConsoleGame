package com.archon.verb;

public final class HelpVerb implements Verb {
    private final CommandManual manual;

    public HelpVerb(CommandManual manual) {
        this.manual = java.util.Objects.requireNonNull(manual, "manual");
    }

    @Override
    public ExitCode execute(VerbContext c) {
        c.say(manual.render(c.inv.arg(0)));
        return ExitCode.SUCCESS;
    }
}