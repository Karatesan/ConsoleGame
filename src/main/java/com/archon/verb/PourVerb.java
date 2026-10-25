// src/main/java/com/archon/verb/PourVerb.java
package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.Substances;

public final class PourVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkDelivery();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Vec2 at = c.selectDeliveryPos(false);
        if (at == null) return ExitCode.BLOCKED;

        Tag substance;
        if (c.materialIn instanceof Material.OfSubstance(Tag sub)) {
            substance = sub;
            if (substance != null) {
                Substances.spill(c.world, at, substance);
            }
        } else {
            Resolved.OnItem selected = c.selectCarriedItem(0);
            if (selected == null) return ExitCode.BLOCKED;
            substance = Substances.pour(c.world, at, selected.item());
        }

        if (substance == null) {
            return c.rejectBlocked("nothing pourable");
        }
        c.describeSpill(at, substance);
        return ExitCode.SUCCESS;
    }
}