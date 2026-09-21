package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.Objects;

public final class PipelineRunner {

    private final World world;
    private final EventBus bus;
    private final VerbCatalog catalog;

    public PipelineRunner(World world, EventBus bus, VerbCatalog catalog) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public ExitCode runStage(Ast.Stage stage, boolean firstStage) {
        Material material = null;
        ExitCode code = ExitCode.SUCCESS;

        for (Ast.Invocation inv : stage.pipeline()) {
            if (code != ExitCode.SUCCESS) return ExitCode.BLOCKED;
            VerbContext ctx = new VerbContext(world, inv, bus, material, firstStage);
            try {
                code = catalog.get(inv.verb()).action().execute(ctx);
            } catch (RuntimeException ex) {
                bus.narrate("the working falters: " + ex.getMessage());
                return ExitCode.BLOCKED;
            }
            material = ctx.materialOut;
        }
        return code;
    }

    public void runFreePipeline(Ast.Stage stage) {
        for (Ast.Invocation inv : stage.pipeline()) {
            VerbDefinition definition = catalog.get(inv.verb());
            if (definition != null) {
                definition.action().execute(new VerbContext(world, inv, bus, null, true));
            }
        }
    }
}