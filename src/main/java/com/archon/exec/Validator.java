package com.archon.exec;

import com.archon.command.Ast;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.Set;

public final class Validator {

    public record Plan(boolean free, int allocation, int tax) {
        public int requiredAp() {
            return allocation + tax;
        }
    }

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

    public Plan quote(Ast.Line line, RoundState round) {
        boolean free = line.stages().stream().noneMatch(this::paidStage);
        int allocation = line.stages().stream().mapToInt(this::stageCost).sum();
        int tax = free ? 0 : round.rules().taxForNextLine(round.linesUsed());
        return new Plan(free, allocation, tax);
    }

    public Check validateStructure(Ast.Line line) {
        for (int s = 0; s < line.stages().size(); s++) {
            Ast.Stage stage = line.stages().get(s);
            Set<MaterialKind> inputKinds = Set.of();

            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                VerbDefinition def = catalog.get(inv.verb());

                if (def.terminal() && (s != line.stages().size() - 1 || p != stage.pipeline().size() - 1)) {
                    return Check.invalid("\"" + inv.verb() + "\" must be the final command",
                            "terminal verbs cannot be chained further");
                }
                if (p < stage.pipeline().size() - 1 && def.output().isEmpty()) {
                    return Check.invalid("\"" + inv.verb() + "\" produces nothing to pipe",
                            "use a material-producing verb before |");
                }
                if (p > 0 && !def.input().containsAll(inputKinds)) {
                    return Check.invalid("\"" + inv.verb() + "\" cannot receive all producer material kinds",
                            "producer: " + inputKinds + "; accepted: " + def.input());
                }

                Check check = def.action().validateStructural(new ValidationContext(world, inv, p > 0));
                if (!check.valid()) return check;

                inputKinds = def.output();
            }
        }
        return Check.ok();
    }
}