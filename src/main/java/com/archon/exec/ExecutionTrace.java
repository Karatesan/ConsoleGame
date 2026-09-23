package com.archon.exec;

import com.archon.verb.ExitCode;

import java.util.List;
import java.util.Optional;

public record ExecutionTrace(List<Stage> stages) {
    public enum State {
        EXECUTED, SKIPPED, NOT_REACHED, INTERRUPTED_BEFORE, INTERRUPTED_DURING
    }

    public record Stage(int index, State state, ExitCode code, int allocation, int charged, boolean paidExecuted,
                        boolean terminalExecuted, String reason) {
        public boolean interrupted() {
            return state == State.INTERRUPTED_BEFORE || state == State.INTERRUPTED_DURING;
        }
    }

    public ExecutionTrace {
        stages = List.copyOf(stages);
    }

    public ExitCode lastCode() {
        for (int i = stages.size() - 1; i >= 0; i--) {
            if (stages.get(i).code() != null) {
                return stages.get(i).code();
            }
        }
        return ExitCode.SUCCESS;
    }

    public int charged() {
        return stages.stream().mapToInt(Stage::charged).reduce(0, Math::addExact);
    }

    public boolean paidExecuted() {
        return stages.stream().anyMatch(Stage::paidExecuted);
    }

    public boolean terminalExecuted() {
        return stages.stream().anyMatch(Stage::terminalExecuted);
    }

    public Optional<Stage> interruption() {
        return stages.stream().filter(Stage::interrupted).findFirst();
    }
}