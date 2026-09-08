package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.List;

/**
 * Executes a single atomic stage pipeline, streaming material from upstream
 * verbs to downstream consumers. Upstream stages must succeed.
 */
public final class PipelineRunner {

    private final World world;
    private final EventBus bus;

    public PipelineRunner(World world, EventBus bus) {
        this.world = world;
        this.bus = bus;
    }

    public ExitCode runStage(Ast.Stage stage, boolean firstStage) {
        Material material = null;
        ExitCode code = ExitCode.SUCCESS;
        List<Ast.Invocation> pipe = stage.pipeline();

        for (int p = 0; p < pipe.size(); p++) {
            Ast.Invocation inv = pipe.get(p);
            Verb verb = Verbs.get(inv.verb());
            VerbContext ctx = new VerbContext(world, inv, bus, material, firstStage);
            try {
                code = verb.execute(ctx);
            } catch (RuntimeException ex) {
                bus.narrate("the working falters: " + ex.getMessage());
                return ExitCode.BLOCKED;
            }
            boolean lastInPipe = p == pipe.size() - 1;
            if (!lastInPipe && code != ExitCode.SUCCESS) return ExitCode.BLOCKED;
            material = ctx.materialOut;
        }
        return code;
    }

    public void runFreePipeline(Ast.Stage stage) {
        for (Ast.Invocation inv : stage.pipeline()) {
            Verb v = Verbs.get(inv.verb());
            if (v != null) {
                v.execute(new VerbContext(world, inv, bus, null, true));
            }
        }
    }
}
