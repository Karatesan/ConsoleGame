package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.Tag;
import com.archon.system.environment.Substances;

public final class SiphonVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAddress(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target target = c.resolveTarget(c.inv.arg(0));
        if (target == null) return ExitCode.BLOCKED;

        if (target.isCeilingTile()) {
            return c.rejectBlocked("cannot siphon from the ceiling");
        }
        if (!c.checkReachable(target)) return ExitCode.BLOCKED;

        Tag substance = Substances.extract(c.world, target);
        if (substance == null) {
            return c.rejectBlocked("nothing to siphon");
        }

        c.materialOut = new Material.OfSubstance(substance);
        c.say("Thrall draws " + substance.name().toLowerCase() + ".");
        return ExitCode.SUCCESS;
    }
}