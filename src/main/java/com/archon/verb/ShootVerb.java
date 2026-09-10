package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.system.combat.CombatEngine;

import java.util.List;

public final class ShootVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "shoot",
            "Ranged attack against visible target in line-of-sight",
            "COMBAT",
            "shoot <target> [-a part]",
            "2 AP (Requires nocked arrow)",
            "Fires a nocked arrow at a target creature. Requires unobstructed line of sight\n"
                    + "and an already nocked arrow. Consumes the nocked arrow upon firing.",
            List.of(
                    new VerbDoc.FlagDoc("-a, --aim <part>", "Aim for a body part: head, torso, arm.l, arm.r, legs."),
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            false,
            false,
            List.of(
                    "shoot g1",
                    "shoot o1 -a head",
                    "nock ; shoot g1"
            )
    );

    @Override public String name() { return "shoot"; }
    @Override public VerbDoc doc() { return DOC; }
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

        BodyPart part = VerbHelpers.aimPart(c.inv, c.inv.arg(0));
        CombatEngine.RangedHitResult result = CombatEngine.resolveRanged(
                c.world.dice,
                c.world,
                c.thrall,
                e,
                part
        );

        if (!result.hit()) {
            c.say("Arrow flies wide of " + e.name + ".");
            return ExitCode.MISS;
        }
        c.say("Arrow strikes " + e.name + "'s " + part.path + ". " + result.damage() + " dmg.");
        if (result.killed()) { c.say(e.name + " falls."); return ExitCode.SUCCESS; }
        return ExitCode.PARTIAL;
    }
}
