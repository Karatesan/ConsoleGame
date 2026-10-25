package com.archon.verb;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.World;

import java.util.Objects;

public final class VerbContext extends ValidationContext {

    public final EventBus bus;
    public final Material materialIn;
    public Material materialOut;

    public VerbContext(World world, Ast.Invocation inv, EventBus bus, Material materialIn) {
        super(world, inv, materialIn != null);
        this.bus = Objects.requireNonNull(bus, "bus");
        this.materialIn = materialIn;
    }

    public void say(String text) {
        bus.narrate(text);
    }
}