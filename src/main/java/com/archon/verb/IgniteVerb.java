package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.Tag;
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

        if (!(target instanceof Resolved.OnEntity || target instanceof Resolved.OnTile || target instanceof Resolved.OnItem)) {
            return c.rejectBlocked("ignite needs a whole entity, item, or floor tile");
        }
        if (!c.checkReachable(target)) return ExitCode.BLOCKED;
        if (!Ignition.hasFlame(c.thrall)) {
            return c.rejectBlocked("no lit source in hand");
        }

        if (target instanceof Resolved.OnItem(Item item, var ignored)) {
            if (item.has(Tag.LIT)) {
                return c.rejectBlocked(item.name() + " is already lit");
            }
            if (!item.has(Tag.FLAMMABLE) && !item.has(Tag.OIL)) {
                c.say(item.name() + " will not catch.");
                return ExitCode.MISS;
            }
            item.tags().add(Tag.LIT);
            c.say(item.name() + " catches fire.");
            return ExitCode.SUCCESS;
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
        if (!Ignition.tile(c.world, at, c.log)) {
            c.say("nothing to burn at " + at);
            return ExitCode.MISS;
        }

        c.say("Fire takes hold at " + at + ".");
        c.flushLog();
        return ExitCode.SUCCESS;
    }
}