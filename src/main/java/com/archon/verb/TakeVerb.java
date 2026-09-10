package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.*;

import java.util.List;

public final class TakeVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "take",
            "Pick up item from ground, pack, or target",
            "ITEMS & PIPELINE",
            "take <address>",
            "1 AP (Produces material)",
            "Takes an item into inventory. Can take from ground (@self, @x,y), from pack\n"
                    + "(/pack/item), or attempt to snatch a weapon held by an adjacent enemy (35% chance).\n"
                    + "Produces the item as piped material for downstream pipeline verbs.",
            List.of(
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            false,
            true,
            List.of(
                    "take blade",
                    "take @self",
                    "take /pack/flask_oil | throw o1",
                    "take o1/hand/right"
            )
    );

    @Override public String name() { return "take"; }
    @Override public VerbDoc doc() { return DOC; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) return Check.invalid("take what?", null);
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        if (c.thrall.inventory().isPackFull()) return Check.blocked("pack full (" + Inventory.PACK_MAX + ")", "drop something");
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String a = c.inv.arg(0);
        Resolved r = VerbHelpers.resolve(c, a);
        Item item = null;

        if (r instanceof Resolved.OnItem oi && oi.item() != null) {
            item = oi.item();
            if ("pack".equals(oi.container())) {
                // already carried: taking it out is a valid pipeline source
                c.materialOut = new Material.OfItem(item);
                c.say("Thrall draws " + item.name + ".");
                return ExitCode.SUCCESS;
            }
            String owner = oi.container().split("/")[0];
            Entity oe = c.world.get(owner);
            if (oe != null && oe.held == item) {
                if (!c.world.dice.chance(35)) { c.say("Snatch fails."); return ExitCode.MISS; }
                oe.held = null;
            }
        } else if (r instanceof Resolved.OnTile ot) {
            World.Tile t = c.world.tile(ot.pos());
            if (t.ground.isEmpty()) { c.say("nothing on the ground there"); return ExitCode.BLOCKED; }
            item = t.ground.remove(0);
        }

        if (item == null) { c.say("cannot take " + a); return ExitCode.BLOCKED; }
        if (c.thrall.inventory().isPackFull()) { c.say("pack full"); return ExitCode.BLOCKED; }
        c.thrall.inventory().addToPack(item);
        c.materialOut = new Material.OfItem(item);
        c.say("Thrall takes " + item.name + ".");
        return ExitCode.SUCCESS;
    }
}
