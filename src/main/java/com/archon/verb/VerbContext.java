package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.exec.RoundState;
import com.archon.model.ItemLocation;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.environment.Substances;
import com.archon.system.item.ItemMoves;
import com.archon.system.physics.ThermalState;

import java.util.ArrayList;
import java.util.List;

public final class VerbContext extends ValidationContext {

    public final EventBus bus;
    public final Material materialIn;
    public final RoundState round;
    public final boolean pipedOutput;
    public final List<String> log = new ArrayList<>();
    public Material materialOut;

    public VerbContext(World world, Ast.Invocation inv, EventBus bus, Material materialIn) {
        this(world, inv, bus, materialIn, null, false);
    }

    public VerbContext(World world, Ast.Invocation inv, EventBus bus, Material materialIn, RoundState round) {
        this(world, inv, bus, materialIn, round, false);
    }

    public VerbContext(World world, Ast.Invocation inv, EventBus bus, Material materialIn, RoundState round, boolean pipedOutput) {
        super(world, inv, materialIn != null);
        this.bus = bus;
        this.materialIn = materialIn;
        this.round = round;
        this.pipedOutput = pipedOutput;
    }

    public void say(String text) {
        bus.narrate(text);
    }

    public void sayAll(List<String> log) {
        for (int i = 0; i < log.size(); i++) {
            say(log.get(i));
        }
    }

    public void flushLog() {
        sayAll(log);
        log.clear();
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
        if (resolved instanceof Resolved.Failure f) {
            say(f.reason());
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
        if (resolved instanceof Resolved.Failure f) {
            say(f.reason());
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

    public Resolved.Target selectDeliveryTarget(boolean isThrow) {
        Resolved.Target target = resolveTarget(inv.arg(pipedInput ? 0 : 1));
        if (target == null) return null;

        boolean accepted = target instanceof Resolved.OnEntity
                || target instanceof Resolved.OnTile
                || (isThrow && target instanceof Resolved.OnBody);
        if (!accepted) {
            say(isThrow ? "throw needs an entity or floor target" : "pour needs an entity or floor tile");
            return null;
        }

        if (!isThrow && !checkReachable(target)) {
            return null;
        }

        Substances.requireDestination(world, target.pos());
        return target;
    }

    public static boolean isFloorDelivery(Resolved.Target target) {
        return target instanceof Resolved.OnTile ot && "floor".equals(ot.layer());
    }

    public boolean payBloodCost(String spell, int hpCost) {
        ThermalState.damageEntity(world, thrall, hpCost, true, log);
        if (!thrall.alive()) {
            say(spell + " drains the last of " + thrall.name() + "'s life (" + hpCost + " HP)!");
            flushLog();
            return false;
        }
        log.add(spell + " drains " + hpCost + " HP from " + thrall.name()
                + " (" + thrall.hp() + "/" + thrall.maxHp() + " HP).");
        return true;
    }
}