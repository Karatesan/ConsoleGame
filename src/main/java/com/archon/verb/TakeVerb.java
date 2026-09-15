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
            if (c.thrall.inventory().isPackFull()) {
                c.say("pack full");
                return ExitCode.BLOCKED;
            }

            World.Tile tile = c.world.tile(onTile.pos());
            if (tile.ground.isEmpty()) {
                c.say("nothing on the ground there");
                return ExitCode.BLOCKED;
            }

            Item item = tile.ground.remove(0);
            if (!c.thrall.inventory().addToPack(item)) {
                tile.ground.add(0, item);
                c.say("pack full");
                return ExitCode.BLOCKED;
            }

            c.materialOut = new Material.OfItem(item);
            c.say("Thrall takes " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        c.say("cannot take " + address);
        return ExitCode.BLOCKED;
    }

    private ExitCode takeItem(VerbContext c, String address, Resolved.OnItem onItem) {
        Item item = onItem.item();
        String container = onItem.container();

        if (isOwnPack(c, container)) {
            c.materialOut = new Material.OfItem(item);
            c.say("Thrall draws " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        if (c.thrall.inventory().isPackFull()) {
            c.say("pack full");
            return ExitCode.BLOCKED;
        }

        String owner = ownerOf(container);

        if (isHandContainer(container)) {
            Actor sourceActor = c.world.actor(owner);
            EquipmentSlot sourceSlot = slotOf(container);
            if (sourceActor == null || sourceSlot == null) {
                c.say("cannot take " + address);
                return ExitCode.BLOCKED;
            }

            if (!c.world.dice.chance(35)) {
                c.say("Snatch fails.");
                return ExitCode.MISS;
            }

            item = sourceActor.disarm(sourceSlot);
            if (item == null) {
                c.say("cannot take " + address);
                return ExitCode.BLOCKED;
            }
        } else if (isPackContainer(container)) {
            Actor sourceActor = c.world.actor(owner);
            if (sourceActor == null || !sourceActor.inventory().remove(item)) {
                c.say("cannot take " + address);
                return ExitCode.BLOCKED;
            }
        } else if (isContentsContainer(container)) {
            Entity ownerEntity = c.world.get(owner);
            Prop sourceProp = ownerEntity instanceof Prop candidate ? candidate : null;
            if (sourceProp == null) {
                c.say("cannot take " + address);
                return ExitCode.BLOCKED;
            }

            item = sourceProp.removeContents();
            if (item == null) {
                c.say("cannot take " + address);
                return ExitCode.BLOCKED;
            }
        } else {
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

    private static boolean isOwnPack(VerbContext c, String container) {
        if ("pack".equals(container)) {
            return true;
        }

        if (!isPackContainer(container)) {
            return false;
        }

        Actor actor = c.world.actor(ownerOf(container));
        return actor == c.thrall;
    }

    private static boolean isHandContainer(String container) {
        return hasSegment(container, "hand");
    }

    private static boolean isPackContainer(String container) {
        return "pack".equals(container) || hasSegment(container, "pack");
    }

    private static boolean isContentsContainer(String container) {
        return hasSegment(container, "contents");
    }

    private static String ownerOf(String container) {
        int separator = container.indexOf('/');
        return separator < 0 ? container : container.substring(0, separator);
    }

    private static boolean hasSegment(String path, String segment) {
        for (String part : path.split("/")) {
            if (segment.equals(part)) {
                return true;
            }
        }
        return false;
    }

    private static EquipmentSlot slotOf(String container) {
        int handIndex = container.indexOf("hand/");
        if (handIndex < 0) {
            return null;
        }
        return EquipmentSlot.parse(container.substring(handIndex)).orElse(null);
    }
}