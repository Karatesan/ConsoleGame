package com.archon.verb;

import com.archon.address.Resolved;

public final class InspectVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.address(c, 0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        Check check = VerbTargets.resolution(resolved);
        if (!check.valid()) return VerbTargets.reject(c, check);

        c.say(TargetDescriptions.inspect(c.world, (Resolved.Target) resolved));
        return ExitCode.SUCCESS;
    }
}