package com.archon.app;

import com.archon.event.EventBus;
import com.archon.exec.Executor;
import com.archon.exec.Interrupts;
import com.archon.exec.RoundState;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.verb.VerbCatalog;

public final class ExecutionSetup {

    private ExecutionSetup() {}

    public static Executor create(World world, EventBus bus, RoundState round) {
        VerbCatalog catalog = DefaultVerbs.create(round.rules());
        Interrupts interrupts = new Interrupts(() -> ReactionSystem.fire(world), bus);
        return new Executor(world, bus, round, catalog, interrupts, () -> SimulationSystem.tick(world));
    }
}