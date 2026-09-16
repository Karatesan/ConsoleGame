package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

public final class ShootVerb implements Verb {
    @Override
    public String name() {
        return "shoot";
    }

    @Override
    public String help() {
        return "shoot <target> [-a part] — 2 AP. Requires nocked.";
    }

    @Override
    public int apCost(Ast.Invocation inv) {
        return 2;
    }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) {
            return Check.invalid("shoot needs a target", "try: scan");
        }
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        if (!c.thrall.isNocked()) {
            return Check.blocked("nothing nocked", "try: nock | shoot <target>");
        }

        Entity target = VerbHelpers.targetEntity(c, c.inv.arg(0));
        if (target == null) {
            return Check.blocked("unknown target", null);
        }

        if (!SpatialService.lineOfSight(c.world, c.thrall.pos(), target.pos())) {
            return Check.blocked("no line of fire", null);
        }

        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (!c.thrall.fireNocked()) {
            c.say("nothing nocked");
            return ExitCode.BLOCKED;
        }

        Entity target = VerbHelpers.targetEntity(c, c.inv.arg(0));
        if (target == null) {
            c.say("target gone");
            return ExitCode.BLOCKED;
        }

        BodyPart part = VerbHelpers.aimPart(c.inv, c.inv.arg(0));
        CombatEngine.RangedHitResult result = CombatEngine.resolveRanged(
                c.world.dice(),
                c.world,
                c.thrall,
                target,
                part
        );

        if (!result.hit()) {
            c.say("Arrow flies wide of " + target.name() + ".");
            return ExitCode.MISS;
        }

        c.say("Arrow strikes " + target.name() + "'s " + part.path + ". " + result.damage() + " dmg.");

        if (result.killed()) {
            c.say(target.name() + " falls.");
            return ExitCode.SUCCESS;
        }

        return ExitCode.PARTIAL;
    }
}
