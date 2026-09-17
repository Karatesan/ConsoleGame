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
        String aim = c.inv.flag("aim");
        if (aim != null && BodyPart.parse(aim) == null) {
            return Check.invalid(
                    "no such hit location",
                    "valid: head, torso, arm.l, arm.r, legs"
            );
        }

        String explicitTarget = c.inv.arg(0);
        if (explicitTarget != null && VerbHelpers.resolve(c, explicitTarget) instanceof Resolution.Failure) {
            return Check.invalid("unknown target \"" + explicitTarget + "\"", "try: scan");
        }

        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        String target = targetArg(c);
        if (target == null) {
            return Check.blocked("target not present", null);
        }

        Resolution resolution = VerbHelpers.resolve(c, target);
        if (resolution instanceof Resolution.Failure) {
            return Check.blocked("target not present", null);
        }

        Resolved found = VerbHelpers.found(resolution);

        if (found instanceof Resolved.EntityTarget entityTarget) {
            return validateEntityTarget(c, entityTarget.entity());
        }

        if (found instanceof Resolved.BodyTarget bodyTarget) {
            return validateEntityTarget(c, bodyTarget.entity());
        }

        if (found instanceof Resolved.EquippedItem equippedItem
                && equippedItem.owner() instanceof Actor owner
                && isHandSlot(equippedItem.slot())) {
            return validateEntityTarget(c, owner);
        }

        if (found instanceof Resolved.EmptyEquipmentSlot emptySlot
                && emptySlot.owner() instanceof Actor
                && isHandSlot(emptySlot.slot())) {
            return Check.blocked("nothing to disarm", null);
        }

        return Check.blocked("target not present", null);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String target = targetArg(c);
        if (target == null) {
            c.say("nothing to strike");
            return ExitCode.BLOCKED;
        }

        Resolution resolution = VerbHelpers.resolve(c, target);
        if (resolution instanceof Resolution.Failure) {
            c.say(target + " is no longer there");
            return ExitCode.BLOCKED;
        }

        Resolved found = VerbHelpers.found(resolution);

        if (found instanceof Resolved.EmptyEquipmentSlot emptySlot
                && emptySlot.owner() instanceof Actor
                && isHandSlot(emptySlot.slot())) {
            c.say("nothing to disarm");
            return ExitCode.BLOCKED;
        }

        if (found instanceof Resolved.EquippedItem equippedItem
                && equippedItem.owner() instanceof Actor owner
                && isHandSlot(equippedItem.slot())) {
            CombatEngine.DisarmResult disarm = CombatEngine.attemptDisarm(c.world.dice(), c.world, owner);

            if (disarm.success()) {
                c.say("The " + disarm.weapon().name() + " is knocked from " + owner.name() + "'s grip.");
                return ExitCode.SUCCESS;
            }

            c.say("The blow glances off " + owner.name() + "'s weapon.");
            return ExitCode.MISS;
        }

        Entity entity;
        BodyPart part;

        if (found instanceof Resolved.EntityTarget entityTarget) {
            entity = entityTarget.entity();
            part = VerbHelpers.aimPart(c.inv);
        } else if (found instanceof Resolved.BodyTarget bodyTarget) {
            entity = bodyTarget.entity();
            part = c.inv.flag("aim") == null
                    ? bodyTarget.bodyPart()
                    : VerbHelpers.aimPart(c.inv);
        } else {
            c.say("target not present");
            return ExitCode.BLOCKED;
        }

        String power = c.inv.flag("power") == null ? "normal" : c.inv.flag("power");

        CombatEngine.MeleeHitResult result = CombatEngine.resolveMelee(
                c.world.dice(),
                c.world,
                c.thrall,
                entity,
                part,
                power,
                c.inv.hasFlag("force")
        );

        if (!result.hit()) {
            c.say("Strike at " + entity.name() + "'s " + part.path + " — MISS.");
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

    private boolean isHandSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HAND_LEFT || slot == EquipmentSlot.HAND_RIGHT;
    }

    private Check validateEntityTarget(VerbContext c, Entity entity) {
        if (entity.pos().chebyshev(c.thrall.pos()) > 1) {
            return Check.blocked(entity.id() + " out of reach", "step closer first");
        }

        return Check.ok();
    }

    private String targetArg(VerbContext c) {
        String target = c.inv.arg(0);
        if (target != null) {
            return target;
        }

        List<Entity> adjacent = SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
        return adjacent.size() == 1 ? adjacent.get(0).id() : null;
    }
}
