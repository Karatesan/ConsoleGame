package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolution;
import com.archon.address.Resolution.Failure;
import com.archon.address.Resolution.Found;
import com.archon.address.Resolved;
import com.archon.address.Resolved.BodyTarget;
import com.archon.address.Resolved.EntityTarget;
import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.spatial.SpatialService;

import java.util.List;

/** Common parsing, targeting, and environmental helper methods for Verbs. */
public final class VerbHelpers {

    private VerbHelpers() {}

    public static Resolution resolve(VerbContext c, String raw) {
        try {
            return Resolution.resolve(Address.parse(raw), c.world);
        } catch (IllegalArgumentException exception) {
            return new Failure(
                    Resolution.Reason.INVALID_SYNTAX,
                    exception.getMessage());
        }
    }

    public static Resolved found(Resolution resolution) {
        return resolution instanceof Found found ? found.target() : null;
    }

    public static String failureDetail(Resolution resolution) {
        return resolution instanceof Failure failure ? failure.detail() : null;
    }

    public static Entity targetEntity(VerbContext c, String raw) {
        Resolved target = found(resolve(c, raw));
        if (target instanceof EntityTarget entityTarget) {
            return entityTarget.entity();
        }
        if (target instanceof BodyTarget bodyTarget) {
            return bodyTarget.entity();
        }
        return null;
    }

    public static BodyPart aimPart(Ast.Invocation inv) {
        BodyPart aim = inv.flag("aim");
        return aim != null ? aim : BodyPart.TORSO;
    }

    public static int powerAp(Ast.Invocation inv, int base) {
        String p = inv.flag("power");
        if (p == null) return base;
        return switch (p.toLowerCase()) {
            case "light" -> 1;
            case "heavy" -> 3;
            default -> base;
        };
    }

    public static String soleAdjacentHostile(VerbContext c) {
        List<Entity> adjacentHostiles =
                SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
        return adjacentHostiles.size() == 1 ? adjacentHostiles.get(0).id() : null;
    }

    public static void spill(VerbContext c, Vec2 at, Tag substance) {
        SimulationSystem.spill(c.world, at, substance);
        c.say(substance.name().toLowerCase() + " spreads across " + at + ".");
    }
}