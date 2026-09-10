package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Vec2;

import java.util.ArrayList;
import java.util.List;

public final class StepVerb implements Verb {

    private static final VerbDoc DOC = new VerbDoc(
            "step",
            "Advance 1 tile in a cardinal or diagonal direction",
            "MOVEMENT",
            "step <dir> [-c]",
            "1 AP (2 AP with -c / --careful)",
            "Moves the thrall 1 tile in the specified direction: n, s, e, w, ne, nw, se, sw.\n"
                    + "Fails statically if targeting a wall or occupied tile.\n"
                    + "May trigger enemy movement reactions (e.g. archers readied with ON_MOVEMENT_IN_LOS).",
            List.of(
                    new VerbDoc.FlagDoc("-c, --careful", "Move carefully at increased AP cost (2 AP)."),
                    new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
            ),
            false,
            false,
            List.of(
                    "step e",
                    "step nw -c",
                    "step w ; strike o1"
            )
    );

    @Override public String name() { return "step"; }
    @Override public VerbDoc doc() { return DOC; }
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
        Vec2 to = c.thrall.pos.plus(Vec2.dir(c.inv.arg(0)));
        if (!c.world.passable(to))
            return Check.blocked("wall or occupant at " + to, "open: " + openDirs(c));
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Vec2 to = c.thrall.pos.plus(Vec2.dir(c.inv.arg(0)));
        if (!c.world.passable(to)) { c.say("blocked at " + to); return ExitCode.BLOCKED; }
        c.thrall.pos = to;
        c.world.thrallMovedThisLine = true;
        c.say("Thrall advances to " + to + ".");
        return ExitCode.SUCCESS;
    }

    private String openDirs(VerbContext c) {
        List<String> open = new ArrayList<>();
        for (String d : List.of("n", "s", "e", "w", "ne", "nw", "se", "sw"))
            if (c.world.passable(c.thrall.pos.plus(Vec2.dir(d)))) open.add(d);
        return String.join(", ", open);
    }
}
