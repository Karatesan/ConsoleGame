package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Tag;
import com.archon.model.Vec2;

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

        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        if (resolved == null) {
            c.say("cannot resolve " + c.inv.arg(0));
            return ExitCode.BLOCKED;
        }

        if (resolved instanceof Resolved.Entity entity) {
            if (!entity.has(Tag.FLAMMABLE)) {
                c.say(entity.name() + " will not catch.");
                return ExitCode.MISS;
            }

            entity.model().ignite();
            c.say(entity.name() + " catches fire.");
            return ExitCode.SUCCESS;
        }

        if (resolved instanceof Resolved.Tile tile) {
            Vec2 at = tile.pos();
            if (!tile.has(Tag.OIL) && !tile.has(Tag.FLAMMABLE)) {
                c.say("nothing to burn at " + at);
                return ExitCode.MISS;
            }

            tile.model().applyTag(Tag.BURNING);

            var occupantModel = c.world.entityAt(at);
            if (occupantModel != null) {
                Resolved.Entity occupant = new Resolved.Entity(occupantModel);
                if (occupant.has(Tag.FLAMMABLE)) {
                    occupant.model().ignite();
                }
            }

            c.say("Fire takes hold at " + at + ".");
            return ExitCode.SUCCESS;
        }

        c.say("cannot ignite that");
        return ExitCode.BLOCKED;
    }

    private static boolean hasFlame(VerbContext c) {
        return c.thrall.inventory().equipment().values().stream()
                .anyMatch(item -> item != null && item.has(Tag.LIT));
    }
}