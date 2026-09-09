package com.archon.verb;

import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.Thrall;

public final class StatusVerb extends FreeVerb {
    @Override public String name() { return "status"; }
    @Override public String help() { return "status — thrall state. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        Thrall t = c.thrall;
        c.say("HP " + t.hp + "/" + t.maxHp + "  tags " + t.tags
                + "\n  hand/right: " + slot(t, EquipmentSlot.HAND_RIGHT)
                + "\n  hand/left : " + slot(t, EquipmentSlot.HAND_LEFT)
                + "\n  pack (" + t.inventory().pack().size() + "/" + Inventory.PACK_MAX + "): " + t.inventory().pack()
                + "\n  nocked: " + t.nocked);
        return ExitCode.SUCCESS;
    }

    private String slot(Thrall t, EquipmentSlot s) {
        Item i = t.inventory().getEquipped(s);
        return i == null ? "empty" : i.name;
    }
}