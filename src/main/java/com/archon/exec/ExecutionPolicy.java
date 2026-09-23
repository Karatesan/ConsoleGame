package com.archon.exec;

import com.archon.command.Ast;
import com.archon.verb.VerbCatalog;

import java.util.Objects;

public final class ExecutionPolicy {

    private final VerbCatalog catalog;

    public ExecutionPolicy(VerbCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** A material pipeline costs only its final invocation. */
    public int stageCost(Ast.Stage stage) {
        return Objects.requireNonNull(catalog.get(stage.last().verb()), "unknown verb").price().cost(stage.last());
    }

    public boolean paidStage(Ast.Stage stage) {
        return stage.pipeline().stream().anyMatch(
                invocation -> !Objects.requireNonNull(catalog.get(invocation.verb()), "unknown verb").free());
    }

    public SubmissionQuote quote(Ast.Line line, RoundState round) {
        boolean free = line.stages().stream().noneMatch(this::paidStage);
        int allocation = line.stages().stream().mapToInt(this::stageCost).reduce(0, Math::addExact);
        return new SubmissionQuote(free, allocation, round);
    }
}