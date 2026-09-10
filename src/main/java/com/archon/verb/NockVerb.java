package com.archon.verb;

import com.archon.command.Ast;

import java.util.List;

public final class NockVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "nock",
            "Load arrow into ranged weapon",
            "COMBAT",
            "nock",
            "1 AP",
            "Loads an arrow into the ranged weapon. Nocked status persists across\n"
                    + "rounds until fired or dropped.",
            List.of(
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            false,
            false,
            List.of(
                    "nock",
                    "nock ; shoot g1"
            )
    );

    @Override public String name() { return "nock"; }
    @Override public VerbDoc doc() { return DOC; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public Check validateStructural(VerbContext c) { return Check.ok(); }

    @Override
    public Check validateState(VerbContext c) {
        if (c.thrall.nocked) return Check.blocked("already nocked", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (c.thrall.nocked) { c.say("already nocked"); return ExitCode.BLOCKED; }
        c.thrall.nocked = true;
        c.say("Arrow nocked.");
        return ExitCode.SUCCESS;
    }
}
