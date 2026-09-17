package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;

public final class PourVerb implements Verb {
    @Override public String name() { return "pour"; }
    @Override public String help() { return "pour <liquid> <tile> — 1 AP. Accepts material."; }
    @Override public int apCost(Ast.Invocation inv) { return 1; }
    @Override public boolean acceptsMaterial() { return true; }
    @Override public boolean producesMaterial() { return true; }

    @Override
    public Check validateStructural(VerbContext c) {
        boolean piped = c.acceptsPipedMaterial();
        if (!piped && c.inv.args().size() < 2) return Check.invalid("pour needs a liquid and a tile", null);
        if (piped && c.inv.arg(0) == null) return Check.invalid("pour needs a destination tile", null);
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Tag substance;
        String targetArg;

        if (c.materialIn instanceof Material.OfSubstance os) {
            substance = os.substance();
            targetArg = c.inv.arg(0);
        } else if (c.materialIn instanceof Material.OfItem oi) {
            Item item = oi.item();
            substance = item.substance();
            item.consumeSubstance();
            targetArg = c.inv.arg(0);
        } else {
            Item item = c.itemFromMaterialOrArg(0);
            if (item == null || item.substance() == null) {
                c.say("nothing pourable");
                return ExitCode.BLOCKED;
            }

            substance = item.substance();
            c.thrall.inventory().remove(item);
            item.consumeSubstance();
            targetArg = c.inv.arg(1);
        }

        if (substance == null) {
            c.say("nothing pourable");
            return ExitCode.BLOCKED;
        }

        Resolution resolution = VerbHelpers.resolve(c, targetArg);
        if (resolution == null || resolution.target() == null) {
            c.say(resolution == null ? "cannot resolve " + targetArg : resolution.detail());
            return ExitCode.BLOCKED;
        }

        Resolved target = resolution.target();
        Vec2 at;

        if (target instanceof Resolved.EntityTarget entityTarget) {
            at = entityTarget.entity().pos();
        } else if (target instanceof Resolved.BodyTarget bodyTarget) {
            at = bodyTarget.body().pos();
        } else if (target instanceof Resolved.TileTarget tileTarget) {
            if (tileTarget.ceiling()) {
                c.say("cannot pour onto a ceiling");
                return ExitCode.BLOCKED;
            }
            at = tileTarget.pos();
        } else {
            c.say("cannot pour there");
            return ExitCode.BLOCKED;
        }

        VerbHelpers.spill(c, at, substance);
        c.materialOut = new Material.OfSubstance(substance);
        return ExitCode.SUCCESS;
    }
}