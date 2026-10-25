package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.*;
import com.archon.system.combat.CombatEngine;

import java.util.Locale;
import java.util.Set;

public final class StrikeVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        String power = c.inv.flag("power");
        if (power != null && !Set.of("light", "normal", "heavy").contains(power.toLowerCase(Locale.ROOT))) {
            return Check.invalid("unknown power \"" + power + "\"", "light, normal, heavy");
        }
        return c.checkAimedAddress(true);
    }

    private boolean isHandTarget(Resolved resolved) {
        return resolved instanceof Resolved.ItemTarget target
                && target.location() instanceof ItemLocation.Equipment equipment
                && (equipment.slot() == EquipmentSlot.HAND_LEFT || equipment.slot() == EquipmentSlot.HAND_RIGHT);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0) != null ? c.inv.arg(0) : c.soleAdjacentHostile();
        if (address == null) {
            return c.rejectBlocked("no single adjacent target; name one explicitly");
        }

        Resolved.Target target = c.resolveTarget(address);
        if (target == null) return ExitCode.BLOCKED;

        if (!(target instanceof Resolved.OnEntity || target instanceof Resolved.OnBody || isHandTarget(target))) {
            return c.rejectBlocked("strike needs an entity, body part, or hand slot");
        }
        if (isHandTarget(target) && target instanceof Resolved.OnLocation) {
            return c.rejectBlocked("nothing in that hand to disarm");
        }
        if (!c.checkReachable(target)) return ExitCode.BLOCKED;

        if (target instanceof Resolved.OnItem item
                && item.location() instanceof ItemLocation.Equipment equipment) {
            Actor owner = equipment.owner();
            var result = CombatEngine.attemptDisarm(c.world.dice(), c.world, owner, equipment.slot());
            if (result.success()) {
                c.say("The " + result.weapon().name() + " is knocked from " + owner.name() + "'s grip.");
                return ExitCode.SUCCESS;
            }
            c.say("The blow glances off " + owner.name() + "'s weapon.");
            return ExitCode.MISS;
        }

        Entity entity = target.targetEntity().orElseThrow();
        BodyPart part = c.aimPart(target);
        String power = c.inv.flag("power") == null ? "normal" : c.inv.flag("power").toLowerCase(Locale.ROOT);
        var result = CombatEngine.resolveMelee(
                c.world.dice(), c.world, c.thrall, entity, part, power, c.inv.hasFlag("force")
        );
        if (!result.hit()) {
            c.say("Strike at " + entity.name() + "'s " + part.path + " — MISS.");
            return ExitCode.MISS;
        }
        c.say(String.format(
                "%s strikes %s's %s. %d dmg.",
                result.weapon() == null ? "Bare limb" : result.weapon().name(),
                entity.name(), part.path, result.damage()
        ));
        if (result.killed()) {
            c.say(entity.name() + " falls.");
        }
        return ExitCode.SUCCESS;
    }
}