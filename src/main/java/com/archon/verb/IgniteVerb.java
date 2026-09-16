package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.spatial.SpatialService;

public final class IgniteVerb implements Verb {
    @Override
    public String name() {
        return "ignite";
    }

    @Override
    public String help() {
        return "ignite <target> — 1 AP. Requires a lit source in hand.";
    }

    @Override
    public int apCost(Ast.Invocation inv) {
        return 1;
    }

    @Override
    public boolean acceptsMaterial() {
        return true;
    }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) {
            return Check.invalid("ignite what?", null);
        }
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        if (!hasFlame(c)) {
            return Check.blocked("no lit source in hand", "wield a torch first");
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (!hasFlame(c)) {
            c.say("no flame to hand");
            return ExitCode.BLOCKED;
        }

        String argument = c.inv.arg(0);
        Resolution resolution = VerbHelpers.resolve(c, argument);
        Resolved target = VerbHelpers.found(resolution);
        if (target == null) {
            c.say("cannot resolve " + argument + ": " + VerbHelpers.failureDetail(resolution));
            return ExitCode.BLOCKED;
        }

        if (target instanceof Resolved.EntityTarget entityTarget) {
            return igniteEntity(c, entityTarget.entity());
        }

        if (target instanceof Resolved.BodyTarget bodyTarget) {
            return igniteEntity(c, bodyTarget.entity());
        }

        if (target instanceof Resolved.TileTarget tileTarget) {
            if (tileTarget.layer() == GameMap.Layer.CEILING) {
                c.say("cannot ignite " + argument + ": ceiling targets are unsupported");
                return ExitCode.BLOCKED;
            }

            if (tileTarget.layer() == GameMap.Layer.FLOOR) {
                return igniteTile(c, tileTarget.pos());
            }
        }

        c.say("cannot ignite " + argument);
        return ExitCode.BLOCKED;
    }

    private static ExitCode igniteTile(VerbContext c, Vec2 at) {
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

    private static ExitCode igniteEntity(VerbContext c, Entity entity) {
        if (!entity.has(Tag.FLAMMABLE)) {
            c.say(entity.name() + " will not catch.");
            return ExitCode.MISS;
        }

        entity.ignite();
        c.say(entity.name() + " catches fire.");
        return ExitCode.SUCCESS;
    }

    private static boolean hasFlame(VerbContext c) {
        return c.thrall.inventory().equipment().values().stream()
                .anyMatch(item -> item != null && item.has(Tag.LIT));
    }
}