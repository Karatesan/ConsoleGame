package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.address.Resolved;

public final class LsVerb extends FreeVerb {
    @Override public String name() { return "ls"; }
    @Override public String help() { return "ls <address> — list contents. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        String a = c.inv.arg(0) == null ? "/pack" : c.inv.arg(0);
        Resolution resolution = VerbHelpers.resolve(c, a);
        Resolved target = VerbHelpers.found(resolution);
        if (target instanceof Resolved.PackRoot packRoot) {
            c.say(packRoot.owner().inventory().isPackEmpty() ? "(empty)" :
                    String.join("\n", packRoot.owner().inventory().pack().stream()
                            .map(i -> "  " + i.id() + "  " + i.tags()).toList()));
            return ExitCode.SUCCESS;
        }
        if (target instanceof Resolved.TileTarget tileTarget) {
            c.say(c.world.tile(tileTarget.pos()).ground().toString());
            return ExitCode.SUCCESS;
        }
        c.say("nothing to list at " + a);
        return ExitCode.INVALID;
    }
}
