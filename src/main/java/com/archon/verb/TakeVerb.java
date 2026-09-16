package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.Entity;
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
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));

        if (resolved instanceof Resolved.OnItem onItem
                && onItem.item() != null
                && isOwnedByThrall(c, onItem.container())) {
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
        Resolved resolved = VerbHelpers.resolve(c, address);

        if (resolved instanceof Resolved.OnItem onItem && onItem.item() != null) {
            return takeItem(c, address, onItem);
        }

        if (resolved instanceof Resolved.OnTile onTile) {
            return takeGroundItem(c, address, onTile);
        }

        c.say("cannot take " + address);
        return ExitCode.BLOCKED;
    }

    private ExitCode takeItem(
            VerbContext c,
            String address,
            Resolved.OnItem onItem
    ) {
        Item item = onItem.item();

        /*
         * An item already owned by the thrall is a valid pipeline source.
         * Do not move it between inventory locations.
         */
        if (isOwnedByThrall(c, onItem.container())) {
            c.materialOut = new Material.OfItem(item);
            c.say("Thrall draws " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        if (c.thrall.inventory().isPackFull()) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        String ownerId = ownerOf(onItem.container());
        Entity owner = c.world.get(ownerId);

        if (owner instanceof Actor actor) {
            if (!c.world.dice().chance(35)) {
                c.say("Snatch fails.");
                return ExitCode.MISS;
            }

            if (!actor.inventory().remove(item)) {
                c.say("cannot take " + address);
                return ExitCode.BLOCKED;
            }
        } else if (owner instanceof Prop prop) {
            if (prop.contents() != item) {
                c.say("cannot take " + address);
                return ExitCode.BLOCKED;
            }

            item = prop.removeContents();
        } else {
            c.say("cannot take " + address);
            return ExitCode.BLOCKED;
        }

        return addToThrallPack(c, item);
    }

    private ExitCode takeGroundItem(
            VerbContext c,
            String address,
            Resolved.OnTile onTile
    ) {
        if (c.thrall.inventory().isPackFull()) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        GameMap.Tile tile = c.world.map().tile(onTile.pos());
        if (tile.ground().isEmpty()) {
            c.say("nothing on the ground there");
            return ExitCode.BLOCKED;
        }

        Item item = c.world.map().removeFirstGroundItem(onTile.pos());
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

    private static boolean isOwnedByThrall(VerbContext c, String container) {
        if ("pack".equals(container)) {
            return true;
        }

        String ownerId = ownerOf(container);
        return "self".equals(ownerId) || c.world.actor(ownerId) == c.thrall;
    }

    private static String ownerOf(String container) {
        if (container == null || container.isBlank()) {
            return "";
        }

        int separator = container.indexOf('/');
        return separator < 0
                ? container
                : container.substring(0, separator);
    }
}