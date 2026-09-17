package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

import java.util.List;

public final class StrikeVerb implements Verb {
    @Override public String name() { return "strike"; }
    @Override public String help() { return "strike [target] [-a part] [-p light|normal|heavy] [-f] — 1/2/3 AP."; }
    @Override public int apCost(Ast.Invocation inv) { return VerbHelpers.powerAp(inv, 2); }

    @Override
    public Check validateStructural(VerbContext c) {
        String aim = c.inv.flag("aim");
        if (aim != null && BodyPart.parse(aim) == null) {
            return Check.invalid(
                    "no such hit location: " + aim,
                    "valid: head, torso, arm.l, arm.r, legs"
            );
        }

        String target = selectedTarget(c);
        if (target == null) {
            List<Entity> adjacent = SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
            if (adjacent.isEmpty()) {
                return Check.blocked("nothing adjacent to strike", null);
            }
            return Check.invalid(
                    "ambiguous target: " + adjacent.stream().map(Entity::id).toList(),
                    "name one explicitly"
            );
        }

        if (Resolution.resolve(c.world, c.thrall, target) == null) {
            return Check.invalid("unknown target \"" + target + "\"", "try: scan");
        }

        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        Resolved resolved = resolveTarget(c);
        if (resolved instanceof Resolved.EntityTarget entityTarget) {
            return validateReach(c, entityTarget.entity());
        }

        if (resolved instanceof Resolved.BodyTarget bodyTarget) {
            return validateReach(c, bodyTarget.entity());
        }

        if (resolved instanceof Resolved.EquipmentTarget equipmentTarget
                && isHandSlot(equipmentTarget.slot())) {
            Actor owner = equipmentTarget.owner();
            if (owner == null || owner.mainHand() == null) {
                return Check.blocked("nothing to disarm", null);
            }
            return validateReach(c, owner);
        }

        return Check.blocked("target not present", null);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String targetArg = selectedTarget(c);
        if (targetArg == null) {
            c.say("nothing to strike");
            return ExitCode.BLOCKED;
        }

        Resolved resolved = Resolution.resolve(c.world, c.thrall, targetArg);
        if (resolved == null) {
            c.say(targetArg + " is no longer there");
            return ExitCode.BLOCKED;
        }

        if (resolved instanceof Resolved.EquipmentTarget equipmentTarget
                && isHandSlot(equipmentTarget.slot())) {
            Actor owner = equipmentTarget.owner();
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

        Entity target;
        BodyPart part;
        if (resolved instanceof Resolved.BodyTarget bodyTarget) {
            target = bodyTarget.entity();
            part = bodyTarget.bodyPart();
        } else if (resolved instanceof Resolved.EntityTarget entityTarget) {
            target = entityTarget.entity();
            part = VerbHelpers.aimPart(c.inv);
        } else {
            c.say("target not present");
            return ExitCode.BLOCKED;
        }

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

    private String selectedTarget(VerbContext c) {
        String explicitTarget = c.inv.arg(0);
        if (explicitTarget != null) {
            return explicitTarget;
        }

        List<Entity> adjacent = SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
        return adjacent.size() == 1 ? adjacent.get(0).id() : null;
    }

    private Resolved resolveTarget(VerbContext c) {
        String target = selectedTarget(c);
        return target == null ? null : Resolution.resolve(c.world, c.thrall, target);
    }

    private Check validateReach(VerbContext c, Entity target) {
        if (target.pos().chebyshev(c.thrall.pos()) > 1) {
            return Check.blocked(target.id() + " out of reach", "step closer first");
        }
        return Check.ok();
    }

    private boolean isHandSlot(EquipmentSlot slot) {
        return slot != null && slot.name().contains("HAND");
    }
}