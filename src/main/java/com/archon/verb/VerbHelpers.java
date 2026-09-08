package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;

import java.util.List;

/** Common parsing, targeting, and environmental helper methods for Verbs. */
public final class VerbHelpers {

    private VerbHelpers() {}

    public static Resolved resolve(VerbContext c, String raw) {
        return Resolved.resolve(Address.parse(raw), c.world);
    }

    public static Entity targetEntity(VerbContext c, String raw) {
        Resolved r = resolve(c, raw);
        return (r instanceof Resolved.OnEntity oe) ? oe.entity() : null;
    }

    public static BodyPart aimPart(Ast.Invocation inv, String targetArg) {
        String aim = inv.flag("aim");
        if (aim != null) {
            BodyPart p = BodyPart.parse(aim);
            if (p != null) return p;
        }
        if (targetArg != null && targetArg.contains("/")) {
            BodyPart p = BodyPart.parse(targetArg.substring(targetArg.indexOf('/') + 1));
            if (p != null) return p;
        }
        return BodyPart.TORSO;
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
        List<Entity> adj = c.world.hostilesAdjacentTo(c.thrall.pos);
        return adj.size() == 1 ? adj.get(0).id : null;
    }

    public static void spill(VerbContext c, Vec2 at, Tag substance) {
        World.Tile t = c.world.tile(at);
        if (t == null) return;
        t.tags.add(Tag.LIQUID);
        t.tags.add(substance);
        if (substance == Tag.OIL) t.tags.add(Tag.FLAMMABLE);
        if (substance == Tag.WATER) t.tags.add(Tag.CONDUCTIVE);
        Entity occ = c.world.entityAt(at);
        if (occ != null && substance == Tag.OIL) occ.tags.add(Tag.FLAMMABLE);
        c.say(substance.name().toLowerCase() + " spreads across " + at + ".");
    }
}
