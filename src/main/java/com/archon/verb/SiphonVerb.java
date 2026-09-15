package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.World;

public final class SiphonVerb implements Verb {
    @Override
    public String name() {
        return "siphon";
    }

    @Override
    public String help() {
        return "siphon <source> — draw liquid. 1 AP. Produces material.";
    }

    @Override
    public int apCost(Ast.Invocation inv) {
        return 1;
    }

    @Override
    public boolean producesMaterial() {
        return true;
    }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) {
            return Check.invalid("siphon from what?", null);
        }
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        Tag substance = sourceSubstance(c, c.inv.arg(0));
        if (substance == null) {
            return Check.blocked(
                    "nothing to siphon from " + c.inv.arg(0),
                    "try: inspect " + c.inv.arg(0)
            );
        }

        Entity holder = holderOf(c, c.inv.arg(0));
        if (holder != null && holder.getPos().chebyshev(c.thrall.getPos()) > 1) {
            return Check.blocked(holder.id() + " out of reach", "step closer");
        }

        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Tag substance = sourceSubstance(c, c.inv.arg(0));
        if (substance == null) {
            c.say("nothing to siphon");
            return ExitCode.BLOCKED;
        }

        c.materialOut = new Material.OfSubstance(substance);
        c.say("Thrall draws " + substance.name().toLowerCase() + ".");
        return ExitCode.SUCCESS;
    }

    private static Tag sourceSubstance(VerbContext c, String arg) {
        Resolved resolved = VerbHelpers.resolve(c, arg);

        if (resolved instanceof Resolved.OnItem onItem && onItem.item() != null) {
            return onItem.item().substance();
        }

        if (resolved instanceof Resolved.OnEntity onEntity) {
            Entity entity = onEntity.entity();

            if (entity instanceof Actor actor && actor.mainHand() != null) {
                return actor.mainHand().substance();
            }

            if (entity instanceof Prop prop && prop.contents() != null) {
                return prop.contents().substance();
            }
        }

        if (resolved instanceof Resolved.OnTile onTile) {
            World.Tile tile = c.world.tile(onTile.pos());
            if (tile.has(Tag.OIL)) {
                return Tag.OIL;
            }
            if (tile.has(Tag.WATER)) {
                return Tag.WATER;
            }
        }

        return null;
    }

    private static Entity holderOf(VerbContext c, String arg) {
        Address address = Address.parse(arg);
        if (address instanceof Address.EntityAddr entityAddress) {
            return c.world.get(entityAddress.id());
        }
        return null;
    }
}