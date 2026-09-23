package com.archon.verb;

import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.Substances;
import com.archon.system.item.ItemMoves;

public final class ThrowVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.delivery(c);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        DeliveryTarget destination = DeliveryTarget.select(c, DeliveryTarget.Mode.THROW);
        if (destination instanceof DeliveryTarget.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }

        ItemInput input = ItemInput.select(c, 0);
        if (input instanceof ItemInput.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }
        var selected = ((ItemInput.Selected) input).target();
        var result = ItemMoves.remove(c.world.map(), selected.item(), selected.location());
        if (result != ItemMoves.Result.SUCCESS) {
            return VerbTargets.reject(c, Check.blocked(result.reason(), null));
        }

        Vec2 at = ((DeliveryTarget.Selected) destination).pos();
        c.say(selected.item().name() + " arcs toward " + at + " and shatters.");
        Tag substance = Substances.pour(c.world, at, selected.item());
        if (substance != null) {
            VerbHelpers.describeSpill(c, at, substance);
        }
        return ExitCode.SUCCESS;
    }
}