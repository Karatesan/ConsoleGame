// src/main/java/com/archon/verb/StrikeVerb.java
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

    private Resolved resolveTarget(ValidationContext c) {
        String address = c.inv.arg(0) != null ? c.inv.arg(0) : c.soleAdjacentHostile();
        return address == null
                ? new Resolved.Failure(Resolved.Problem.UNKNOWN_ENTITY, "no single adjacent target; name one explicitly")
                : c.resolve(address);
    }

    private boolean isHandTarget(Resolved resolved) {
        return resolved instanceof Resolved.ItemTarget target
                && target.location() instanceof ItemLocation.Equipment equipment
                && (equipment.slot() == EquipmentSlot.HAND_LEFT || equipment.slot() == EquipmentSlot.HAND_RIGHT);
    }

    private Check check(ValidationContext c, Resolved resolved) {
        Check resolution = resolved.toCheck();
        if (!resolution.valid()) return resolution;
        if (!(resolved instanceof Resolved.OnEntity || resolved instanceof Resolved.OnBody || isHandTarget(resolved))) {
            return Check.blocked("strike needs an entity, body part, or hand slot", null);
        }
        if (isHandTarget(resolved) && resolved instanceof Resolved.OnLocation) {
            return Check.blocked("nothing in that hand to disarm", null);
        }
        return c.checkReachable((Resolved.Target) resolved);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = resolveTarget(c);
        Check check = check(c, resolved);
        if (!check.valid()) return c.reject(check);

        if (resolved instanceof Resolved.OnItem item
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

        Entity target = resolved.targetEntity().orElseThrow();
        BodyPart part = c.aimPart(resolved);
        String power = c.inv.flag("power") == null ? "normal" : c.inv.flag("power").toLowerCase(Locale.ROOT);
        var result = CombatEngine.resolveMelee(
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
        }
        return ExitCode.SUCCESS;
    }
}