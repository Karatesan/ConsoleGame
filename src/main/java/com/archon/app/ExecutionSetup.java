package com.archon.app;

import com.archon.command.CommandParser;
import com.archon.command.InvocationParser;
import com.archon.event.EventBus;
import com.archon.exec.*;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.verb.CommandManual;
import com.archon.verb.VerbCatalog;

/** Builds the application's execution collaborators. */
public final class ExecutionSetup {

    private ExecutionSetup() {}

    public static Executor create(World world, EventBus bus, RoundState round) {
        CommandManual manual = new CommandManual(round.rules());
        VerbCatalog catalog = DefaultVerbs.create(manual);
        manual.bind(catalog);
        ExecutionPolicy policy = new ExecutionPolicy(catalog);
        CommandParser parser = new CommandParser(new InvocationParser(catalog));
        Validator validator = new Validator(world, catalog);
        Auditor auditor = new Auditor(world, bus, round, validator, policy);
        PipelineRunner pipelines = new PipelineRunner(world, bus, catalog);
        Interrupts interrupts = new Interrupts(
                () -> ReactionSystem.pendingInterrupt(world),
                actor -> ReactionSystem.resolveInterrupt(world, actor),
                bus
        );
        StageRunner stages = new StageRunner(bus, pipelines, policy, interrupts);
        SettlementManager settlement = new SettlementManager(world, bus, round, policy, interrupts);

        return new Executor(
                world, bus, round, parser, validator, auditor, stages, settlement, policy,
                () -> SimulationSystem.tick(world)
        );
    }
}