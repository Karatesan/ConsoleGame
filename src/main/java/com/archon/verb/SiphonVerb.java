package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.Item;
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
        String source = c.inv.arg(0);
        Tag substance = sourceSubstance(c, source);

        if (substance == null) {
            return Check.blocked(
                    "nothing to siphon from " + source,
                    "try: inspect " + source
            );
        }

        Entity holder = holderOf(c, source);
        if (holder != null && holder.pos().chebyshev(c.thrall.pos()) > 1) {
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

    private static Tag sourceSubstance(VerbContext c, String address) {
        Resolved resolved = VerbHelpers.resolve(c, address);

        if (resolved instanceof Resolved.OnItem onItem) {
            return substanceOf(onItem.item());
        }

        if (resolved instanceof Resolved.OnEntity onEntity) {
            Entity entity = onEntity.entity();

            if (entity instanceof Actor actor) {
                return substanceOf(actor.mainHand());
            }

            if (entity instanceof Prop prop) {
                return substanceOf(prop.contents());
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

    private static Tag substanceOf(Item item) {
        return item == null ? null : item.substance();
    }

    private static Entity holderOf(VerbContext c, String address) {
        Address parsed = Address.parse(address);

        if (parsed instanceof Address.EntityAddr entityAddress) {
            return c.world.get(entityAddress.id());
        }

        return null;
    }
}