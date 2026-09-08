package com.archon.verb;

import com.archon.model.Item;
import com.archon.model.Thrall;

public final class StatusVerb extends FreeVerb {
    @Override public String name() { return "status"; }
    @Override public String help() { return "status — thrall state. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        Thrall t = c.thrall;
        c.say("HP " + t.hp + "/" + t.maxHp + "  tags " + t.tags
                + "\n  hand/right: " + slot(t, "hand/right")
                + "\n  hand/left : " + slot(t, "hand/left")
                + "\n  pack (" + t.pack.size() + "/" + Thrall.PACK_MAX + "): " + t.pack
                + "\n  nocked: " + t.nocked);
        return ExitCode.SUCCESS;
    }

    private String slot(Thrall t, String s) {
        Item i = t.slots.get(s);
        return i == null ? "empty" : i.name;
    }
}
