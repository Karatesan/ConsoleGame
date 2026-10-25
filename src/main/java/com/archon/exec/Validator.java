package com.archon.exec;

import com.archon.command.Ast;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.Set;

public final class Validator {

    public record Plan(Check check, boolean free, int allocation, int tax) {}

    private final World world;
    private final VerbCatalog catalog;

    public Validator(World world, VerbCatalog catalog) {
        this.world = world;
        this.catalog = catalog;
    }

    public int stageCost(Ast.Stage stage) {
        return catalog.get(stage.last().verb()).price().cost(stage.last());
    }

    public boolean paidStage(Ast.Stage stage) {
        return stage.pipeline().stream().anyMatch(inv -> !catalog.get(inv.verb()).free());
    }

    public Plan checkAndQuote(Ast.Line line, RoundState round) {
        Check check = validateStructure(line);
        if (!check.valid()) return new Plan(check, true, 0, 0);

        boolean free = line.stages().stream().noneMatch(this::paidStage);
        int allocation = 0;
        try {
            for (Ast.Stage stage : line.stages()) {
                allocation = Math.addExact(allocation, stageCost(stage));
            }
        } catch (ArithmeticException e) {
            return new Plan(Check.invalid("submitted allocation exceeds the supported AP range", "submit less work"), true, 0, 0);
        }

        int tax = free ? 0 : round.rules().taxForNextLine(round.linesUsed());
        long required = (long) allocation + tax;

        if (required > round.ap()) {
            return new Plan(Check.blocked(
                    String.format("insufficient AP: %d AP tax + %d AP allocation = %d. Available: %d.",
                            tax, allocation, required, round.ap()),
                    "reduce the submitted allocation"), free, allocation, tax);
        }
        return new Plan(Check.ok(), free, allocation, tax);
    }

    private Check validateStructure(Ast.Line line) {
        for (int s = 0; s < line.stages().size(); s++) {
            Ast.Stage stage = line.stages().get(s);
            Set<MaterialKind> inputKinds = Set.of();

            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                VerbDefinition def = catalog.get(inv.verb());

                if (def.terminal() && (s != line.stages().size() - 1 || p != stage.pipeline().size() - 1))
                    return Check.invalid("\"" + inv.verb() + "\" must be the final command", "terminal verbs cannot be chained further");

                if (p < stage.pipeline().size() - 1 && def.output().isEmpty())
                    return Check.invalid("\"" + inv.verb() + "\" produces nothing to pipe", "use a material-producing verb before |");

                if (p > 0 && !def.input().containsAll(inputKinds))
                    return Check.invalid("\"" + inv.verb() + "\" cannot receive all producer material kinds",
                            "producer: " + inputKinds + "; accepted: " + def.input());

                Check check = def.action().validateStructural(new ValidationContext(world, inv, p > 0, null));
                if (!check.valid()) {
                    if (check.code() != ExitCode.INVALID) {
                        throw new IllegalStateException("submission validation returned a gameplay failure");
                    }
                    return check;
                }
                inputKinds = def.output();
            }
        }
        return Check.ok();
    }
}