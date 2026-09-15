package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.*;

public final class TakeVerb implements Verb {
    @Override public String name() { return "take"; }
    @Override public String help() { return "take <address> — pick up. 1 AP. Produces material."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean producesMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        if (c.inv.arg(0) == null) return Check.invalid("take what?", null);
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        if (resolved instanceof Resolved.OnItem item && "pack".equals(item.container())) {
            return Check.ok();
        }
        if (c.thrall.inventory().isPackFull()) {
            return Check.blocked("pack full (" + Inventory.PACK_MAX + ")", "drop something");
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0);
        Resolved resolved = VerbHelpers.resolve(c, address);
        Item item = null;

        if (resolved instanceof Resolved.OnItem onItem && onItem.item() != null) {
            item = onItem.item();
            String container = onItem.container();

            if ("pack".equals(container)) {
                c.materialOut = new Material.OfItem(item);
                c.say("Thrall draws " + item.name() + ".");
                return ExitCode.SUCCESS;
            }

            int firstSlash = container.indexOf('/');
            String owner = firstSlash >= 0 ? container.substring(0, firstSlash) : container;

            Actor actor = c.world.actor(owner);
            if (actor != null) {
                if (!c.world.dice.chance(35)) {
                    c.say("Snatch fails.");
                    return ExitCode.MISS;
                }

                if (container.startsWith(owner + "/hand/")) {
                    int lastSlash = container.lastIndexOf('/');
                    EquipmentSlot slot = EquipmentSlot.parse(container.substring(lastSlash + 1));
                    actor.removeEquipped(slot);
                } else {
                    actor.inventory().remove(item);
                }
            } else {
                Entity entity = c.world.get(owner);
                if (entity instanceof Prop prop) {
                    prop.removeContents(item);
                }
            }
        } else if (resolved instanceof Resolved.OnTile onTile) {
            World.Tile tile = c.world.tile(onTile.pos());
            if (tile.ground.isEmpty()) {
                c.say("nothing on the ground there");
                return ExitCode.BLOCKED;
            }
            item = tile.ground.remove(0);
        }

        if (item == null) {
            c.say("cannot take " + address);
            return ExitCode.BLOCKED;
        }

        if (!c.thrall.inventory().addToPack(item)) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        c.materialOut = new Material.OfItem(item);
        c.say("Thrall takes " + item.name() + ".");
        return ExitCode.SUCCESS;
    }
}