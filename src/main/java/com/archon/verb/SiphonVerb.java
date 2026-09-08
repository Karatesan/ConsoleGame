package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.World;

public final class SiphonVerb implements Verb {
    @Override public String name() { return "siphon"; }
    @Override public String help() { return "siphon <source> — draw liquid. 1 AP. Produces material."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean producesMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) return Check.invalid("siphon from what?", null);
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        Tag s = sourceSubstance(c, c.inv.arg(0));
        if (s == null) return Check.blocked("nothing to siphon from " + c.inv.arg(0), "try: inspect " + c.inv.arg(0));
        Entity holder = holderOf(c, c.inv.arg(0));
        if (holder != null && holder.pos.chebyshev(c.thrall.pos) > 1)
            return Check.blocked(holder.id + " out of reach", "step closer");
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Tag s = sourceSubstance(c, c.inv.arg(0));
        if (s == null) { c.say("nothing to siphon"); return ExitCode.BLOCKED; }
        c.materialOut = new Material.OfSubstance(s);
        c.say("Thrall draws " + s.name().toLowerCase() + ".");
        return ExitCode.SUCCESS;
    }

    private static Tag sourceSubstance(VerbContext c, String arg) {
        Resolved r = VerbHelpers.resolve(c, arg);
        if (r instanceof Resolved.OnItem oi && oi.item() != null) return oi.item().substance;
        if (r instanceof Resolved.OnEntity oe && oe.entity().held != null) return oe.entity().held.substance;
        if (r instanceof Resolved.OnTile ot) {
            World.Tile t = c.world.tile(ot.pos());
            if (t.has(Tag.OIL)) return Tag.OIL;
            if (t.has(Tag.WATER)) return Tag.WATER;
        }
        return null;
    }

    private static Entity holderOf(VerbContext c, String arg) {
        Address a = Address.parse(arg);
        if (a instanceof Address.EntityAddr ea) return c.world.get(ea.id());
        return null;
    }
}
