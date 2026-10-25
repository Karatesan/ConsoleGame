package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.system.environment.Ignition;

public final class IgniteVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAddress(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target target = c.resolveTarget(c.inv.arg(0));
        if (target == null) return ExitCode.BLOCKED;

        if (!(target instanceof Resolved.OnEntity || target.isFloorTile())) {
            return c.rejectBlocked("ignite needs a whole entity or floor tile");
        }
        if (!c.checkReachable(target)) return ExitCode.BLOCKED;
        if (!Ignition.hasFlame(c.thrall)) {
            return c.rejectBlocked("no lit source in hand");
        }

        if (target instanceof Resolved.OnEntity(Entity entity)) {
            if (!Ignition.entity(entity)) {
                c.say(entity.name() + " will not catch.");
                return ExitCode.MISS;
            }
            c.say(entity.name() + " catches fire.");
            return ExitCode.SUCCESS;
        }

        Vec2 at = target.pos();
        if (!Ignition.tile(c.world, at)) {
            c.say("nothing to burn at " + at);
            return ExitCode.MISS;
        }

        c.say("Fire takes hold at " + at + ".");
        return ExitCode.SUCCESS;
    }
}