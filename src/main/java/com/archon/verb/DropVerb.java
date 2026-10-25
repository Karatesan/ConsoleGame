// src/main/java/com/archon/verb/DropVerb.java
package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.system.item.ItemMoves;

public final class DropVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkItem(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.OnItem selected = c.selectCarriedItem(0);
        if (selected == null) return ExitCode.BLOCKED;

        var result = ItemMoves.drop(c.world.map(), selected.item(), selected.location());
        if (result != ItemMoves.Result.SUCCESS) {
            return c.rejectBlocked(result.reason());
        }
        c.say("Thrall drops " + selected.item().name() + ".");
        return ExitCode.SUCCESS;
    }
}