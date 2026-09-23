package com.archon.verb;

import com.archon.system.item.ItemMoves;

public final class DropVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.item(c, 0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        ItemInput input = ItemInput.select(c, 0);
        if (input instanceof ItemInput.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }
        var selected = ((ItemInput.Selected) input).target();
        var result = ItemMoves.drop(c.world.map(), selected.item(), selected.location());
        if (result != ItemMoves.Result.SUCCESS) {
            return VerbTargets.reject(c, Check.blocked(result.reason(), null));
        }
        c.say("Thrall drops " + selected.item().name() + ".");
        return ExitCode.SUCCESS;
    }
}