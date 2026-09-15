package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;

public final class WieldVerb implements Verb {
    @Override public String name() { return "wield"; }
    @Override public String help() { return "wield <item> — equip to hand. 1 AP. Accepts material."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null && !c.acceptsPipedMaterial()) return Check.invalid("wield what?", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Item item = c.itemFromMaterialOrArg(0);
        if (item == null) {
            c.say("no such item");
            return ExitCode.BLOCKED;
        }

        EquipmentSlot slot = c.thrall.inventory().equipped(EquipmentSlot.HAND_RIGHT) == null
                ? EquipmentSlot.HAND_RIGHT
                : EquipmentSlot.HAND_LEFT;
        if (c.thrall.inventory().equipped(slot) != null) {
            c.say("both hands full");
            return ExitCode.BLOCKED;
        }

        if (!c.thrall.inventory().pack().contains(item) && !c.thrall.inventory().addToPack(item)) {
            c.say("cannot carry " + item.name());
            return ExitCode.BLOCKED;
        }

        if (!c.thrall.inventory().equipFromPack(item, slot)) {
            c.say("cannot wield " + item.name());
            return ExitCode.BLOCKED;
        }

        c.say("Thrall grips " + item.name() + " (" + slot.path + ").");
        return ExitCode.SUCCESS;
    }
}