// src/main/java/com/archon/verb/TakeVerb.java
package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.system.item.ItemMoves;

public final class TakeVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAddress(0);
    }

    private Resolved resolveItem(ValidationContext c) {
        Resolved resolved = c.resolve(c.inv.arg(0));
        if (resolved.isFloorTile() && resolved instanceof Resolved.OnTile tile) {
            var ground = c.world.tile(tile.pos()).ground();
            if (!ground.isEmpty()) {
                return new Resolved.OnItem(ground.getFirst(), new ItemLocation.Ground(tile.pos()));
            }
        }
        return resolved;
    }

    private Check check(ValidationContext c, Resolved resolved) {
        Check resolution = resolved.toCheck();
        if (!resolution.valid()) return resolution;

        if (resolved instanceof Resolved.OnTile tile) {
            return tile.layer() == Address.Layer.CEILING
                    ? Check.blocked("cannot take ground items from the ceiling", null)
                    : Check.blocked("nothing on the ground there", null);
        }
        if (resolved instanceof Resolved.OnLocation location) {
            return location.location() instanceof ItemLocation.Pack
                    ? Check.blocked("select an item from the pack", "use /pack/<item>")
                    : Check.blocked("nothing there to take", null);
        }
        if (!(resolved instanceof Resolved.OnItem item)) {
            return Check.blocked("take needs an item or ground tile", null);
        }

        boolean carried = c.isCarried(item.location());
        if (!carried) {
            Check reach = c.checkReachable(item);
            if (!reach.valid()) return reach;
        }

        ItemMoves.Result result = carried
                ? ItemMoves.checkSource(c.world.map(), item.item(), item.location())
                : ItemMoves.checkToPack(c.world.map(), item.item(), item.location(), c.thrall);
        return result == ItemMoves.Result.SUCCESS
                ? Check.ok()
                : Check.blocked(result.reason(), result == ItemMoves.Result.PACK_FULL ? "drop something" : null);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = resolveItem(c);
        Check check = check(c, resolved);
        if (!check.valid()) return c.reject(check);

        Resolved.OnItem selected = (Resolved.OnItem) resolved;
        Item item = selected.item();
        ItemLocation location = selected.location();
        if (c.isCarried(location)) {
            c.materialOut = new Material.OfItem(item);
            c.say("Thrall draws " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        boolean stealing = location instanceof ItemLocation.Pack || location instanceof ItemLocation.Equipment;
        if (stealing && !c.world.dice().chance(35)) {
            c.say("Snatch fails.");
            return ExitCode.MISS;
        }

        ItemMoves.Result result = ItemMoves.toPack(c.world.map(), item, location, c.thrall);
        if (result != ItemMoves.Result.SUCCESS) {
            return c.rejectBlocked(result.reason());
        }

        c.materialOut = new Material.OfItem(item);
        c.say("Thrall takes " + item.name() + ".");
        return ExitCode.SUCCESS;
    }
}