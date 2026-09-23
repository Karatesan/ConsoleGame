package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.Objects;

public final class InvocationRunner {
    public record Result(ExitCode code, Material output, boolean paid, boolean terminal) {}

    private final World world;
    private final EventBus bus;
    private final VerbCatalog catalog;

    public InvocationRunner(World world, EventBus bus, VerbCatalog catalog) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public Result run(Ast.Invocation invocation, Material input, boolean piped) {
        VerbDefinition definition = Objects.requireNonNull(catalog.get(invocation.verb()), "unknown invocation");
        if (piped && input == null) {
            bus.narrate("no material was produced for " + invocation.verb());
            return new Result(ExitCode.BLOCKED, null, false, false);
        }
        if (input != null && !definition.input().contains(kind(input))) {
            throw new IllegalStateException("unexpected input kind for " + invocation.verb());
        }

        VerbContext context = new VerbContext(world, invocation, bus, input);
        ExitCode code = Objects.requireNonNull(definition.action().execute(context), "verb returned no outcome");
        if (code == ExitCode.INVALID) {
            throw new IllegalStateException(invocation.verb() + " returned INVALID after submission validation");
        }
        if (context.materialOut != null && !definition.output().contains(kind(context.materialOut))) {
            throw new IllegalStateException("undeclared output kind from " + invocation.verb());
        }

        return new Result(code, context.materialOut, !definition.free(),
                definition.terminal() && code == ExitCode.SUCCESS);
    }

    private static MaterialKind kind(Material material) {
        return switch (material) {
            case Material.OfItem item -> {
                Objects.requireNonNull(item.item(), "material item");
                yield MaterialKind.ITEM;
            }
            case Material.OfSubstance substance -> {
                Objects.requireNonNull(substance.substance(), "material substance");
                yield MaterialKind.SUBSTANCE;
            }
        };
    }
}