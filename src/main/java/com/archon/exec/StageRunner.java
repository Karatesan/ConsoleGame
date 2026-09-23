package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.system.combat.Reaction;
import com.archon.verb.ExitCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StageRunner {
    private final EventBus bus;
    private final PipelineRunner pipelineRunner;
    private final ExecutionPolicy policy;
    private final Interrupts interrupts;

    public StageRunner(EventBus bus, PipelineRunner pipelineRunner, ExecutionPolicy policy, Interrupts interrupts) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.pipelineRunner = Objects.requireNonNull(pipelineRunner, "pipelineRunner");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.interrupts = Objects.requireNonNull(interrupts, "interrupts");
    }

    public ExecutionTrace execute(Ast.Line line, RoundState round) {
        List<ExecutionTrace.Stage> results = new ArrayList<>();
        ExitCode last = ExitCode.SUCCESS;
        int charged = 0;
        boolean paidExecuted = false;
        boolean stopped = false;

        for (int i = 0; i < line.stages().size(); i++) {
            Ast.Stage stage = line.stages().get(i);
            int cost = policy.stageCost(stage);
            if (stopped) {
                results.add(
                        new ExecutionTrace.Stage(i + 1, ExecutionTrace.State.NOT_REACHED, null, cost, 0, false, false,
                                "line interrupted"));
                continue;
            }

            boolean run = switch (stage.op()) {
                case AND -> last == ExitCode.SUCCESS;
                case OR -> last != ExitCode.SUCCESS;
                default -> true;
            };
            if (!run) {
                String reason = stage.op() == Ast.Op.AND ? "&& requires SUCCESS" : "|| requires ordinary failure";
                results.add(new ExecutionTrace.Stage(i + 1, ExecutionTrace.State.SKIPPED, null, cost, 0, false, false,
                        reason));
                bus.post(new GameEvent.StageSkipped(i + 1, stage.render(), reason));
                continue;
            }

            Reaction reaction = paidExecuted && policy.paidStage(stage) ? interrupts.fire().orElse(null) : null;
            if (reaction != null) {
                results.add(new ExecutionTrace.Stage(i + 1, ExecutionTrace.State.INTERRUPTED_BEFORE, ExitCode.INTERRUPT,
                        cost, round.rules().interruptedCharge(cost), false, false,
                        reaction.sourceName() + " (READIED) interrupted the sequence"));
                stopped = true;
                continue;
            }

            PipelineRunner.Result result = pipelineRunner.runStage(stage);
            last = result.code();
            paidExecuted |= result.paidExecuted();
            stopped = last == ExitCode.INTERRUPT;
            int charge = stopped ? round.rules().interruptedCharge(cost) : cost;
            charged += charge;
            results.add(new ExecutionTrace.Stage(i + 1,
                    stopped ? ExecutionTrace.State.INTERRUPTED_DURING : ExecutionTrace.State.EXECUTED, last, cost,
                    charge, result.paidExecuted(), result.terminalExecuted(),
                    stopped ? "interrupted during stage " + (i + 1) : null));
            if (!stopped) {
                bus.post(new GameEvent.StageResult(i + 1, line.stages().size(), stage.render(), last, charge,
                        round.ap() - charged));
            }
        }
        return new ExecutionTrace(results);
    }
}