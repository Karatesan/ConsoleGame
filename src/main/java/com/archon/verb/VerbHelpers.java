package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.SimulationSystem;
import com.archon.system.spatial.SpatialService;

import java.util.List;
import java.util.Optional;

public final class VerbHelpers {
    private VerbHelpers() {}

    public static Resolved resolve(ValidationContext c, String raw) {
        return Resolved.resolve(Address.parse(raw), c.world);
    }

    public static Optional<Entity> targetEntity(Resolved resolved) {
        if (resolved instanceof Resolved.OnEntity target) {
            return Optional.of(target.entity());
        }
        if (resolved instanceof Resolved.OnBody target) {
            return Optional.of(target.actor());
        }
        return Optional.empty();
    }

    public static Optional<BodyPart> aimPart(Ast.Invocation inv, Resolved resolved) {
        String aim = inv.flag("aim");
        if (aim != null) {
            return Optional.ofNullable(BodyPart.parse(aim));
        }
        return Optional.of(resolved instanceof Resolved.OnBody body ? body.part() : BodyPart.TORSO);
    }

    public static String soleAdjacentHostile(ValidationContext c) {
        List<Entity> hostiles = SpatialService.hostilesAdjacentTo(c.world, c.thrall.pos());
        return hostiles.size() == 1 ? hostiles.getFirst().id() : null;
    }

    public static void spill(VerbContext c, Vec2 at, Tag substance) {
        SimulationSystem.spill(c.world, at, substance);
        c.say(substance.name().toLowerCase() + " spreads across " + at + ".");
    }
}