package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.GameMap;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.Prop;

public final class TakeVerb implements Verb {
    @Override
    public String name() {
        return "take";
    }

    @Override
    public String help() {
        return "take <address> — pick up. 1 AP. Produces material.";
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
            return Check.invalid("take what?", null);
        }
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        Resolution resolution = VerbHelpers.resolve(c, c.inv.arg(0));

        if ((resolution instanceof Resolution.PackedItem packedItem
                && packedItem.owner() == c.thrall)
                || (resolution instanceof Resolution.EquippedItem equippedItem
                && equippedItem.owner() == c.thrall)) {
            return Check.ok();
        }

        if (c.thrall.inventory().isPackFull()) {
            return Check.blocked(
                    "pack full (" + Inventory.PACK_MAX + ")",
                    "drop something"
            );
        }

        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0);
        Resolution resolution = VerbHelpers.resolve(c, address);

        if (resolution instanceof Resolution.PackedItem packedItem) {
            return takeInventoryItem(c, address, packedItem.owner(), packedItem.item());
        }

        if (resolution instanceof Resolution.EquippedItem equippedItem) {
            return takeInventoryItem(c, address, equippedItem.owner(), equippedItem.item());
        }

        if (resolution instanceof Resolution.PropContents propContents) {
            return takePropContents(c, address, propContents.prop(), propContents.item());
        }

        if (resolution instanceof Resolution.TileTarget tileTarget) {
            return takeGroundItem(c, tileTarget);
        }

        c.say("cannot take " + address);
        return ExitCode.BLOCKED;
    }

    private ExitCode takeInventoryItem(
            VerbContext c,
            String address,
            Actor owner,
            Item item
    ) {
        /*
         * An item already owned by the thrall is a valid pipeline source.
         * Do not move it between inventory locations.
         */
        if (owner == c.thrall) {
            c.materialOut = new Material.OfItem(item);
            c.say("Thrall draws " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        if (c.thrall.inventory().isPackFull()) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        if (!c.world.dice().chance(35)) {
            c.say("Snatch fails.");
            return ExitCode.MISS;
        }

        if (!owner.inventory().remove(item)) {
            c.say("cannot take " + address);
            return ExitCode.BLOCKED;
        }

        return addToThrallPack(c, item);
    }

    private ExitCode takePropContents(
            VerbContext c,
            String address,
            Prop prop,
            Item item
    ) {
        if (c.thrall.inventory().isPackFull()) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        if (prop.contents() != item) {
            c.say("cannot take " + address);
            return ExitCode.BLOCKED;
        }

        return addToThrallPack(c, prop.removeContents());
    }

    private ExitCode takeGroundItem(
            VerbContext c,
            Resolution.TileTarget tileTarget
    ) {
        if (c.thrall.inventory().isPackFull()) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        GameMap.Tile tile = c.world.map().tile(tileTarget.pos());
        if (tile.ground().isEmpty()) {
            c.say("nothing on the ground there");
            return ExitCode.BLOCKED;
        }

        Item item = c.world.map().removeFirstGroundItem(tileTarget.pos());
        return addToThrallPack(c, item);
    }

    private ExitCode addToThrallPack(VerbContext c, Item item) {
        if (!c.thrall.inventory().addToPack(item)) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        c.materialOut = new Material.OfItem(item);
        c.say("Thrall takes " + item.name() + ".");
        return ExitCode.SUCCESS;
    }

}
