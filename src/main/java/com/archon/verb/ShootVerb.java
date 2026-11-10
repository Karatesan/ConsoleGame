package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

public final class ShootVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAimedAddress(false);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target resolved = c.resolveTarget(c.inv.arg(0));
        if (resolved == null) return ExitCode.BLOCKED;

        Entity target = resolved.targetEntity();
        if (target == null) {
            return c.rejectBlocked("shoot needs an entity or body-part target");
        }
        if (!c.thrall.isNocked()) {
            return c.rejectBlocked("nothing nocked");
        }
        if (c.thrall.has(Tag.BLIND)) {
            return c.rejectBlocked("blinded — cannot aim ranged weapon");
        }
        if (!SpatialService.lineOfFire(c.world, c.thrall.pos(), target.pos())) {
            return c.rejectBlocked("no straight line of fire");
        }

        BodyPart part = c.aimPart(resolved);
        c.thrall.fireNocked();

        var result = CombatEngine.resolveRanged(c.world.dice(), c.world, c.thrall, target, part);
        if (!result.hit()) {
            c.say("Arrow flies wide of " + target.name() + ".");
            return ExitCode.MISS;
        }

        String crit = result.crit() ? "CRITICAL! " : "";
        c.say(crit + "Arrow strikes " + target.name() + "'s " + part.path + ". " + result.damage() + " dmg.");
        if (result.crippledPart()) {
            c.say(target.name() + "'s " + part.path + " is crippled!");
        }
        if (result.blindedTarget()) {
            c.say(target.name() + " is blinded!");
        }
        if (result.killed()) {
            c.say(target.name() + " falls.");
        }
        return ExitCode.SUCCESS;
    }
}