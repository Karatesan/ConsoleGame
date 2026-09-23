package com.archon.verb;

import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.system.item.ItemMoves;

public final class WieldVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return VerbSyntax.item(c, 0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        ItemInput input = ItemInput.select(c, 0);
        if (input instanceof ItemInput.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }

        var selected = ((ItemInput.Selected) input).target();
        Inventory inventory = c.thrall.inventory();
        EquipmentSlot slot = inventory.equipped(
                EquipmentSlot.HAND_RIGHT) == null ? EquipmentSlot.HAND_RIGHT : EquipmentSlot.HAND_LEFT;
        var result = ItemMoves.equip(selected.item(), selected.location(), slot);
        if (result != ItemMoves.Result.SUCCESS) {
            String reason = switch (result) {
                case NOT_PACKED -> "item is already equipped";
                case SLOT_OCCUPIED -> "both hands full";
                default -> result.reason();
            };
            return VerbTargets.reject(c, Check.blocked(reason, null));
        }

        c.say("Thrall grips " + selected.item().name() + " (" + slot.path + ").");
        return ExitCode.SUCCESS;
    }
}