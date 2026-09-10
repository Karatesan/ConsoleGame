package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;

import java.util.List;

public final class WieldVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "wield",
            "Equip item into hand slot",
            "ITEMS & PIPELINE",
            "wield [item]",
            "1 AP (Accepts material)",
            "Equips an item from the thrall's pack (or material piped in) into an empty hand slot\n"
                    + "(right hand preferred, left hand secondary). Fails if both hands are occupied.",
            List.of(
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            true,
            false,
            List.of(
                    "wield sword",
                    "take /pack/torch | wield"
            )
    );

    @Override public String name() { return "wield"; }
    @Override public VerbDoc doc() { return DOC; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null && !c.acceptsPipedMaterial()) return Check.invalid("wield what?", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Item item = c.itemFromMaterialOrArg(0);
        if (item == null) { c.say("no such item"); return ExitCode.BLOCKED; }
        EquipmentSlot slot = c.thrall.inventory().getEquipped(EquipmentSlot.HAND_RIGHT) == null
                ? EquipmentSlot.HAND_RIGHT
                : EquipmentSlot.HAND_LEFT;
        if (c.thrall.inventory().getEquipped(slot) != null) { c.say("both hands full"); return ExitCode.BLOCKED; }
        if (c.thrall.inventory().pack().contains(item)) {
            c.thrall.inventory().equipFromPack(item, slot);
        } else {
            c.thrall.inventory().equip(slot, item);
        }
        c.say("Thrall grips " + item.name + " (" + slot.path + ").");
        return ExitCode.SUCCESS;
    }
}
