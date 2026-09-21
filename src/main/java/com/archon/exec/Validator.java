package com.archon.exec;

import com.archon.command.Ast;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.Objects;

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

            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                VerbDefinition definition = catalog.get(inv.verb());

                if (definition == null) return Check.invalid("unknown verb \"" + inv.verb() + "\"",
                        "verbs: " + String.join(" ", catalog.names()));

                if (definition.terminal() && (s != line.stages().size() - 1 || p != stage.pipeline().size() - 1))
                    return Check.invalid("\"" + inv.verb() + "\" must be the final stage of a line",
                            "terminal verbs cannot be chained further");

                if (p < stage.pipeline().size() - 1 && definition.output().isEmpty())
                    return Check.invalid("\"" + inv.verb() + "\" produces nothing to pipe",
                            "use a material-producing verb before |");

                if (p > 0 && definition.input().isEmpty())
                    return Check.invalid("\"" + inv.verb() + "\" has no material slot; cannot receive a pipe",
                            "the command after | must accept material");

                ValidationContext ctx = new ValidationContext(world, inv,
                        p > 0 ? new Material.OfSubstance(com.archon.model.Tag.LIQUID) : null, s == 0);

                try {
                    Check structural = definition.action().validateStructural(ctx);
                    Check result = structural.valid() && s == 0 ? definition.action().validateState(ctx) : structural;
                    if (!result.valid()) return result;
                } catch (IllegalArgumentException ex) {
                    return Check.invalid(ex.getMessage(), null);
                }
            }
        }
        return Check.ok();
    }
}