package com.archon.exec;

import com.archon.command.Ast;
import com.archon.verb.ExitCode;
import com.archon.verb.Material;

import java.util.Objects;

public final class PipelineRunner {
    public record Result(ExitCode code, boolean paidExecuted, boolean terminalExecuted) {}

    private final InvocationRunner invocations;

    public PipelineRunner(InvocationRunner invocations) {
        this.invocations = Objects.requireNonNull(invocations, "invocations");
    }

    public Result runStage(Ast.Stage stage) {
        Material material = null;
        boolean paid = false;
        boolean terminal = false;

        for (int i = 0; i < stage.pipeline().size(); i++) {
            InvocationRunner.Result result = invocations.run(
                    stage.pipeline().get(i), material, i > 0);
            paid |= result.paid();
            terminal |= result.terminal();
            if (result.code() != ExitCode.SUCCESS) {
                return new Result(result.code(), paid, terminal);
            }
            material = result.output();
        }
        return new Result(ExitCode.SUCCESS, paid, terminal);
    }
}