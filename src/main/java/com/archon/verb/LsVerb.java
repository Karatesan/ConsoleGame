package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;

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
        Resolved target = VerbHelpers.found(VerbHelpers.resolve(c, address));

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

        if (target instanceof Resolved.TileTarget tileTarget
                && tileTarget.layer() == Address.Layer.FLOOR) {
            c.say(c.world.map().tile(tileTarget.pos()).ground().isEmpty()
                    ? "(empty)"
                    : String.join(
                            "\n",
                            c.world.map().tile(tileTarget.pos()).ground().stream()
                                    .map(item -> "  " + item.id() + "  " + item.tags())
                                    .toList()));
            return ExitCode.SUCCESS;
        }

        c.say("nothing to list at " + address);
        return ExitCode.INVALID;
    }
}