package com.archon.verb;

import com.archon.address.Resolved;

public final class LsVerb extends FreeVerb {
    @Override public String name() { return "ls"; }
    @Override public String help() { return "ls <address> — list contents. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        String a = c.inv.arg(0) == null ? "/pack" : c.inv.arg(0);
        Resolved r = VerbHelpers.resolve(c, a);
        if (r instanceof Resolved.OnItem oi && "pack".equals(oi.container())) {
            c.say(c.thrall.inventory().isEmpty() ? "(empty)" :
                    String.join("\n", c.thrall.inventory().pack().stream()
                            .map(i -> "  " + i.id + "  " + i.tags).toList()));
            return ExitCode.SUCCESS;
        }
        if (r instanceof Resolved.OnTile ot) {
            c.say(c.world.tile(ot.pos()).ground.toString());
            return ExitCode.SUCCESS;
        }
        c.say("nothing to list at " + a);
        return ExitCode.INVALID;
    }
}
