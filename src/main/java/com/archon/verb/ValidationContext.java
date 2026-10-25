package com.archon.verb;

import com.archon.command.Ast;
import com.archon.model.Thrall;
import com.archon.model.World;

import java.util.Objects;

public class ValidationContext {

    public final World world;
    public final Thrall thrall;
    public final Ast.Invocation inv;

    /** Input is supplied by a pipe, or expected from one during validation. */
    public final boolean pipedInput;

    public ValidationContext(World world, Ast.Invocation inv, boolean pipedInput) {
        this.world = Objects.requireNonNull(world, "world");
        this.thrall = world.thrall();
        this.inv = Objects.requireNonNull(inv, "inv");
        this.pipedInput = pipedInput;
    }
}