package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.ItemLocation;

public final class LsVerb implements Verb {

    @Override
    public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0) == null ? "/pack" : c.inv.arg(0);
        Resolved resolved = VerbHelpers.resolve(c, address);
        if (resolved instanceof Resolved.Failure failure) {
            c.say(failure.reason());
            return ExitCode.INVALID;
        }
        String listing = switch (resolved) {
            case Resolved.OnLocation target when target.location() instanceof ItemLocation.Pack pack ->
                    pack.owner().inventory().isPackEmpty() ? "(empty)" :
                            String.join("\n", pack.owner().inventory().pack().stream()
                                    .map(item -> "  " + item.id() + "  " + item.tags()).toList());
            case Resolved.OnLocation ignored -> "(empty)";
            case Resolved.OnItem target -> "  " + target.item().id() + "  " + target.item().tags();
            case Resolved.OnTile target -> target.layer() == Address.Layer.CEILING
                    ? "ceiling — tags " + c.world.tile(target.pos()).ceiling()
                    : c.world.tile(target.pos()).ground().toString();
            default -> null;
        };
        c.say(listing == null ? "nothing to list at " + address : listing);
        return listing == null ? ExitCode.INVALID : ExitCode.SUCCESS;
    }
}