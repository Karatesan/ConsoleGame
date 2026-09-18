package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.*;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

import java.util.List;

import java.util.List;

import java.util.List;

public final class StrikeVerb implements Verb {
    @Override public String name() { return "strike"; }
    @Override public String help() {
        return "strike [target] [-a part] [-p light|normal|heavy] [-f] — 1/2/3 AP.";
    }
    @Override public int apCost(Ast.Invocation inv) { return VerbHelpers.powerAp(inv, 2); }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) {
            List<Entity> adjacent = SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
            if (adjacent.isEmpty()) return Check.blocked("nothing adjacent to strike", null);
            if (adjacent.size() > 1) {
                return Check.invalid(
                        "ambiguous target: " + adjacent.stream().map(Entity::id).toList(),
                        "name one explicitly"
                );
            }
        }
        Check check = targetCheck(c, resolveTarget(c));
        return check.valid() ? check : Check.invalid(check.reason(), check.hint());
    }

    private Resolved resolveTarget(VerbContext c) {
        String address = c.inv.arg(0);
        if (address == null) address = VerbHelpers.soleAdjacentHostile(c);
        return address == null
                ? new Resolved.Failure(
                Resolved.Problem.UNKNOWN_ENTITY,
                "no single adjacent target; name one explicitly"
        )
                : VerbHelpers.resolve(c, address);
    }

    private boolean isHandTarget(Resolved resolved) {
        return resolved instanceof Resolved.ItemTarget target
                && target.location() instanceof ItemLocation.Equipment equipment
                && (equipment.slot() == EquipmentSlot.HAND_LEFT
                || equipment.slot() == EquipmentSlot.HAND_RIGHT);
    }

    private Check targetCheck(VerbContext c, Resolved resolved) {
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) return resolution;
        if (VerbHelpers.aimPart(c.inv, resolved).isEmpty()) {
            return Check.invalid(
                    "unknown hit location",
                    "valid: head, torso, arm.l, arm.r, legs"
            );
        }
        return resolved instanceof Resolved.OnEntity
                || resolved instanceof Resolved.OnBody
                || isHandTarget(resolved)
                ? Check.ok()
                : Check.invalid("strike needs an entity, body part, or hand slot", null);
    }

    private Check check(VerbContext c, Resolved resolved) {
        Check target = targetCheck(c, resolved);
        if (!target.valid()) return target;
        if (isHandTarget(resolved) && resolved instanceof Resolved.OnLocation) {
            return Check.blocked("nothing in that hand to disarm", null);
        }
        return VerbTargets.reachable(c, (Resolved.Target) resolved);
    }

    @Override
    public Check validateState(VerbContext c) {
        return check(c, resolveTarget(c));
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = resolveTarget(c);
        Check check = check(c, resolved);
        if (!check.valid()) return VerbTargets.reject(c, check);

        if (resolved instanceof Resolved.OnItem item
                && item.location() instanceof ItemLocation.Equipment equipment) {
            Actor owner = equipment.owner();
            CombatEngine.DisarmResult result = CombatEngine.attemptDisarm(
                    c.world.dice(), c.world, owner, equipment.slot()
            );
            if (result.success()) {
                c.say("The " + result.weapon().name() + " is knocked from " + owner.name() + "'s grip.");
                return ExitCode.SUCCESS;
            }
            c.say("The blow glances off " + owner.name() + "'s weapon.");
            return ExitCode.MISS;
        }

        Entity target = VerbHelpers.targetEntity(resolved).orElseThrow();
        BodyPart part = VerbHelpers.aimPart(c.inv, resolved).orElseThrow();
        String power = c.inv.flag("power") == null ? "normal" : c.inv.flag("power");
        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                c.world.dice(), c.world, c.thrall, target, part, power, c.inv.hasFlag("force")
        );
        if (!result.hit()) {
            c.say("Strike at " + target.name() + "'s " + part.path + " — MISS.");
            return ExitCode.MISS;
        }
        c.say(String.format(
                "%s strikes %s's %s. %d dmg.",
                result.weapon() == null ? "Bare limb" : result.weapon().name(),
                target.name(), part.path, result.damage()
        ));
        if (result.killed()) {
            c.say(target.name() + " falls.");
            return ExitCode.SUCCESS;
        }
        return ExitCode.PARTIAL;
    }
}