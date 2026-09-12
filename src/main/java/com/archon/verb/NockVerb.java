package com.archon.verb;

import com.archon.command.Ast;
import com.archon.thrall.WeaponState;

public final class NockVerb implements Verb {
    @Override public String name() { return "nock"; }
    @Override public String help() { return "nock — load a missile. 1 AP. Persists across rounds."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public Check validateStructural(VerbContext c) { return Check.ok(); }

    @Override
    public Check validateState(VerbContext c) {
        if (c.thrall.weaponState() == WeaponState.NOCKED) return Check.blocked("already nocked", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (c.thrall.weaponState() == WeaponState.NOCKED) { c.say("already nocked"); return ExitCode.BLOCKED; }
        c.thrall.setWeaponState(WeaponState.NOCKED);
        c.say("Arrow nocked.");
        return ExitCode.SUCCESS;
    }
}