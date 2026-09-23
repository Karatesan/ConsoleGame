package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.verb.*;

import java.util.Optional;

public final class LsVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.optionalAddress(c, 0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0) == null ? "/pack" : c.inv.arg(0);
        Resolved resolved = VerbHelpers.resolve(c, address);
        Check check = VerbTargets.resolution(resolved);
        if (!check.valid()) return VerbTargets.reject(c, check);

        Optional<String> listing = TargetDescriptions.list(c.world, (Resolved.Target) resolved);
        if (listing.isEmpty()) {
            return VerbTargets.reject(c, Check.blocked("nothing to list at " + address, null));
        }
        c.say(listing.get());
        return ExitCode.SUCCESS;
    }
}