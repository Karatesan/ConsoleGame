package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

public final class ShootVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        if (c.inv.arg(0) == null) {
            return Check.invalid("shoot needs a target", "try: scan");
        }
        String aim = c.inv.flag("aim");
        return aim != null && BodyPart.parse(aim) == null
                ? Check.invalid("unknown hit location \"" + aim + "\"", null)
                : Check.ok();
    }

    @Override
    public Check validateState(ValidationContext c) {
        return check(c, VerbHelpers.resolve(c, c.inv.arg(0)));
    }

    private Check check(ValidationContext c, Resolved resolved) {
        Check syntax = validateStructural(c);
        if (!syntax.valid()) return syntax;

        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) return resolution;
        if (!(resolved instanceof Resolved.OnEntity || resolved instanceof Resolved.OnBody)) {
            return Check.invalid("shoot needs an entity or body-part target", null);
        }
        if (!c.thrall.isNocked()) {
            return Check.blocked("nothing nocked", "try: nock ; shoot <target>");
        }

        Entity target = VerbHelpers.targetEntity(resolved).orElseThrow();
        return SpatialService.lineOfSight(c.world, c.thrall.pos(), target.pos())
                ? Check.ok()
                : Check.blocked("no line of fire", null);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        Check check = check(c, resolved);
        if (!check.valid()) return VerbTargets.reject(c, check);

        Entity target = VerbHelpers.targetEntity(resolved).orElseThrow();
        BodyPart part = VerbHelpers.aimPart(c.inv, resolved).orElseThrow();
        c.thrall.fireNocked();

        CombatEngine.RangedHitResult result = CombatEngine.resolveRanged(
                c.world.dice(), c.world, c.thrall, target, part
        );
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