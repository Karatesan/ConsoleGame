package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Item;

public final class WieldVerb implements Verb {
    @Override public String name() { return "wield"; }
    @Override public String help() { return "wield <item> — equip to hand. 1 AP. Accepts material."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null && !c.acceptsPipedMaterial()) return Check.invalid("wield what?", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Item item = c.itemFromMaterialOrArg(0);
        if (item == null) { c.say("no such item"); return ExitCode.BLOCKED; }
        String slot = c.thrall.slots.get("hand/right") == null ? "hand/right" : "hand/left";
        if (c.thrall.slots.get(slot) != null) { c.say("both hands full"); return ExitCode.BLOCKED; }
        c.thrall.pack.remove(item);
        c.thrall.slots.put(slot, item);
        c.say("Thrall grips " + item.name + " (" + slot + ").");
        return ExitCode.SUCCESS;
    }
}
