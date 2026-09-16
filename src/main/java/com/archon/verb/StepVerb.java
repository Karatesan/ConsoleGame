package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Vec2;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.List;

public final class StepVerb implements Verb {
    @Override
    public String name() {
        return "step";
    }

    @Override
    public String help() {
        return "step <dir> [-c] — move 1 tile. 1 AP (2 with -c).";
    }

    @Override
    public int apCost(Ast.Invocation inv) {
        return inv.hasFlag("careful") ? 2 : 1;
    }

    @Override
    public Check validateStructural(VerbContext c) {
        String direction = c.inv.arg(0);
        if (direction == null) {
            return Check.invalid("step needs a direction", "n s e w ne nw se sw");
        }
        if (Vec2.dir(direction) == null) {
            return Check.invalid("unknown direction \"" + direction + "\"", "n s e w ne nw se sw");
        }
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        Vec2 destination = c.thrall.pos().plus(Vec2.dir(c.inv.arg(0)));
        if (!SpatialService.passable(c.world, destination)) {
            return Check.blocked("wall or occupant at " + destination, "open: " + openDirs(c));
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Vec2 destination = c.thrall.pos().plus(Vec2.dir(c.inv.arg(0)));
        if (!SpatialService.passable(c.world, destination)) {
            c.say("blocked at " + destination);
            return ExitCode.BLOCKED;
        }

        c.thrall.moveTo(destination);
        c.world.markThrallMoved();
        c.say("Thrall advances to " + destination + ".");
        return ExitCode.SUCCESS;
    }

    private String openDirs(VerbContext c) {
        List<String> open = new ArrayList<>();
        for (String direction : List.of("n", "s", "e", "w", "ne", "nw", "se", "sw")) {
            Vec2 destination = c.thrall.pos().plus(Vec2.dir(direction));
            if (SpatialService.passable(c.world, destination)) {
                open.add(direction);
            }
        }
        return String.join(", ", open);
    }
}