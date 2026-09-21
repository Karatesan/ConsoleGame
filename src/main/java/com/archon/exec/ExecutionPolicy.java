package com.archon.exec;

import com.archon.command.Ast;
import com.archon.verb.VerbCatalog;
import com.archon.verb.VerbDefinition;

import java.util.Objects;

/** Shared pricing and line classification under the current economy rules. */
public final class ExecutionPolicy {

    private final VerbCatalog catalog;

    public ExecutionPolicy(VerbCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** A material pipeline costs only its final invocation. */
    public int stageCost(Ast.Stage stage) {
        VerbDefinition definition = catalog.get(stage.last().verb());
        return definition == null ? 0 : definition.price().cost(stage.last());
    }

    public int allocationOf(Ast.Line line) {
        return line.stages().stream().mapToInt(this::stageCost).sum();
    }

    public boolean allFree(Ast.Line line) {
        return line.stages().stream()
                .flatMap(stage -> stage.pipeline().stream())
                .allMatch(invocation -> {
                    VerbDefinition definition = catalog.get(invocation.verb());
                    return definition != null && definition.free();
                });
    }

    /** Preserves syntax-based terminal detection, including skipped stages. */
    public boolean endsWithTerminal(Ast.Line line) {
        if (line.stages().isEmpty()) {
            return false;
        }

        Ast.Stage last = line.stages().getLast();
        VerbDefinition definition = catalog.get(last.last().verb());
        return definition != null && definition.terminal();
    }
}