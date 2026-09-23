package com.archon.verb;

import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.Substances;

public final class PourVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.delivery(c);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        DeliveryTarget destination = DeliveryTarget.select(c, DeliveryTarget.Mode.POUR);
        if (destination instanceof DeliveryTarget.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }
        Vec2 at = ((DeliveryTarget.Selected) destination).pos();

        Tag substance;
        if (c.materialIn instanceof Material.OfSubstance material) {
            substance = material.substance();
            if (substance != null) {
                Substances.spill(c.world, at, substance);
            }
        } else {
            ItemInput input = ItemInput.select(c, 0);
            if (input instanceof ItemInput.Rejected rejected) {
                return VerbTargets.reject(c, rejected.check());
            }
            substance = Substances.pour(c.world, at, ((ItemInput.Selected) input).target().item());
        }

        if (substance == null) {
            return VerbTargets.reject(c, Check.blocked("nothing pourable", null));
        }
        VerbHelpers.describeSpill(c, at, substance);
        return ExitCode.SUCCESS;
    }
}