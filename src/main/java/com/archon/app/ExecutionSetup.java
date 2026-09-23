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

public final class ExecutionSetup {

    private ExecutionSetup() {}

    public static Executor create(World world, EventBus bus, RoundState round) {
        CommandManual manual = new CommandManual(round.rules());
        VerbCatalog catalog = DefaultVerbs.create(manual);
        manual.bind(catalog);
        ExecutionPolicy policy = new ExecutionPolicy(catalog);
        CommandParser parser = new CommandParser(new InvocationParser(catalog));
        SubmissionChecks submissions = new SubmissionChecks(new Validator(world, catalog), policy, round);
        Auditor auditor = new Auditor(world, bus, round, submissions);
        PipelineRunner pipelines = new PipelineRunner(new InvocationRunner(world, bus, catalog));
        Interrupts interrupts = new Interrupts(() -> ReactionSystem.fire(world), bus);
        StageRunner stages = new StageRunner(bus, pipelines, policy, interrupts);
        SettlementManager settlement = new SettlementManager(world, bus, round, interrupts);

        return new Executor(world, bus, round, parser, submissions, auditor, stages, settlement,
                () -> SimulationSystem.tick(world));
    }
}