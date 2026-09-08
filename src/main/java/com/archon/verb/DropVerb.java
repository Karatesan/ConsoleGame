package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Item;

public final class DropVerb implements Verb {
    @Override public String name() { return "drop"; }
    @Override public String help() { return "drop <item> — put on current tile. 1 AP."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null && !c.acceptsPipedMaterial()) return Check.invalid("drop what?", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Item item = c.itemFromMaterialOrArg(0);
        if (item == null) { c.say("not carrying that"); return ExitCode.BLOCKED; }
        c.thrall.pack.remove(item);
        c.thrall.slots.replaceAll((k, v) -> v == item ? null : v);
        c.world.tile(c.thrall.pos).ground.add(item);
        c.say("Thrall drops " + item.name + ".");
        return ExitCode.SUCCESS;
    }
}
