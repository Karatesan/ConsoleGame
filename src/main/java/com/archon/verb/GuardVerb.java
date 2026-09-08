package com.archon.verb;

import com.archon.command.Ast;

public final class GuardVerb implements Verb {
    @Override public String name() { return "guard"; }
    @Override public String help() { return "guard — +30% armour until next round. 1 AP."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public Check validateStructural(VerbContext c) { return Check.ok(); }

    @Override
    public ExitCode execute(VerbContext c) {
        c.thrall.guarded = true;
        c.say("Thrall raises its guard.");
        return ExitCode.SUCCESS;
    }
}
