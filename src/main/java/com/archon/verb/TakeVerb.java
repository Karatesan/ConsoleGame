package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.*;

public final class TakeVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.inv.arg(0) == null ? Check.invalid("take what?", null) : Check.ok();
    }

    private Resolved resolveItem(ValidationContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        if (resolved instanceof Resolved.OnTile tile
                && tile.layer() != Address.Layer.CEILING) {
            var ground = c.world.tile(tile.pos()).ground();
            if (!ground.isEmpty()) {
                return new Resolved.OnItem(
                        ground.getFirst(),
                        new ItemLocation.Ground(tile.pos())
                );
            }
        }
        return resolved;
    }

    private Check check(ValidationContext c, Resolved resolved) {
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) return resolution;

        if (resolved instanceof Resolved.OnTile tile) {
            return tile.layer() == Address.Layer.CEILING
                    ? Check.invalid("cannot take ground items from the ceiling", null)
                    : Check.blocked("nothing on the ground there", null);
        }
        if (resolved instanceof Resolved.OnLocation location) {
            return location.location() instanceof ItemLocation.Pack
                    ? Check.invalid("select an item from the pack", "use /pack/<item>")
                    : Check.blocked("nothing there to take", null);
        }
        if (!(resolved instanceof Resolved.OnItem item)) {
            return Check.invalid("take needs an item or ground tile", null);
        }
        if (VerbTargets.ownedBy(item.location(), c.thrall)) {
            return Check.ok();
        }

        Check reach = VerbTargets.reachable(c, item);
        if (!reach.valid()) return reach;
        return c.thrall.inventory().isPackFull()
                ? Check.blocked("pack full (" + Inventory.PACK_MAX + ")", "drop something")
                : Check.ok();
    }

    @Override
    public Check validateState(ValidationContext c) {
        return check(c, resolveItem(c));
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = resolveItem(c);
        Check check = check(c, resolved);
        if (!check.valid()) return VerbTargets.reject(c, check);

        Resolved.OnItem selected = (Resolved.OnItem) resolved;
        Item item = selected.item();
        ItemLocation location = selected.location();
        if (VerbTargets.ownedBy(location, c.thrall)) {
            c.materialOut = new Material.OfItem(item);
            c.say("Thrall draws " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        boolean stealing = location instanceof ItemLocation.Pack
                || location instanceof ItemLocation.Equipment;
        if (stealing && !c.world.dice().chance(35)) {
            c.say("Snatch fails.");
            return ExitCode.MISS;
        }
        if (!c.thrall.inventory().addToPack(item)) {
            return VerbTargets.reject(c, Check.blocked("cannot place item in pack", null));
        }
        if (!detach(c, selected)) {
            c.thrall.inventory().remove(item);
            return VerbTargets.reject(c, Check.blocked("item no longer at its source", null));
        }

        c.materialOut = new Material.OfItem(item);
        c.say("Thrall takes " + item.name() + ".");
        return ExitCode.SUCCESS;
    }

    private boolean detach(VerbContext c, Resolved.OnItem selected) {
        Item item = selected.item();
        return switch (selected.location()) {
            case ItemLocation.Pack pack ->
                    pack.owner().inventory().pack().stream().anyMatch(candidate -> candidate == item)
                            && pack.owner().inventory().remove(item);
            case ItemLocation.Equipment equipment ->
                    equipment.owner().inventory().equipped(equipment.slot()) == item
                            && equipment.owner().inventory().removeEquipped(equipment.slot()) == item;
            case ItemLocation.Contents contents ->
                    contents.owner().contents() == item
                            && contents.owner().removeContents() == item;
            case ItemLocation.Ground ground ->
                    c.world.map().removeGroundItem(ground.pos(), item);
        };
    }
}