package com.archon.verb;

import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.Thrall;

public final class StatusVerb extends FreeVerb {
    @Override
    public String name() {
        return "status";
    }

    @Override
    public String help() {
        return "status — thrall state. 0 AP.";
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Thrall thrall = c.thrall;
        Inventory inventory = thrall.inventory();

        c.say("HP " + thrall.hp() + "/" + thrall.maxHp() + "  tags " + thrall.tags()
                + "\n  hand/right: " + slot(inventory, EquipmentSlot.HAND_RIGHT)
                + "\n  hand/left : " + slot(inventory, EquipmentSlot.HAND_LEFT)
                + "\n  pack (" + inventory.pack().size() + "/" + Inventory.PACK_MAX + "): " + inventory.pack()
                + "\n  nocked: " + (thrall.isNocked() ? "NOCKED" : "READY"));

        return ExitCode.SUCCESS;
    }

    private String slot(Inventory inventory, EquipmentSlot slot) {
        Item item = inventory.equipped(slot);
        return item == null ? "empty" : item.name();
    }
}