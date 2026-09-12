package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;
import com.archon.model.World;
import com.archon.verb.ExitCode;

import java.util.List;

/**
 * Executes stages of a line left to right, evaluating operators (AND, OR, SEQ),
 * detecting boundary interrupts, and tracking execution trace.
 */
public final class StageRunner {

    public record ExecutionTrace(
            ExitCode lastCode,
            int charged,
            boolean broke,
            int breakStage,
            String breakReason,
            String breakHint
    ) {}

    private final World world;
    private final EventBus bus;
    private final PipelineRunner pipelineRunner;

    public StageRunner(World world, EventBus bus, PipelineRunner pipelineRunner) {
        this.world = world;
        this.bus = bus;
        this.pipelineRunner = pipelineRunner;
    }

    public void runFreeLine(Ast.Line line) {
        for (Ast.Stage st : line.stages()) {
            pipelineRunner.runFreePipeline(st);
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
            int cost = Executor.stageCost(stage);

            // 7a. operator branching — never a break
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

                // 7b. interrupt check at the stage boundary
                Actor source = world.pendingInterrupt();
                if (source != null) {
                    int dmg = world.resolveInterrupt(source);
                    bus.post(new GameEvent.InterruptFired(source.id, source.readied().description(), dmg));
                    charged += cost / 2;
                    breakStage = i + 1;
                    breakReason = source.name + " (READIED) interrupted the sequence";
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
                charged += cost / 2;
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