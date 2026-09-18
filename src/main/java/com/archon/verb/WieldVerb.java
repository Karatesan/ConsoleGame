package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.ItemLocation;

public final class WieldVerb implements Verb {
    @Override public String name() { return "wield"; }
    @Override public String help() { return "wield <item> — equip from pack to hand. 1 AP. Accepts material."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        return c.inv.arg(0) == null && !c.acceptsPipedMaterial()
                ? Check.invalid("wield what?", null)
                : Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        ItemInput input = ItemInput.select(c, 0);
        if (input instanceof ItemInput.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }

        Resolved.OnItem selected = ((ItemInput.Selected) input).target();
        if (!(selected.location() instanceof ItemLocation.Pack)) {
            return VerbTargets.reject(c, Check.blocked("item is already equipped", null));
        }

        Inventory inventory = c.thrall.inventory();
        EquipmentSlot slot = inventory.equipped(EquipmentSlot.HAND_RIGHT) == null
                ? EquipmentSlot.HAND_RIGHT
                : EquipmentSlot.HAND_LEFT;
        if (inventory.equipped(slot) != null) {
            return VerbTargets.reject(c, Check.blocked("both hands full", null));
        }
        if (!inventory.equipFromPack(selected.item(), slot)) {
            return VerbTargets.reject(c, Check.blocked("item is no longer in the pack", null));
        }

        c.say("Thrall grips " + selected.item().name() + " (" + slot.path + ").");
        return ExitCode.SUCCESS;
    }
}