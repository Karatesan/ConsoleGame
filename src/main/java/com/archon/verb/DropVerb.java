package com.archon.verb;

import com.archon.model.Item;

public final class DropVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.inv.arg(0) == null && !c.acceptsPipedMaterial()
                ? Check.invalid("drop what?", null)
                : Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
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