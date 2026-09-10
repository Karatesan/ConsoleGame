package com.archon.verb;

import com.archon.command.Ast;

import java.util.List;

public final class GuardVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "guard",
            "Raise defense (+30% armor until next round)",
            "COMBAT",
            "guard",
            "1 AP",
            "Raises guard, increasing the thrall's armor rating by +30% until the start\n"
                    + "of the next round. Excellent fallback stage in conditional chains.",
            List.of(
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            false,
            false,
            List.of(
                    "guard",
                    "strike o1 || guard"
            )
    );

    @Override public String name() { return "guard"; }
    @Override public VerbDoc doc() { return DOC; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public Check validateStructural(VerbContext c) { return Check.ok(); }

    @Override
    public ExitCode execute(VerbContext c) {
        c.thrall.guarded = true;
        c.say("Thrall raises its guard.");
        return ExitCode.SUCCESS;
    }
}
