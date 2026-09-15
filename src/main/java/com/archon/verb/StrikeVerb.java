package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.system.combat.CombatEngine;

import java.util.List;

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
        String target = c.inv.arg(0);

        if (target == null) {
            List<Entity> adjacent =
                    c.world.hostilesAdjacentTo(c.thrall.getPos());

            if (adjacent.isEmpty()) {
                return Check.blocked("nothing adjacent to strike", null);
            }

            if (adjacent.size() > 1) {
                return Check.invalid(
                        "ambiguous target: "
                                + adjacent.stream().map(Entity::getId).toList(),
                        "name one explicitly"
                );
            }

            target = adjacent.get(0).getId();
        }

        if (aimPartInvalid(c, target)) {
            return Check.invalid(
                    "no such hit location on " + target,
                    "valid: head, torso, arm.l, arm.r, legs"
            );
        }

        Resolved resolved = VerbHelpers.resolve(c, target);
        if (resolved == null) {
            return Check.invalid(
                    "unknown target \"" + target + "\"",
                    "try: scan"
            );
        }

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
            if (!isHeldItem(onItem)) {
                return Check.blocked("target is not a held item", null);
            }

            entity = heldItemOwner(c, onItem);
        } else {
            return Check.blocked("target not present", null);
        }

        if (entity == null) {
            return Check.blocked("target not present", null);
        }

        // Both branches of the original reach expression returned 1.
        int reach = 1;
        if (entity.getPos().chebyshev(c.thrall.getPos()) > reach) {
            return Check.blocked(
                    entity.getId() + " out of reach",
                    "step closer first"
            );
        }

        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String targetArg = targetArgument(c);
        if (targetArg == null) {
            c.say("nothing to strike");
            return ExitCode.BLOCKED;
        }

        Resolved resolved = VerbHelpers.resolve(c, targetArg);
        if (resolved == null) {
            c.say(targetArg + " is no longer there");
            return ExitCode.BLOCKED;
        }

        // Striking a held item is a disarm attempt.
        if (resolved instanceof Resolved.OnItem onItem) {
            if (!isHeldItem(onItem)) {
                c.say("target is not a held item");
                return ExitCode.BLOCKED;
            }

            Entity owner = heldItemOwner(c, onItem);
            if (owner == null) {
                c.say("nothing to disarm");
                return ExitCode.BLOCKED;
            }

            CombatEngine.DisarmResult disarm = CombatEngine.attemptDisarm(
                    c.world.dice,
                    c.world,
                    owner
            );

            // CombatEngine checks Actor.mainHand() or Entity.getHeld().
            if (disarm.weapon() == null) {
                c.say("nothing to disarm");
                return ExitCode.BLOCKED;
            }

            if (disarm.success()) {
                c.say(
                        "The " + disarm.weapon().name
                                + " is knocked from "
                                + owner.getName() + "'s grip."
                );
                return ExitCode.SUCCESS;
            }

            c.say("The blow glances off " + owner.getName() + "'s weapon.");
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
                    "Strike at " + entity.getName()
                            + "'s " + part.path + " — MISS."
            );
            return ExitCode.MISS;
        }

        c.say(String.format(
                "%s strikes %s's %s. %d dmg.",
                result.weapon() == null ? "Bare limb" : result.weapon().name,
                entity.getName(),
                part.path,
                result.damage()
        ));

        if (result.killed()) {
            c.say(entity.getName() + " falls.");
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

    private boolean isHeldItem(Resolved.OnItem item) {
        return item.container() != null
                && item.container().contains("hand");
    }

    private Entity heldItemOwner(VerbContext c, Resolved.OnItem item) {
        String ownerId = item.container().split("/", 2)[0];
        return c.world.get(ownerId);
    }
}