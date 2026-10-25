// src/main/java/com/archon/verb/ThrowVerb.java
package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.Substances;
import com.archon.system.item.ItemMoves;

public final class ThrowVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkDelivery();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Vec2 at = c.selectDeliveryPos(true);
        if (at == null) return ExitCode.BLOCKED;

        Resolved.OnItem selected = c.selectCarriedItem(0);
        if (selected == null) return ExitCode.BLOCKED;

        var result = ItemMoves.remove(c.world.map(), selected.item(), selected.location());
        if (result != ItemMoves.Result.SUCCESS) {
            return c.rejectBlocked(result.reason());
        }

        c.say(selected.item().name() + " arcs toward " + at + " and shatters.");
        Tag substance = Substances.pour(c.world, at, selected.item());
        if (substance != null) {
            c.describeSpill(at, substance);
        }
        return ExitCode.SUCCESS;
    }
}