package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;
import com.archon.verb.ExitCode;

import java.util.List;
import java.util.Objects;

public final class StageRunner {

    public record ExecutionTrace(
            ExitCode lastCode,
            int charged,
            boolean broke,
            int breakStage,
            String breakReason,
            String breakHint
    ) {}

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

    public void runFreeLine(Ast.Line line) {
        for (Ast.Stage stage : line.stages()) {
            pipelineRunner.runFreePipeline(stage);
        }
    }

    public ExecutionTrace execute(Ast.Line line, RoundState round) {
        List<Ast.Stage> stages = line.stages();
        ExitCode last = ExitCode.SUCCESS;
        int charged = 0;
        int breakStage = -1;
        boolean broke = false;
        String breakReason = null;
        String breakHint = null;

        for (int i = 0; i < stages.size(); i++) {
            Ast.Stage stage = stages.get(i);
            int cost = policy.stageCost(stage);

            // Operator branching — never a break.
            if (i > 0) {
                boolean run = switch (stage.op()) {
                    case AND -> last == ExitCode.SUCCESS;
                    case OR -> last != ExitCode.SUCCESS;
                    default -> true;
                };
                if (!run) {
                    bus.post(new GameEvent.StageSkipped(
                            i + 1,
                            stage.render(),
                            stage.op() == Ast.Op.AND ? "&& requires SUCCESS" : "|| requires failure"
                    ));
                    continue;
                }

                // Interrupt check at the stage boundary.
                Actor source = interrupts.fire();
                if (source != null) {
                    charged += round.rules().interruptedCharge(cost);
                    breakStage = i + 1;
                    breakReason = source.name() + " (READIED) interrupted the sequence";
                    breakHint = "it was flagged READIED in your last scan";
                    broke = true;
                    break;
                }
            }

            ExitCode code = pipelineRunner.runStage(stage, i == 0);
            last = code;

            if (code == ExitCode.BLOCKED || code == ExitCode.INVALID) {
                breakStage = i + 1;
                breakReason = "runtime BLOCKED at stage " + (i + 1) + ": " + stage.render();
                breakHint = "use && to make this stage conditional on the previous one";
                broke = true;
                break;
            }
            if (code == ExitCode.INTERRUPT) {
                charged += round.rules().interruptedCharge(cost);
                breakStage = i + 1;
                breakReason = "interrupted during stage " + (i + 1);
                broke = true;
                break;
            }

            charged += cost;
            bus.post(new GameEvent.StageResult(i + 1, stages.size(), stage.render(), code, cost, round.ap() - charged));
        }

        return new ExecutionTrace(last, charged, broke, breakStage, breakReason, breakHint);
    }
}