package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.World;
import com.archon.verb.*;

/**
 * Submission-time validation. Anything knowable from current state is rejected here,
 * for free. State checks apply only to the FIRST stage, because later stages legitimately
 * depend on effects the earlier stages have not produced yet — those failures are the
 * runtime BREAK the design wants.
 */
public final class Validator {

    private final World world;
    private final EventBus bus;

    public Validator(World world, EventBus bus) { this.world = world; this.bus = bus; }

    public Check validate(Ast.Line line) {
        for (int s = 0; s < line.stages().size(); s++) {
            Ast.Stage stage = line.stages().get(s);
            boolean lastStage = s == line.stages().size() - 1;

            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                Verb verb = Verbs.get(inv.verb());

                if (verb == null)
                    return Check.invalid("unknown verb \"" + inv.verb() + "\"",
                            "verbs: " + String.join(" ", Verbs.names()));

                boolean lastInPipe = p == stage.pipeline().size() - 1;

                if (verb.terminal() && !(lastStage && lastInPipe))
                    return Check.invalid("\"" + inv.verb() + "\" must be the final stage of a line",
                            "terminal verbs cannot be chained further");

                if (!lastInPipe && !verb.producesMaterial())
                    return Check.invalid("\"" + inv.verb() + "\" produces nothing to pipe",
                            "producers: take, siphon, pour");

                if (p > 0 && !verb.acceptsMaterial())
                    return Check.invalid("\"" + inv.verb() + "\" has no material slot; cannot receive a pipe",
                            "consumers: throw, pour, wield, drop");

                boolean piped = p > 0;
                VerbContext ctx = new VerbContext(world, inv, bus,
                        piped ? new Material.OfSubstance(com.archon.model.Tag.LIQUID) : null, s == 0);

                Check structural;
                try { structural = verb.validateStructural(ctx); }
                catch (IllegalArgumentException ex) { return Check.invalid(ex.getMessage(), null); }
                if (!structural.valid()) return structural;

                if (s == 0) {
                    Check state;
                    try { state = verb.validateState(ctx); }
                    catch (IllegalArgumentException ex) { return Check.invalid(ex.getMessage(), null); }
                    if (!state.valid()) return state;
                }
            }
        }
        return Check.ok();
    }
}