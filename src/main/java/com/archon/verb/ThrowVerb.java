package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Item;
import com.archon.model.Vec2;

import java.util.List;

public final class ThrowVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "throw",
            "Hurl an item or flask at a target or tile",
            "ITEMS & PIPELINE",
            "throw [item] <target>",
            "2 AP (Accepts material)",
            "Hurls an item or flask at a target entity or tile. If piped material is received,\n"
                    + "only the destination argument is required. Shatters flasks and spills their contents.",
            List.of(
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            true,
            false,
            List.of(
                    "throw flask_oil o1",
                    "take /pack/flask_oil | throw @3,5"
            )
    );

    @Override public String name() { return "throw"; }
    @Override public VerbDoc doc() { return DOC; }
    @Override public int apCost(Ast.Invocation inv) { return 2; }

    @Override
    public Check validateStructural(VerbContext c) {
        boolean piped = c.acceptsPipedMaterial();
        int need = piped ? 1 : 2;
        if (c.inv.args().size() < need)
            return Check.invalid("throw needs " + (piped ? "a target" : "an item and a target"),
                    "e.g. take /pack/flask_oil | throw o1");
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        boolean piped = c.materialIn instanceof Material.OfItem;
        Item item = piped ? ((Material.OfItem) c.materialIn).item() : c.thrall.findInPack(c.inv.arg(0));
        String targetArg = piped ? c.inv.arg(0) : c.inv.arg(1);
        if (item == null) { c.say("no such item to throw"); return ExitCode.BLOCKED; }

        Resolved r = VerbHelpers.resolve(c, targetArg);
        if (r == null) { c.say("cannot resolve " + targetArg); return ExitCode.BLOCKED; }
        Vec2 at = switch (r) {
            case Resolved.OnEntity oe -> oe.entity().pos;
            case Resolved.OnTile ot -> ot.pos();
            case Resolved.OnItem ignored -> c.thrall.pos;
        };
        c.thrall.inventory().removeFromPack(item);
        c.say("Flask arcs toward " + at + " and shatters.");
        if (item.substance != null) VerbHelpers.spill(c, at, item.substance);
        return ExitCode.SUCCESS;
    }
}
