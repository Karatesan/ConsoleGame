package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;

public final class ShootVerb implements Verb {
    @Override public String name() { return "shoot"; }
    @Override public String help() { return "shoot <target> [-a part] — 2 AP. Requires nocked."; }
    @Override public int apCost(Ast.Invocation inv) { return 2; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) return Check.invalid("shoot needs a target", "try: scan");
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        if (!c.thrall.nocked) return Check.blocked("nothing nocked", "try: nock | shoot <target>");
        Entity e = VerbHelpers.targetEntity(c, c.inv.arg(0));
        if (e == null) return Check.blocked("unknown target", null);
        if (!c.world.lineOfSight(c.thrall.pos, e.pos)) return Check.blocked("no line of fire", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (!c.thrall.nocked) { c.say("nothing nocked"); return ExitCode.BLOCKED; }
        Entity e = VerbHelpers.targetEntity(c, c.inv.arg(0));
        if (e == null) { c.say("target gone"); return ExitCode.BLOCKED; }
        c.thrall.nocked = false;

        int dist = e.pos.chebyshev(c.thrall.pos);
        BodyPart part = VerbHelpers.aimPart(c.inv, c.inv.arg(0));
        int band = dist <= 1 ? -20 : dist <= 4 ? 0 : dist <= 8 ? -10 : -25;
        int hit = 70 + part.hitMod + band - e.evasion;

        if (!c.world.dice.chance(Math.max(5, Math.min(95, hit)))) {
            c.say("Arrow flies wide of " + e.name + ".");
            return ExitCode.MISS;
        }
        int dmg = Math.max(1, c.world.dice.between(5, 10) - e.armor);
        e.hp -= dmg;
        c.say("Arrow strikes " + e.name + "'s " + part.path + ". " + dmg + " dmg.");
        if (!e.alive()) { c.say(e.name + " falls."); return ExitCode.SUCCESS; }
        return ExitCode.PARTIAL;
    }
}
