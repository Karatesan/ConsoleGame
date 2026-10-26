package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.ItemLocation;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.environment.Substances;
import com.archon.system.item.ItemMoves;

public final class VerbContext extends ValidationContext {

    public final EventBus bus;
    public final Material materialIn;
    public Material materialOut;

    public VerbContext(World world, Ast.Invocation inv, EventBus bus, Material materialIn) {
        super(world, inv, materialIn != null);
        this.bus = bus;
        this.materialIn = materialIn;
    }

    public void say(String text) {
        bus.narrate(text);
    }

    public ExitCode rejectBlocked(String reason) {
        say(reason);
        return ExitCode.BLOCKED;
    }

    public boolean checkReachable(Resolved.Target target) {
        if (target.pos().chebyshev(thrall.pos()) <= 1) {
            return true;
        }
        say("target at " + target.pos() + " out of reach");
        return false;
    }

    public void describeSpill(Vec2 at, Tag substance) {
        say(substance.name().toLowerCase() + " spreads across " + at + ".");
    }

    public Resolved.Target resolveTarget(String raw) {
        Resolved resolved = resolve(raw);
        if (resolved instanceof Resolved.Failure(String reason)) {
            say(reason);
            return null;
        }
        return (Resolved.Target) resolved;
    }

    public Resolved.OnItem selectCarriedItem(int argIndex) {
        if (materialIn instanceof Material.OfItem(var item)) {
            ItemLocation location = ItemMoves.carriedLocation(thrall, item);
            if (location == null) {
                say("piped item is no longer carried");
                return null;
            }
            return new Resolved.OnItem(item, location);
        }

        Resolved resolved = resolve(itemAddress(inv.arg(argIndex)));
        if (resolved instanceof Resolved.Failure(String reason)) {
            say(reason);
            return null;
        }
        if (resolved instanceof Resolved.OnLocation) {
            say("address does not select an item");
            return null;
        }
        if (!(resolved instanceof Resolved.OnItem item)) {
            say("expected an item address");
            return null;
        }
        if (!isCarried(item.location())) {
            say("not carrying that item");
            return null;
        }
        if (ItemMoves.checkSource(world.map(), item.item(), item.location()) != ItemMoves.Result.SUCCESS) {
            say("item no longer at its source");
            return null;
        }
        return item;
    }

    public Vec2 selectDeliveryPos(boolean isThrow) {
        Resolved.Target target = resolveTarget(inv.arg(pipedInput ? 0 : 1));
        if (target == null) return null;

        boolean accepted = target instanceof Resolved.OnEntity
                || target.isFloorTile()
                || (isThrow && target instanceof Resolved.OnBody);
        if (!accepted) {
            say(isThrow ? "throw needs an entity or floor target" : "pour needs an entity or floor tile");
            return null;
        }

        if (!isThrow && !checkReachable(target)) {
            return null;
        }

        Substances.requireDestination(world, target.pos());
        return target.pos();
    }
}