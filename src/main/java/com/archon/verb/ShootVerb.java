package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.system.combat.CombatEngine;
import com.archon.system.combat.CombatNarrative;
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
        if (c.thrall.has(Tag.BLIND) && target.pos().chebyshev(c.thrall.pos()) > Balance.BLIND_MAX_RANGE) {
            return c.rejectBlocked("blinded — cannot target beyond " + Balance.BLIND_MAX_RANGE + " tile");
        }
        if (!SpatialService.lineOfFire(c.world, c.thrall.pos(), target.pos())) {
            return c.rejectBlocked("no straight line of fire");
        }

        boolean isCreature = target.kind() == Entity.Kind.CREATURE;
        boolean aimed = isCreature && (c.inv.flag("aim") != null || resolved instanceof Resolved.OnBody);
        BodyPart part = null;
        if (isCreature) {
            part = aimed ? c.aimPart(resolved) : BodyPart.rollLocation(c.world.dice());
        }
        c.thrall.fireNocked();

        var result = CombatEngine.resolveRanged(c.world.dice(), c.world, c.thrall, target, part, aimed, c.log);
        ExitCode code = CombatNarrative.narrateRanged(c, c.world.dice(), target, result);
        c.flushLog();
        return code;
    }
}