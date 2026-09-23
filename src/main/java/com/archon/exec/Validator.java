package com.archon.exec;

import com.archon.command.Ast;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.Objects;
import java.util.Set;

public final class Validator {

    private final World world;
    private final VerbCatalog catalog;

    public Validator(World world, VerbCatalog catalog) {
        this.world = Objects.requireNonNull(world, "world");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public Check validate(Ast.Line line) {
        for (int s = 0; s < line.stages().size(); s++) {
            Ast.Stage stage = line.stages().get(s);
            Set<MaterialKind> inputKinds = Set.of();

            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                VerbDefinition definition = catalog.get(inv.verb());

                if (definition == null) return Check.invalid("unknown verb \"" + inv.verb() + "\"",
                        "verbs: " + String.join(" ", catalog.names()));

                if (definition.terminal() && (s != line.stages().size() - 1 || p != stage.pipeline().size() - 1))
                    return Check.invalid("\"" + inv.verb() + "\" must be the final command",
                            "terminal verbs cannot be chained further");

                if (p < stage.pipeline().size() - 1 && definition.output().isEmpty())
                    return Check.invalid("\"" + inv.verb() + "\" produces nothing to pipe",
                            "use a material-producing verb before |");

                if (p > 0 && !definition.input().containsAll(inputKinds))
                    return Check.invalid("\"" + inv.verb() + "\" cannot receive all producer material kinds",
                            "producer: " + inputKinds + "; accepted: " + definition.input());

                Check check = definition.action().validateStructural(new ValidationContext(world, inv, p > 0, null));
                if (!check.valid()) {
                    if (check.code() != ExitCode.INVALID) {
                        throw new IllegalStateException("submission validation returned a gameplay failure");
                    }
                    return check;
                }
                inputKinds = definition.output();
            }
        }
        return Check.ok();
    }
}