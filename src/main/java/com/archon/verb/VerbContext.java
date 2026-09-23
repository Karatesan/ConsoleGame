package com.archon.verb;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.World;

public final class VerbContext extends ValidationContext {
    public final EventBus bus;
    public Material materialOut;

    public VerbContext(
            World world,
            Ast.Invocation inv,
            EventBus bus,
            Material materialIn
    ) {
        super(world, inv, materialIn != null, materialIn);
        this.bus = java.util.Objects.requireNonNull(bus, "bus");
    }

    public void say(String text) {
        bus.narrate(text);
    }
}