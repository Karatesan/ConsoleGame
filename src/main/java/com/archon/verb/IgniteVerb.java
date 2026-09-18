package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.*;
import com.archon.system.spatial.SpatialService;

public final class IgniteVerb implements Verb {
    @Override public String name() { return "ignite"; }
    @Override public String help() {
        return "ignite <entity|tile> — 1 AP. Requires a lit source in hand.";
    }
    @Override public int apCost(Ast.Invocation inv) { return 1; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.acceptsPipedMaterial()) {
            return Check.invalid("ignite does not consume piped material", "sequence it with &&");
        }
        return c.inv.arg(0) == null ? Check.invalid("ignite what?", null) : Check.ok();
    }

    private Check check(VerbContext c, Resolved resolved) {
        Check syntax = validateStructural(c);
        if (!syntax.valid()) return syntax;

        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) return resolution;
        if (!(resolved instanceof Resolved.OnEntity
                || resolved instanceof Resolved.OnTile tile
                && tile.layer() != Address.Layer.CEILING)) {
            return Check.invalid("ignite needs a whole entity or floor tile", null);
        }
        Check reach = VerbTargets.reachable(c, (Resolved.Target) resolved);
        if (!reach.valid()) return reach;
        return hasFlame(c)
                ? Check.ok()
                : Check.blocked("no lit source in hand", "wield a torch first");
    }

    @Override
    public Check validateState(VerbContext c) {
        return check(c, VerbHelpers.resolve(c, c.inv.arg(0)));
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        Check check = check(c, resolved);
        if (!check.valid()) return VerbTargets.reject(c, check);

        if (resolved instanceof Resolved.OnEntity target) {
            Entity entity = target.entity();
            if (!entity.has(Tag.FLAMMABLE)) {
                c.say(entity.name() + " will not catch.");
                return ExitCode.MISS;
            }
            entity.ignite();
            c.say(entity.name() + " catches fire.");
            return ExitCode.SUCCESS;
        }

        Vec2 at = ((Resolved.OnTile) resolved).pos();
        GameMap.Tile tile = c.world.tile(at);
        if (!tile.has(Tag.OIL) && !tile.has(Tag.FLAMMABLE)) {
            c.say("nothing to burn at " + at);
            return ExitCode.MISS;
        }

        c.world.map().addTag(at, Tag.BURNING);
        Entity occupant = SpatialService.entityAt(c.world, at);
        if (occupant != null && occupant.has(Tag.FLAMMABLE)) {
            occupant.ignite();
        }
        c.say("Fire takes hold at " + at + ".");
        return ExitCode.SUCCESS;
    }

    private static boolean hasFlame(VerbContext c) {
        Item left = c.thrall.inventory().equipped(EquipmentSlot.HAND_LEFT);
        Item right = c.thrall.inventory().equipped(EquipmentSlot.HAND_RIGHT);
        return (left != null && left.has(Tag.LIT))
                || (right != null && right.has(Tag.LIT));
    }
}