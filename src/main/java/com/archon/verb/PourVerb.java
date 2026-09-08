package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;

public final class PourVerb implements Verb {
    @Override public String name() { return "pour"; }
    @Override public String help() { return "pour <liquid> <tile> — 1 AP. Accepts material."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }
    @Override public boolean producesMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        boolean piped = c.acceptsPipedMaterial();
        if (!piped && c.inv.args().size() < 2) return Check.invalid("pour needs a liquid and a tile", null);
        if (piped && c.inv.arg(0) == null) return Check.invalid("pour needs a destination tile", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Tag substance;
        String targetArg;
        if (c.materialIn instanceof Material.OfSubstance os) {
            substance = os.substance();
            targetArg = c.inv.arg(0);
        } else if (c.materialIn instanceof Material.OfItem oi) {
            substance = oi.item().substance;
            targetArg = c.inv.arg(0);
        } else {
            Item it = c.thrall.findInPack(c.inv.arg(0));
            if (it == null || it.substance == null) { c.say("nothing pourable"); return ExitCode.BLOCKED; }
            substance = it.substance;
            targetArg = c.inv.arg(1);
            c.thrall.pack.remove(it);
        }
        if (substance == null) { c.say("nothing pourable"); return ExitCode.BLOCKED; }
        Resolved r = VerbHelpers.resolve(c, targetArg);
        if (r == null) { c.say("cannot resolve " + targetArg); return ExitCode.BLOCKED; }
        Vec2 at = (r instanceof Resolved.OnEntity oe) ? oe.entity().pos
                : (r instanceof Resolved.OnTile ot) ? ot.pos() : c.thrall.pos;
        VerbHelpers.spill(c, at, substance);
        c.materialOut = new Material.OfSubstance(substance);
        return ExitCode.SUCCESS;
    }
}
