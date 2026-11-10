package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.system.item.ItemMoves;

public final class TakeVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAddress(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target target = c.resolveTarget(c.inv.arg(0));
        if (target == null) return ExitCode.BLOCKED;

        if (target instanceof Resolved.OnTile) {
            var ground = c.world.tile(target.pos()).ground();
            if (!ground.isEmpty()) {
                target = new Resolved.OnItem(ground.getFirst(), new ItemLocation.Ground(target.pos()));
            } else {
                return c.rejectBlocked("nothing on the ground there");
            }
        }
        if (target instanceof Resolved.OnLocation(var loc)) {
            return c.rejectBlocked(loc instanceof ItemLocation.Pack
                    ? "select an item from the pack"
                    : "nothing there to take");
        }
        if (!(target instanceof Resolved.OnItem(Item item, ItemLocation location))) {
            return c.rejectBlocked("take needs an item or ground tile");
        }

        if (c.isCarried(location)) {
            var check = ItemMoves.checkSource(c.world.map(), item, location);
            if (check != ItemMoves.Result.SUCCESS) return c.rejectBlocked(check.reason());

            c.materialOut = new Material.OfItem(item);
            c.say("Thrall draws " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        if (!c.checkReachable(target)) return ExitCode.BLOCKED;

        var check = ItemMoves.checkToPack(c.world.map(), item, location, c.thrall);
        if (check != ItemMoves.Result.SUCCESS) return c.rejectBlocked(check.reason());

        boolean stealing = location instanceof ItemLocation.Pack || location instanceof ItemLocation.Equipment;
        if (stealing && !c.world.dice().chance(com.archon.model.Balance.SNATCH_BASE_CHANCE)) {
            c.say("Snatch fails.");
            return ExitCode.MISS;
        }

        var moveResult = ItemMoves.toPack(c.world.map(), item, location, c.thrall);
        if (moveResult != ItemMoves.Result.SUCCESS) {
            return c.rejectBlocked(moveResult.reason());
        }

        c.materialOut = new Material.OfItem(item);
        c.say("Thrall takes " + item.name() + ".");
        return ExitCode.SUCCESS;
    }
}