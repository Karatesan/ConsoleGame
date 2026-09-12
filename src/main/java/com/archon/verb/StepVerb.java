package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Vec2;

import java.util.ArrayList;
import java.util.List;

public final class StepVerb implements Verb {
    @Override public String name() { return "step"; }
    @Override public String help() { return "step <dir> [-c] — move 1 tile. 1 AP (2 with -c)."; }
    @Override public int apCost(Ast.Invocation inv) { return inv.hasFlag("careful") ? 2 : 1; }

    @Override
    public Check validateStructural(VerbContext c) {
        String d = c.inv.arg(0);
        if (d == null) return Check.invalid("step needs a direction", "n s e w ne nw se sw");
        if (Vec2.dir(d) == null) return Check.invalid("unknown direction \"" + d + "\"", "n s e w ne nw se sw");
        return Check.ok();
    }

    @Override
    public Check validateState(VerbContext c) {
        Vec2 to = c.thrall.pos().plus(Vec2.dir(c.inv.arg(0)));
        if (!c.world.passable(to))
            return Check.blocked("wall or occupant at " + to, "open: " + openDirs(c));
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Vec2 to = c.thrall.pos().plus(Vec2.dir(c.inv.arg(0)));
        if (!c.world.passable(to)) { c.say("blocked at " + to); return ExitCode.BLOCKED; }
        c.thrall.moveTo(to);
        c.world.thrallMovedThisLine = true;
        c.say("Thrall advances to " + to + ".");
        return ExitCode.SUCCESS;
    }

    private String openDirs(VerbContext c) {
        List<String> open = new ArrayList<>();
        for (String d : List.of("n", "s", "e", "w", "ne", "nw", "se", "sw"))
            if (c.world.passable(c.thrall.pos().plus(Vec2.dir(d)))) open.add(d);
        return String.join(", ", open);
    }
}