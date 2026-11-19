// src/main/java/com/archon/verb/PourVerb.java
package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.Substances;

import java.util.Locale;

public final class PourVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkDelivery();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target target = c.selectDeliveryTarget(false);
        if (target == null) return ExitCode.BLOCKED;
        Vec2 at = target.pos();
        boolean floorOnly = VerbContext.isFloorDelivery(target);

        Tag substance;
        if (c.materialIn instanceof Material.OfSubstance(Tag sub)) {
            if (sub != Tag.WATER && sub != Tag.OIL && sub != Tag.ACID) {
                return c.rejectBlocked("cannot pour " + sub.name().toLowerCase(Locale.ROOT));
            }
            substance = sub;
            Substances.spill(c.world, at, substance, floorOnly, c.log);
        } else {
            Resolved.OnItem selected = c.selectCarriedItem(0);
            if (selected == null) return ExitCode.BLOCKED;
            substance = Substances.pour(c.world, at, selected.item(), floorOnly, c.log);
        }

        if (substance == null) {
            return c.rejectBlocked("nothing pourable");
        }
        if (c.log.isEmpty()) c.describeSpill(at, substance);
        else c.flushLog();
        return ExitCode.SUCCESS;
    }
}