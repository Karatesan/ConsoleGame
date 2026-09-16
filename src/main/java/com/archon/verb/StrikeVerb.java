package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

import java.util.List;

public final class StrikeVerb implements Verb {
    @Override public String name() { return "strike"; }
    @Override public String help() { return "strike [target] [-a part] [-p light|normal|heavy] [-f] — 1/2/3 AP."; }
    @Override public int apCost(Ast.Invocation inv) { return VerbHelpers.powerAp(inv, 2); }

    @Override
    public Check validateStructural(VerbContext c) {
        String target = c.inv.arg(0);
        if (target == null) {
            List<Entity> adjacent = SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
            if (adjacent.isEmpty()) return Check.blocked("nothing adjacent to strike", null);
            if (adjacent.size() > 1) {
                return Check.invalid(
                        "ambiguous target: " + adjacent.stream().map(Entity::id).toList(),
                        "name one explicitly"
                );
            }
            return Check.ok();
        }

        if (aimPartInvalid(c, target)) {
            return Check.invalid(
                    "no such hit location on " + target,
                    "valid: head, torso, arm.l, arm.r, legs"
            );
        }

        if (VerbHelpers.resolve(c, target) == null) {
            return Check.invalid("unknown target \"" + target + "\"", "try: scan");
        }

        return Check.ok();
    }

    private boolean aimPartInvalid(VerbContext c, String target) {
        String aim = c.inv.flag("aim");
        if (aim != null && BodyPart.parse(aim) == null) return true;

        if (target.contains("/")) {
            String part = target.substring(target.indexOf('/') + 1);
            return BodyPart.parse(part) == null && !part.startsWith("hand/");
        }

        return false;
    }

    private String targetArg(VerbContext c) {
        String target = c.inv.arg(0);
        if (target != null) return target;

        List<Entity> adjacent = SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
        return adjacent.size() == 1 ? adjacent.get(0).id() : null;
    }

    @Override
    public Check validateState(VerbContext c) {
        String target = targetArg(c);
        Resolved resolved = VerbHelpers.resolve(c, target);

        if (resolved instanceof Resolved.OnEntity onEntity) {
            Entity entity = onEntity.entity();
            if (entity.pos().chebyshev(c.thrall.pos()) > 1) {
                return Check.blocked(entity.id() + " out of reach", "step closer first");
            }
            return Check.ok();
        }

        if (resolved instanceof Resolved.OnItem onItem
                && onItem.container() != null
                && onItem.container().contains("/hand/")) {
            String ownerId = onItem.container().substring(0, onItem.container().indexOf("/hand/"));
            Actor owner = c.world.actor(ownerId);
            if (owner == null || owner.mainHand() == null) {
                return Check.blocked("nothing to disarm", null);
            }
            if (owner.pos().chebyshev(c.thrall.pos()) > 1) {
                return Check.blocked(owner.id() + " out of reach", "step closer first");
            }
            return Check.ok();
        }

        return Check.blocked("target not present", null);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String targetArg = targetArg(c);
        if (targetArg == null) {
            c.say("nothing to strike");
            return ExitCode.BLOCKED;
        }

        Resolved resolved = VerbHelpers.resolve(c, targetArg);
        if (resolved == null) {
            c.say(targetArg + " is no longer there");
            return ExitCode.BLOCKED;
        }

        if (resolved instanceof Resolved.OnItem onItem
                && onItem.container() != null
                && onItem.container().contains("/hand/")) {
            String ownerId = onItem.container().substring(0, onItem.container().indexOf("/hand/"));
            Actor owner = c.world.actor(ownerId);
            if (owner == null || owner.mainHand() == null) {
                c.say("nothing to disarm");
                return ExitCode.BLOCKED;
            }

            CombatEngine.DisarmResult disarm = CombatEngine.attemptDisarm(c.world.dice(), c.world, owner);
            if (disarm.success()) {
                c.say("The " + disarm.weapon().name() + " is knocked from " + owner.name() + "'s grip.");
                return ExitCode.SUCCESS;
            }

            c.say("The blow glances off " + owner.name() + "'s weapon.");
            return ExitCode.MISS;
        }

        if (!(resolved instanceof Resolved.OnEntity onEntity)) {
            c.say("target not present");
            return ExitCode.BLOCKED;
        }

        Entity target = onEntity.entity();
        BodyPart part = VerbHelpers.aimPart(c.inv, targetArg);
        String power = c.inv.flag("power") == null ? "normal" : c.inv.flag("power");

        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                c.world.dice(),
                c.world,
                c.thrall,
                target,
                part,
                power,
                c.inv.hasFlag("force")
        );

        if (!result.hit()) {
            c.say("Strike at " + target.name() + "'s " + part.path + " — MISS.");
            return ExitCode.MISS;
        }

        c.say(String.format(
                "%s strikes %s's %s. %d dmg.",
                result.weapon() == null ? "Bare limb" : result.weapon().name(),
                target.name(),
                part.path,
                result.damage()
        ));

        if (result.killed()) {
            c.say(target.name() + " falls.");
            return ExitCode.SUCCESS;
        }

        return ExitCode.PARTIAL;
    }
}