package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Thrall;
import com.archon.model.World;

import java.util.Objects;

/** Shared command inputs available during validation and execution. */
public class ValidationContext {

    public final World world;
    public final Thrall thrall;
    public final Ast.Invocation inv;
    public final Material materialIn;

    /** True for invocations in the first stage of a line. */
    public final boolean firstStage;

    public ValidationContext(
            World world,
            Ast.Invocation inv,
            Material materialIn,
            boolean firstStage
    ) {
        this.world = Objects.requireNonNull(world, "world");
        this.thrall = world.thrall();
        this.inv = Objects.requireNonNull(inv, "inv");
        this.materialIn = materialIn;
        this.firstStage = firstStage;
    }

    public boolean acceptsPipedMaterial() {
        return materialIn != null;
    }
}