package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;

public final class SiphonVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.inv.arg(0) == null
                ? Check.invalid("siphon from what?", null)
                : Check.ok();
    }

    private Check check(ValidationContext c, Resolved resolved) {
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) return resolution;
        if (resolved instanceof Resolved.OnTile tile
                && tile.layer() == Address.Layer.CEILING) {
            return Check.invalid("cannot siphon from the ceiling", null);
        }

        Check reach = VerbTargets.reachable(c, (Resolved.Target) resolved);
        if (!reach.valid()) return reach;
        return sourceSubstance(c, resolved) == null
                ? Check.blocked("nothing to siphon", "try: inspect " + c.inv.arg(0))
                : Check.ok();
    }

    @Override
    public Check validateState(ValidationContext c) {
        return check(c, VerbHelpers.resolve(c, c.inv.arg(0)));
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        Check check = check(c, resolved);
        if (!check.valid()) return VerbTargets.reject(c, check);

        Item item = sourceItem(resolved);
        Tag substance;
        if (item != null) {
            substance = item.consumeSubstance();
        } else {
            Resolved.OnTile tile = (Resolved.OnTile) resolved;
            substance = sourceSubstance(c, resolved);
            c.world.map().removeTag(tile.pos(), substance);
        }

        c.materialOut = new Material.OfSubstance(substance);
        c.say("Thrall draws " + substance.name().toLowerCase() + ".");
        return ExitCode.SUCCESS;
    }

    private static Tag sourceSubstance(ValidationContext c, Resolved resolved) {
        Item item = sourceItem(resolved);
        if (item != null) return item.substance();

        if (resolved instanceof Resolved.OnTile target
                && target.layer() != Address.Layer.CEILING) {
            GameMap.Tile tile = c.world.tile(target.pos());
            if (tile.has(Tag.OIL)) return Tag.OIL;
            if (tile.has(Tag.WATER)) return Tag.WATER;
        }
        return null;
    }

    private static Item sourceItem(Resolved resolved) {
        if (resolved instanceof Resolved.OnItem target) {
            return target.item();
        }
        if (resolved instanceof Resolved.OnEntity target) {
            if (target.entity() instanceof Actor actor) {
                return actor.mainHand();
            }
            if (target.entity() instanceof Prop prop) {
                return prop.contents();
            }
        }
        return null;
    }
}