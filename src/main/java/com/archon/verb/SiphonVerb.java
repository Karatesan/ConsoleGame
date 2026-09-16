package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;

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
        Resolution resolution = VerbHelpers.resolve(c, source);

        if (!(resolution instanceof Resolved target)) {
            return Check.blocked(
                    "nothing to siphon from " + source,
                    "try: inspect " + source
            );
        }

        Tag substance = sourceSubstance(c, target);
        if (substance == null) {
            return Check.blocked(
                    "nothing to siphon from " + source,
                    "try: inspect " + source
            );
        }

        Entity holder = null;
        if (target instanceof Resolved.PackedItem packedItem) {
            holder = packedItem.owner();
        } else if (target instanceof Resolved.EquippedItem equippedItem) {
            holder = equippedItem.owner();
        } else if (target instanceof Resolved.PropContents propContents) {
            holder = propContents.prop();
        } else if (target instanceof Resolved.EntityTarget entityTarget) {
            holder = entityTarget.entity();
        } else if (target instanceof Resolved.BodyTarget bodyTarget) {
            holder = bodyTarget.entity();
        }

        if (holder != null && holder.pos().chebyshev(c.thrall.pos()) > 1) {
            return Check.blocked(holder.id() + " out of reach", "step closer");
        }

        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolution resolution = VerbHelpers.resolve(c, c.inv.arg(0));
        Tag substance = resolution instanceof Resolved target
                ? sourceSubstance(c, target)
                : null;

        if (substance == null) {
            c.say("nothing to siphon");
            return ExitCode.BLOCKED;
        }

        c.materialOut = new Material.OfSubstance(substance);
        c.say("Thrall draws " + substance.name().toLowerCase() + ".");
        return ExitCode.SUCCESS;
    }

    private static Tag sourceSubstance(VerbContext c, Resolved target) {
        if (target instanceof Resolved.PackedItem packedItem) {
            return substanceOf(packedItem.item());
        }

        if (target instanceof Resolved.EquippedItem equippedItem) {
            return substanceOf(equippedItem.item());
        }

        if (target instanceof Resolved.PropContents propContents) {
            return substanceOf(propContents.item());
        }

        if (target instanceof Resolved.EntityTarget entityTarget) {
            Entity entity = entityTarget.entity();

            if (entity instanceof Actor actor) {
                return substanceOf(actor.mainHand());
            }

            if (entity instanceof Prop prop) {
                return substanceOf(prop.contents());
            }
        }

        if (target instanceof Resolved.BodyTarget bodyTarget) {
            Entity entity = bodyTarget.entity();

            if (entity instanceof Actor actor) {
                return substanceOf(actor.mainHand());
            }

            if (entity instanceof Prop prop) {
                return substanceOf(prop.contents());
            }
        }

        if (target instanceof Resolved.TileTarget tileTarget) {
            GameMap.Tile tile = c.world.tile(tileTarget.pos());

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
}
