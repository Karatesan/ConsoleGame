package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;

public final class IgniteVerb implements Verb {
    @Override public String name() { return "ignite"; }
    @Override public String help() { return "ignite <target> — 1 AP. Requires a lit source in hand."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) return Check.invalid("ignite what?", null);
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        if (!hasFlame(c)) return Check.blocked("no lit source in hand", "wield a torch first");
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (!hasFlame(c)) { c.say("no flame to hand"); return ExitCode.BLOCKED; }
        Resolved r = VerbHelpers.resolve(c, c.inv.arg(0));
        if (r == null) { c.say("cannot resolve " + c.inv.arg(0)); return ExitCode.BLOCKED; }
        if (r instanceof Resolved.OnEntity oe) {
            Entity e = oe.entity();
            if (!e.has(Tag.FLAMMABLE)) { c.say(e.name + " will not catch."); return ExitCode.MISS; }
            e.tags.add(Tag.BURNING);
            c.say(e.name + " catches fire.");
            return ExitCode.SUCCESS;
        }
        Vec2 at = ((Resolved.OnTile) r).pos();
        World.Tile t = c.world.tile(at);
        if (!t.has(Tag.OIL) && !t.has(Tag.FLAMMABLE)) { c.say("nothing to burn at " + at); return ExitCode.MISS; }
        t.tags.add(Tag.BURNING);
        Entity occupant = c.world.entityAt(at);
        if (occupant != null && occupant.has(Tag.FLAMMABLE)) occupant.tags.add(Tag.BURNING);
        c.say("Fire takes hold at " + at + ".");
        return ExitCode.SUCCESS;
    }

    private static boolean hasFlame(VerbContext c) {
        return c.thrall.slots.values().stream().anyMatch(i -> i != null && i.has(Tag.LIT));
    }
}
