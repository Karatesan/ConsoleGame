package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.address.Resolved;
import com.archon.world.Layer;

public final class LsVerb extends FreeVerb {
    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String help() {
        return "ls <address> — list contents. 0 AP.";
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0) == null ? "/pack" : c.inv.arg(0);
        Resolution resolution = VerbHelpers.resolve(c, address);
        Resolved target = VerbHelpers.found(resolution);

        if (target instanceof Resolved.PackRoot packRoot) {
            c.say(packRoot.owner().inventory().isPackEmpty()
                    ? "(empty)"
                    : String.join(
                            "\n",
                            packRoot.owner().inventory().pack().stream()
                                    .map(item -> "  " + item.id() + "  " + item.tags())
                                    .toList()));
            return ExitCode.SUCCESS;
        }

        if (target instanceof Resolved.TileTarget tileTarget && tileTarget.layer() == Layer.FLOOR) {
            c.say(tileTarget.tile().ground().isEmpty()
                    ? "(empty)"
                    : String.join(
                            "\n",
                            tileTarget.tile().ground().stream()
                                    .map(item -> "  " + item.id() + "  " + item.tags())
                                    .toList()));
            return ExitCode.SUCCESS;
        }

        c.say("Nothing to list at " + address + ".");
        return ExitCode.INVALID;
    }
}