package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.system.combat.CombatEngine;

import java.util.List;

public final class StrikeVerb implements Verb {
    @Override public String name() { return "strike"; }
    @Override public String help() { return "strike [target] [-a part] [-p light|normal|heavy] [-f] — 1/2/3 AP."; }
    @Override public int apCost(Ast.Invocation inv) { return VerbHelpers.powerAp(inv, 2); }

    @Override
    public Check validateStructural(VerbContext c) {
        String t = c.inv.arg(0);
        if (t == null) {
            List<Entity> adj = c.world.hostilesAdjacentTo(c.thrall.pos);
            if (adj.isEmpty()) return Check.blocked("nothing adjacent to strike", null);
            if (adj.size() > 1) return Check.invalid("ambiguous target: "
                    + adj.stream().map(e -> e.id).toList(), "name one explicitly");
            return Check.ok();
        }
        if (aimPartInvalid(c, t)) return Check.invalid(
                "no such hit location on " + t,
                "valid: head, torso, arm.l, arm.r, legs");
        Resolved r = VerbHelpers.resolve(c, t);
        if (r == null) return Check.invalid("unknown target \"" + t + "\"", "try: scan");
        return Check.ok();
    }

    private boolean aimPartInvalid(VerbContext c, String target) {
        String aim = c.inv.flag("aim");
        if (aim != null && BodyPart.parse(aim) == null) return true;
        if (target.contains("/")) {
            String p = target.substring(target.indexOf('/') + 1);
            return BodyPart.parse(p) == null && !p.startsWith("hand/");
        }
        return false;
    }

    @Override
    public Check validateState(VerbContext c) {
        String t = c.inv.arg(0) == null ? VerbHelpers.soleAdjacentHostile(c) : c.inv.arg(0);
        Entity e = VerbHelpers.targetEntity(c, t);
        if (e == null) {
            Resolved r = VerbHelpers.resolve(c, t);
            if (r instanceof Resolved.OnItem) return Check.ok(); // striking a held item
            return Check.blocked("target not present", null);
        }
        int reach = c.thrall.mainHand() != null && c.thrall.mainHand().has(Tag.HEAVY) ? 1 : 1;
        if (e.pos.chebyshev(c.thrall.pos) > reach)
            return Check.blocked(e.id + " out of reach", "step closer first");
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String targetArg = c.inv.arg(0) == null ? VerbHelpers.soleAdjacentHostile(c) : c.inv.arg(0);
        if (targetArg == null) { c.say("nothing to strike"); return ExitCode.BLOCKED; }

        Resolved r = VerbHelpers.resolve(c, targetArg);
        if (r == null) { c.say(targetArg + " is no longer there"); return ExitCode.BLOCKED; }

        // Striking a held item = disarm attempt.
        if (r instanceof Resolved.OnItem oi && oi.container() != null && oi.container().contains("hand")) {
            String ownerId = oi.container().split("/")[0];
            Entity owner = c.world.get(ownerId);
            if (owner == null || owner.held == null) { c.say("nothing to disarm"); return ExitCode.BLOCKED; }
            CombatEngine.DisarmResult disarm = CombatEngine.attemptDisarm(c.world.dice, c.world, owner);
            if (disarm.success()) {
                c.say("The " + disarm.weapon().name + " is knocked from " + owner.name + "'s grip.");
                return ExitCode.SUCCESS;
            }
            c.say("The blow glances off " + owner.name + "'s weapon.");
            return ExitCode.MISS;
        }

        Entity e = ((Resolved.OnEntity) r).entity();
        BodyPart part = VerbHelpers.aimPart(c.inv, targetArg);
        String power = c.inv.flag("power") == null ? "normal" : c.inv.flag("power");
        boolean force = c.inv.hasFlag("force");

        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                c.world.dice, c.world, c.thrall, e, part, power, force);

        if (!result.hit()) {
            c.say("Strike at " + e.name + "'s " + part.path + " — MISS.");
            return ExitCode.MISS;
        }

        c.say(String.format("%s strikes %s's %s. %d dmg.",
                result.weapon() == null ? "Bare limb" : result.weapon().name, e.name, part.path, result.damage()));

        if (result.killed()) {
            c.say(e.name + " falls.");
            return ExitCode.SUCCESS;
        }
        return ExitCode.PARTIAL;
    }
}
