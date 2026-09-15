package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.system.combat.CombatEngine;

import java.util.List;

public final class StrikeVerb implements Verb {

    @Override
    public String name() {
        return "strike";
    }

    @Override
    public String help() {
        return "strike [target] [-a part] [-p light|normal|heavy] [-f] — 1/2/3 AP.";
    }

    @Override
    public int apCost(Ast.Invocation inv) {
        return VerbHelpers.powerAp(inv, 2);
    }

    @Override
    public Check validateStructural(VerbContext c) {
        String t = c.inv.arg(0);
        if (t == null) {
            List<Entity> adj = c.world.hostilesAdjacentTo(c.thrall.pos());
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
        if (aim != null && BodyPart.parse(aim) == null) {
            return true;
        }

        int slash = target.indexOf('/');
        if (slash >= 0) {
            String part = target.substring(slash + 1);
            return BodyPart.parse(part) == null
                    && !part.startsWith("hand/");
        }

        return false;
    }

    @Override
    public Check validateState(VerbContext c) {
        String target = targetArgument(c);
        if (target == null) {
            return Check.blocked("nothing adjacent to strike", null);
        }

        Resolved resolved = VerbHelpers.resolve(c, target);
        Entity entity;

        if (resolved instanceof Resolved.OnEntity onEntity) {
            entity = onEntity.entity();
        } else if (resolved instanceof Resolved.OnItem onItem) {
            Actor owner = handContainerOwner(c, onItem);
            if (owner == null) {
                return Check.blocked("target is not a held item", null);
            }

            entity = owner;
        } else {
            return Check.blocked("target not present", null);
        }
        int reach = c.thrall.mainHand() != null && c.thrall.mainHand().has(Tag.HEAVY) ? 1 : 1;
        if (e.pos().chebyshev(c.thrall.pos()) > reach)
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
            Actor owner = c.world.actor(ownerId);
            if (owner == null || owner.mainHand() == null) { c.say("nothing to disarm"); return ExitCode.BLOCKED; }
            CombatEngine.DisarmResult disarm = CombatEngine.attemptDisarm(c.world.dice, c.world, owner);
            if (disarm.success()) {
                c.say(
                        "The " + disarm.weapon().name()
                                + " is knocked from "
                                + owner.name() + "'s grip."
                );
                return ExitCode.SUCCESS;
            }

            c.say("The blow glances off " + owner.name() + "'s weapon.");
            return ExitCode.MISS;
        }

        if (!(resolved instanceof Resolved.OnEntity onEntity)) {
            c.say("target cannot be struck");
            return ExitCode.BLOCKED;
        }

        Entity entity = onEntity.entity();
        BodyPart part = VerbHelpers.aimPart(c.inv, targetArg);

        String power = c.inv.flag("power");
        if (power == null) {
            power = "normal";
        }

        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                c.world.dice,
                c.world,
                c.thrall,
                entity,
                part,
                power,
                c.inv.hasFlag("force")
        );

        if (!result.hit()) {
            c.say(
                    "Strike at " + entity.name()
                            + "'s " + part.path + " — MISS."
            );
            return ExitCode.MISS;
        }

        c.say(String.format(
                "%s strikes %s's %s. %d dmg.",
                result.weapon() == null ? "Bare limb" : result.weapon().name(),
                entity.name(),
                part.path,
                result.damage()
        ));

        if (result.killed()) {
            c.say(entity.name() + " falls.");
            return ExitCode.SUCCESS;
        }

        return ExitCode.PARTIAL;
    }

    private String targetArgument(VerbContext c) {
        String target = c.inv.arg(0);
        return target != null
                ? target
                : VerbHelpers.soleAdjacentHostile(c);
    }

    private Actor handContainerOwner(VerbContext c, Resolved.OnItem item) {
        String container = item.container();
        if (container == null) {
            return null;
        }

        int handIndex = container.indexOf("/hand/");
        if (handIndex < 0) {
            return null;
        }

        return c.world.actor(container.substring(0, handIndex));
    }
}