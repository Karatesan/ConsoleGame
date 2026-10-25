// src/main/java/com/archon/verb/IgniteVerb.java
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

    private Check check(ValidationContext c, Resolved resolved) {
        Check resolution = resolved.toCheck();
        if (!resolution.valid()) return resolution;

        if (!(resolved instanceof Resolved.OnEntity || resolved.isFloorTile())) {
            return Check.blocked("ignite needs a whole entity or floor tile", null);
        }
        Check reach = c.checkReachable((Resolved.Target) resolved);
        if (!reach.valid()) return reach;

        return Ignition.hasFlame(c.thrall)
                ? Check.ok()
                : Check.blocked("no lit source in hand", "wield a torch first");
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = c.resolve(c.inv.arg(0));
        Check check = check(c, resolved);
        if (!check.valid()) return c.reject(check);

        if (resolved instanceof Resolved.OnEntity(Entity entity)) {
            if (!Ignition.entity(entity)) {
                c.say(entity.name() + " will not catch.");
                return ExitCode.MISS;
            }
            c.say(entity.name() + " catches fire.");
            return ExitCode.SUCCESS;
        }

        Vec2 at = ((Resolved.OnTile) resolved).pos();
        if (!Ignition.tile(c.world, at)) {
            c.say("nothing to burn at " + at);
            return ExitCode.MISS;
        }

        c.say("Fire takes hold at " + at + ".");
        return ExitCode.SUCCESS;
    }
}