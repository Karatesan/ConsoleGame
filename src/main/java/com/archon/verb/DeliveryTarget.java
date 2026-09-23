package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.Vec2;
import com.archon.system.environment.Substances;

sealed interface DeliveryTarget {

    enum Mode {
        POUR,
        THROW
    }

    record Selected(Vec2 pos) implements DeliveryTarget {}

    record Rejected(Check check) implements DeliveryTarget {}

    static DeliveryTarget select(VerbContext c, Mode mode) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(c.pipedInput ? 0 : 1));
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) {
            return new Rejected(resolution);
        }

        boolean accepted = resolved instanceof Resolved.OnEntity
                || resolved instanceof Resolved.OnTile tile && tile.layer() != Address.Layer.CEILING
                || mode == Mode.THROW && resolved instanceof Resolved.OnBody;
        if (!accepted) {
            String reason = mode == Mode.POUR
                    ? "pour needs an entity or floor tile"
                    : "throw needs an entity or floor target";
            return new Rejected(Check.blocked(reason, null));
        }

        Resolved.Target target = (Resolved.Target) resolved;
        if (mode == Mode.POUR) {
            Check reach = VerbTargets.reachable(c, target);
            if (!reach.valid()) {
                return new Rejected(reach);
            }
        }

        Substances.requireDestination(c.world, target.pos());
        return new Selected(target.pos());
    }
}