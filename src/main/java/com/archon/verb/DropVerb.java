package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Item;

public final class DropVerb implements Verb {
    @Override public String name() { return "drop"; }
    @Override public String help() { return "drop <item> — put on current tile. 1 AP."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override public Check validateStructural(VerbContext c) {
        return c.inv.arg(0) == null && !c.acceptsPipedMaterial()
                ? Check.invalid("drop what?", null)
                : Check.ok();
    }

    @Override public ExitCode execute(VerbContext c) {
        ItemInput input = ItemInput.select(c, 0);
        if (input instanceof ItemInput.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }
        Item item = ((ItemInput.Selected) input).target().item();
        if (!c.thrall.inventory().remove(item)) {
            return VerbTargets.reject(c, Check.blocked("not carrying that item", null));
        }
        c.world.map().placeGroundItem(c.thrall.pos(), item);
        c.say("Thrall drops " + item.name() + ".");
        return ExitCode.SUCCESS;
    }
}