package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.system.environment.Substances;

public final class SiphonVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.address(c, 0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) {
            return VerbTargets.reject(c, resolution);
        }
        if (resolved instanceof Resolved.OnTile tile && tile.layer() == Address.Layer.CEILING) {
            return VerbTargets.reject(c, Check.blocked("cannot siphon from the ceiling", null));
        }

        Resolved.Target target = (Resolved.Target) resolved;
        Check reach = VerbTargets.reachable(c, target);
        if (!reach.valid()) {
            return VerbTargets.reject(c, reach);
        }

        Substances.Extraction extraction = Substances.extract(c.world, target);
        if (!(extraction instanceof Substances.Portion portion)) {
            Check failure = extraction == Substances.Unavailable.STALE_SOURCE
                    ? Check.blocked("item no longer at its source", null)
                    : Check.blocked("nothing to siphon", "try: inspect " + c.inv.arg(0));
            return VerbTargets.reject(c, failure);
        }

        c.materialOut = new Material.OfSubstance(portion.substance());
        c.say("Thrall draws " + portion.substance().name().toLowerCase() + ".");
        return ExitCode.SUCCESS;
    }
}