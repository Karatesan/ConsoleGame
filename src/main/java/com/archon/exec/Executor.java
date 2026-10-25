package com.archon.exec;

import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.World;
import com.archon.system.combat.Reaction;
import com.archon.verb.*;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class Executor {

    private record StepResult(ExitCode code, Material output) {}

    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final VerbCatalog catalog;
    private final CommandParser parser;
    private final Validator validator;
    private final Interrupts interrupts;
    private final Supplier<List<String>> tick;

    public Executor(World world, EventBus bus, RoundState round, VerbCatalog catalog,
                    Interrupts interrupts, Supplier<List<String>> tick) {
        this.world = world;
        this.bus = bus;
        this.round = round;
        this.catalog = catalog;
        this.parser = new CommandParser(catalog);
        this.validator = new Validator(world, catalog);
        this.interrupts = interrupts;
        this.tick = tick;
    }

    public RoundState round() {
        return round;
    }

    public void submit(String raw) {
        Ast.Line line;
        try {
            line = parser.parse(raw);
        } catch (CommandParser.ParseError err) {
            bus.post(new GameEvent.Rejected(ExitCode.INVALID, err.getMessage(), err.hint));
            return;
        }

        if (line.stages().isEmpty()) return;

        Validator.Plan plan = validator.checkAndQuote(line, round);
        if (!plan.check().valid()) {
            Check c = plan.check();
            bus.post(new GameEvent.Rejected(c.code(), c.reason(), c.hint()));
            return;
        }

        if (!plan.free()) {
            round.spend(plan.tax());
            round.countLine();
            world.resetThrallMovement();
            bus.post(new GameEvent.LineStart(line.raw(), round.linesUsed(), plan.allocation(), plan.tax(), line.isChain()));
        }

        executeAndSettle(line, plan);
    }

    private void executeAndSettle(Ast.Line line, Validator.Plan plan) {
        ExitCode lastCode = ExitCode.SUCCESS;
        int charged = 0;
        boolean paidExecuted = false;
        boolean terminalExecuted = false;
        int brokeStage = -1;
        String brokeReason = null;

        for (int i = 0; i < line.stages().size(); i++) {
            Ast.Stage stage = line.stages().get(i);
            int stageNum = i + 1;
            int cost = validator.stageCost(stage);

            boolean shouldRun = switch (stage.op()) {
                case AND -> lastCode == ExitCode.SUCCESS;
                case OR -> lastCode != ExitCode.SUCCESS;
                default -> true;
            };
            if (!shouldRun) {
                String reason = stage.op() == Ast.Op.AND ? "&& requires SUCCESS" : "|| requires ordinary failure";
                bus.post(new GameEvent.StageSkipped(stageNum, stage.render(), reason));
                continue;
            }

            if (paidExecuted && validator.paidStage(stage)) {
                Reaction reaction = interrupts.fire().orElse(null);
                if (reaction != null) {
                    lastCode = ExitCode.INTERRUPT;
                    charged += round.rules().interruptedCharge(cost);
                    brokeStage = stageNum;
                    brokeReason = reaction.sourceName() + " (READIED) interrupted the sequence";
                    break;
                }
            }

            Material material = null;
            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                VerbDefinition def = catalog.get(inv.verb());

                if (p > 0 && material == null) {
                    bus.narrate("no material was produced for " + inv.verb());
                    lastCode = ExitCode.BLOCKED;
                    break;
                }

                StepResult res = runInvocation(def, inv, material);
                lastCode = res.code();
                paidExecuted |= !def.free();
                terminalExecuted |= (def.terminal() && lastCode == ExitCode.SUCCESS);

                if (lastCode != ExitCode.SUCCESS) break;
                material = res.output();
            }

            if (lastCode == ExitCode.INTERRUPT) {
                charged += round.rules().interruptedCharge(cost);
                brokeStage = stageNum;
                brokeReason = "interrupted during stage " + stageNum;
                break;
            }

            charged += cost;
            bus.post(new GameEvent.StageResult(stageNum, line.stages().size(), stage.render(), lastCode, cost, round.ap() - charged));
        }

        boolean broke = brokeStage != -1;
        if (broke) {
            int forfeited = plan.free() ? 0 : round.rules().breakCharge(plan.allocation(), round.ap());
            round.spend(forfeited);
            bus.post(new GameEvent.LineBroke(brokeStage, brokeReason, plan.allocation(),
                    plan.free() ? 0 : round.rules().breakPenalty(), forfeited, round.ap(), null));
        } else if (!plan.free()) {
            round.spend(charged);
            if (paidExecuted && world.thrall().alive()) {
                interrupts.fire();
            }
            bus.post(new GameEvent.LineComplete(charged, plan.tax(), plan.allocation() - charged, round.ap()));
        }

        if (!world.thrall().alive()) {
            bus.post(new GameEvent.ThrallDied());
        } else if ((!plan.free() && round.ap() == 0) || (!broke && terminalExecuted)) {
            endRound();
        }
    }

    private StepResult runInvocation(VerbDefinition def, Ast.Invocation inv, Material input) {
        if (input != null && !def.input().contains(kind(input))) {
            throw new IllegalStateException("unexpected input kind for " + inv.verb());
        }
        VerbContext ctx = new VerbContext(world, inv, bus, input);
        ExitCode code = Objects.requireNonNull(def.action().execute(ctx), "verb returned no outcome");
        if (code == ExitCode.INVALID) {
            throw new IllegalStateException(inv.verb() + " returned INVALID after submission validation");
        }
        if (ctx.materialOut != null && !def.output().contains(kind(ctx.materialOut))) {
            throw new IllegalStateException("undeclared output kind from " + inv.verb());
        }
        return new StepResult(code, ctx.materialOut);
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

    public void endRound() {
        List<String> worldLog = tick.get();
        bus.post(new GameEvent.RoundEnd(round.reset(), worldLog));
        bus.post(world.thrall().alive() ? new GameEvent.RoundStart(round.roundNo(), round.ap()) : new GameEvent.ThrallDied());
    }
}