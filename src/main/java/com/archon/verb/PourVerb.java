package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;

public final class PourVerb implements Verb {
    @Override public String name() { return "pour"; }
    @Override public String help() {
        return "pour <liquid> <tile|entity> — 1 AP. Consumes liquid; keeps the container.";
    }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        int required = c.acceptsPipedMaterial() ? 1 : 2;
        return c.inv.args().size() < required
                ? Check.invalid("pour needs a liquid source and destination", null)
                : Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Check syntax = validateStructural(c);
        if (!syntax.valid()) return VerbTargets.reject(c, syntax);

        String address = c.inv.arg(c.acceptsPipedMaterial() ? 0 : 1);
        Resolved destination = VerbHelpers.resolve(c, address);
        Check resolution = VerbTargets.resolution(destination);
        if (!resolution.valid()) return VerbTargets.reject(c, resolution);
        if (!(destination instanceof Resolved.OnEntity
                || destination instanceof Resolved.OnTile tile
                && tile.layer() != Address.Layer.CEILING)) {
            return VerbTargets.reject(c, Check.invalid("pour needs an entity or floor tile", null));
        }

        Resolved.Target target = (Resolved.Target) destination;
        Check reach = VerbTargets.reachable(c, target);
        if (!reach.valid()) return VerbTargets.reject(c, reach);

        Item container = null;
        Tag substance;
        if (c.materialIn instanceof Material.OfSubstance material) {
            substance = material.substance();
        } else {
            ItemInput input = ItemInput.select(c, 0);
            if (input instanceof ItemInput.Rejected rejected) {
                return VerbTargets.reject(c, rejected.check());
            }
            container = ((ItemInput.Selected) input).target().item();
            substance = container.substance();
        }

        if (substance == null) {
            return VerbTargets.reject(c, Check.blocked("nothing pourable", null));
        }
        if (container != null) {
            substance = container.consumeSubstance();
        }
        VerbHelpers.spill(c, target.pos(), substance);
        return ExitCode.SUCCESS;
    }
}