package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Item;
import com.archon.model.Vec2;

public final class ThrowVerb implements Verb {
    @Override public String name() { return "throw"; }
    @Override public String help() { return "throw <item> <target> — 2 AP. Accepts material from a pipe."; }
    @Override public int apCost(Ast.Invocation inv) { return 2; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        boolean piped = c.acceptsPipedMaterial();
        int need = piped ? 1 : 2;
        if (c.inv.args().size() < need)
            return Check.invalid("throw needs " + (piped ? "a target" : "an item and a target"),
                    "e.g. take /pack/flask_oil | throw o1");
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        boolean piped = c.materialIn instanceof Material.OfItem;
        Item item = piped ? ((Material.OfItem) c.materialIn).item() : c.thrall.findInPack(c.inv.arg(0));
        String targetArg = piped ? c.inv.arg(0) : c.inv.arg(1);
        if (item == null) { c.say("no such item to throw"); return ExitCode.BLOCKED; }

        Resolved r = VerbHelpers.resolve(c, targetArg);
        if (r == null) { c.say("cannot resolve " + targetArg); return ExitCode.BLOCKED; }
        Vec2 at = switch (r) {
            case Resolved.OnEntity oe -> oe.entity().pos;
            case Resolved.OnTile ot -> ot.pos();
            case Resolved.OnItem ignored -> c.thrall.pos;
        };
        c.thrall.inventory().removeFromPack(item);
        c.say("Flask arcs toward " + at + " and shatters.");
        if (item.substance != null) VerbHelpers.spill(c, at, item.substance);
        return ExitCode.SUCCESS;
    }
}
