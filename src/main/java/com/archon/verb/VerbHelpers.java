package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.SimulationSystem;

import java.util.List;

/** Common parsing, targeting, and environmental helper methods for Verbs. */
public final class VerbHelpers {

    private VerbHelpers() {
    }

    public static Resolved resolve(VerbContext c, String raw) {
        return Resolved.resolve(Address.parse(raw), c.world);
    }

    public static Entity targetEntity(VerbContext c, String raw) {
        Resolved resolved = resolve(c, raw);
        return resolved instanceof Resolved.OnEntity onEntity ? onEntity.entity() : null;
    }

    public static BodyPart aimPart(Ast.Invocation inv, String targetArg) {
        String aim = inv.flag("aim");
        if (aim != null) {
            BodyPart part = BodyPart.parse(aim);
            if (part != null) {
                return part;
            }
        }

        if (targetArg != null && targetArg.contains("/")) {
            BodyPart part = BodyPart.parse(targetArg.substring(targetArg.indexOf('/') + 1));
            if (part != null) {
                return part;
            }
        }

        return BodyPart.TORSO;
    }

    public static int powerAp(Ast.Invocation inv, int base) {
        String power = inv.flag("power");
        if (power == null) {
            return base;
        }

        return switch (power.toLowerCase()) {
            case "light" -> 1;
            case "heavy" -> 3;
            default -> base;
        };
    }

    public static String soleAdjacentHostile(VerbContext c) {
        List<Actor> adj = c.world.hostilesAdjacentTo(c.thrall.pos());
        return adj.size() == 1 ? adj.get(0).id : null;
    }

    public static void spill(VerbContext c, Vec2 at, Tag substance) {
        SimulationSystem.spill(c.world, at, substance);
        c.say(substance.name().toLowerCase() + " spreads across " + at + ".");
    }
}