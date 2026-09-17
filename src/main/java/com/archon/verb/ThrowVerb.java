package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolution;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Item;
import com.archon.model.Vec2;

public final class ThrowVerb implements Verb {
    @Override public String name() { return "throw"; }
    @Override public String help() { return "throw <item> <target> — 2 AP. Accepts material from a pipe."; }
    @Override public int apCost(Ast.Invocation inv) { return 2; }
    @Override public boolean acceptsMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        boolean piped = c.acceptsPipedMaterial();
        int need = piped ? 1 : 2;
        if (c.inv.args().size() < need) {
            return Check.invalid(
                    "throw needs " + (piped ? "a target" : "an item and a target"),
                    "e.g. take /pack/flask_oil | throw o1");
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        boolean piped = c.materialIn instanceof Material.OfItem;
        Item item = piped
                ? ((Material.OfItem) c.materialIn).item()
                : c.itemFromMaterialOrArg(0);
        String targetArg = piped ? c.inv.arg(0) : c.inv.arg(1);

        if (item == null) {
            c.say("no such item to throw");
            return ExitCode.BLOCKED;
        }

        Resolution resolution = VerbHelpers.resolve(c, targetArg);
        Resolved target = VerbHelpers.found(resolution);
        if (target == null) {
            c.say("cannot resolve " + targetArg + ": " + VerbHelpers.failureDetail(resolution));
            return ExitCode.BLOCKED;
        }

        Vec2 at = switch (target) {
            case Resolved.EntityTarget entityTarget -> entityTarget.entity().pos();
            case Resolved.BodyTarget bodyTarget -> bodyTarget.entity().pos();
            case Resolved.TileTarget tileTarget -> {
                if (tileTarget.address() == Address.FLOOR) {
                    yield tileTarget.pos();
                }
                c.say("cannot throw at ceiling");
                yield null;
            }
            default -> {
                c.say("cannot throw at " + targetArg);
                yield null;
            }
        };

        if (at == null) {
            return ExitCode.BLOCKED;
        }

        if (!piped && !c.thrall.inventory().remove(item)) {
            c.say("no such item to throw");
            return ExitCode.BLOCKED;
        }

        c.say("Flask arcs toward " + at + " and shatters.");
        var substance = item.consumeSubstance();
        if (substance != null) {
            VerbHelpers.spill(c, at, substance);
        }
        return ExitCode.SUCCESS;
    }
}