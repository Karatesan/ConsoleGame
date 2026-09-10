package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;

import java.util.List;

public final class DropVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "drop",
            "Drop item onto current tile",
            "ITEMS & PIPELINE",
            "drop [item]",
            "1 AP (Accepts material)",
            "Drops the specified item (or material piped in from previous stage) from the thrall's\n"
                    + "hands or pack onto the current tile.",
            List.of(
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            true,
            false,
            List.of(
                    "drop blade",
                    "take /pack/stone | drop"
            )
    );

    @Override public String name() { return "drop"; }
    @Override public VerbDoc doc() { return DOC; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null && !c.acceptsPipedMaterial()) return Check.invalid("drop what?", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Item item = c.itemFromMaterialOrArg(0);
        if (item == null) { c.say("not carrying that"); return ExitCode.BLOCKED; }
        c.thrall.inventory().removeFromPack(item);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (c.thrall.inventory().getEquipped(slot) == item) {
                c.thrall.inventory().equip(slot, null);
            }
        }
        c.world.tile(c.thrall.pos).ground.add(item);
        c.say("Thrall drops " + item.name + ".");
        return ExitCode.SUCCESS;
    }
}
