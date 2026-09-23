package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.system.environment.Ignition;

public final class IgniteVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.address(c, 0);
    }

    private Check check(ValidationContext c, Resolved resolved) {
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) {
            return resolution;
        }
        if (!(resolved instanceof Resolved.OnEntity
                || resolved instanceof Resolved.OnTile tile && tile.layer() != Address.Layer.CEILING)) {
            return Check.blocked("ignite needs a whole entity or floor tile", null);
        }
        Check reach = VerbTargets.reachable(c, (Resolved.Target) resolved);
        if (!reach.valid()) {
            return reach;
        }
        return Ignition.hasFlame(c.thrall)
                ? Check.ok()
                : Check.blocked("no lit source in hand", "wield a torch first");
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        Check check = check(c, resolved);
        if (!check.valid()) {
            return VerbTargets.reject(c, check);
        }

        if (resolved instanceof Resolved.OnEntity target) {
            Entity entity = target.entity();
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