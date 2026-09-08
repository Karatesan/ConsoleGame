package com.archon.verb;

import com.archon.command.Ast;

public final class PassVerb implements Verb {
    @Override public String name() { return "pass"; }
    @Override public String help() { return "pass — forfeit remaining AP, end round. Free, never counts as a line."; }
    @Override public boolean free() { return true; }
    @Override public boolean terminal() { return true; }
    @Override public int apCost(Ast.Invocation inv) { return 0; }
    @Override public Check validateStructural(VerbContext c) { return Check.ok(); }

    @Override
    public ExitCode execute(VerbContext c) {
        c.say("Thrall falls still.");
        return ExitCode.SUCCESS;
    }
}
