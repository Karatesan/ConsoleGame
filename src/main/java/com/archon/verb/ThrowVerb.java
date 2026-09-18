package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;

public final class ThrowVerb implements Verb {
    @Override public String name() { return "throw"; }
    @Override public String help() {
        return "throw <item> <target> — 2 AP. Accepts material from a pipe.";
    }
    @Override public int apCost(Ast.Invocation inv) { return 2; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        int required = c.acceptsPipedMaterial() ? 1 : 2;
        return c.inv.args().size() < required
                ? Check.invalid("throw needs an item and a target", "take /pack/flask_oil | throw o1")
                : Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Check syntax = validateStructural(c);
        if (!syntax.valid()) return VerbTargets.reject(c, syntax);

        Resolved destination = VerbHelpers.resolve(
                c, c.inv.arg(c.acceptsPipedMaterial() ? 0 : 1)
        );
        Check resolution = VerbTargets.resolution(destination);
        if (!resolution.valid()) return VerbTargets.reject(c, resolution);
        if (!(destination instanceof Resolved.OnEntity
                || destination instanceof Resolved.OnBody
                || destination instanceof Resolved.OnTile tile
                && tile.layer() != Address.Layer.CEILING)) {
            return VerbTargets.reject(c, Check.invalid("throw needs an entity or floor target", null));
        }

        ItemInput input = ItemInput.select(c, 0);
        if (input instanceof ItemInput.Rejected rejected) {
            return VerbTargets.reject(c, rejected.check());
        }
        Item item = ((ItemInput.Selected) input).target().item();
        if (!c.thrall.inventory().remove(item)) {
            return VerbTargets.reject(c, Check.blocked("item is no longer carried", null));
        }

        Vec2 at = ((Resolved.Target) destination).pos();
        c.say(item.name() + " arcs toward " + at + " and shatters.");
        Tag substance = item.consumeSubstance();
        if (substance != null) VerbHelpers.spill(c, at, substance);
        return ExitCode.SUCCESS;
    }
}