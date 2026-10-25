// src/main/java/com/archon/verb/ShootVerb.java
package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

public final class ShootVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAimedAddress(false);
    }

    private Check check(ValidationContext c, Resolved resolved) {
        Check resolution = resolved.toCheck();
        if (!resolution.valid()) return resolution;
        if (!(resolved instanceof Resolved.OnEntity || resolved instanceof Resolved.OnBody)) {
            return Check.blocked("shoot needs an entity or body-part target", null);
        }
        if (!c.thrall.isNocked()) {
            return Check.blocked("nothing nocked", "try: nock ; shoot <target>");
        }

        Entity target = resolved.targetEntity().orElseThrow();
        return SpatialService.lineOfSight(c.world, c.thrall.pos(), target.pos())
                ? Check.ok()
                : Check.blocked("no line of fire", null);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = c.resolve(c.inv.arg(0));
        Check check = check(c, resolved);
        if (!check.valid()) return c.reject(check);

        Entity target = resolved.targetEntity().orElseThrow();
        BodyPart part = c.aimPart(resolved);
        c.thrall.fireNocked();

        var result = CombatEngine.resolveRanged(c.world.dice(), c.world, c.thrall, target, part);
        if (!result.hit()) {
            c.say("Arrow flies wide of " + target.name() + ".");
            return ExitCode.MISS;
        }

        c.say("Arrow strikes " + target.name() + "'s " + part.path + ". " + result.damage() + " dmg.");
        if (result.killed()) {
            c.say(target.name() + " falls.");
        }
        return ExitCode.SUCCESS;
    }
}