package com.archon.exec;

import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.World;
import com.archon.system.combat.Reaction;
import com.archon.verb.*;

import java.util.List;
import java.util.function.Supplier;

public final class Executor {

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

        Check check = validator.validateStructure(line);
        if (!check.valid()) {
            bus.post(new GameEvent.Rejected(ExitCode.INVALID, check.reason(), check.hint()));
            return;
        }

        Validator.Plan plan = validator.quote(line, round);
        if (plan.requiredAp() > round.ap()) {
            String reason = String.format("insufficient AP: %d AP tax + %d AP allocation = %d. Available: %d.",
                    plan.tax(), plan.allocation(), plan.requiredAp(), round.ap());
            bus.post(new GameEvent.Rejected(ExitCode.BLOCKED, reason, "reduce the submitted allocation"));
            return;
        }

        if (!plan.free()) {
            round.spend(plan.tax());
            round.countLine();
            world.resetThrallMovement();
            bus.post(new GameEvent.LineStart(line.raw(), round.linesUsed(), plan.allocation(), plan.tax(), line.isChain()));
        }

        executeLine(line, plan);
    }

    private void executeLine(Ast.Line line, Validator.Plan plan) {
        ExitCode lastCode = ExitCode.SUCCESS;
        int charged = 0;
        boolean paidExecuted = false;
        boolean terminalExecuted = false;

        for (int i = 0; i < line.stages().size(); i++) {
            Ast.Stage stage = line.stages().get(i);
            int stageNum = i + 1;

            if (!shouldRun(stage.op(), lastCode)) {
                String reason = stage.op() == Ast.Op.AND ? "&& requires SUCCESS" : "|| requires ordinary failure";
                bus.post(new GameEvent.StageSkipped(stageNum, line.stages().size(), stage.render(), reason));
                continue;
            }

            if (paidExecuted && validator.paidStage(stage)) {
                Reaction reaction = interrupts.fire().orElse(null);
                if (reaction != null) {
                    breakLine(stageNum, reaction.sourceName() + " (READIED) interrupted the sequence", plan);
                    return;
                }
            }

            bus.post(new GameEvent.StageStart(stageNum, line.stages().size(), stage.render()));
            Material material = null;
            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                if (p > 0 && material == null) {
                    bus.narrate("no material was produced for " + inv.verb());
                    lastCode = ExitCode.BLOCKED;
                    break;
                }

                VerbDefinition def = catalog.get(inv.verb());
                VerbContext ctx = new VerbContext(world, inv, bus, material);
                lastCode = def.action().execute(ctx);
                paidExecuted |= !def.free();

                if (lastCode != ExitCode.SUCCESS) break;
                material = ctx.materialOut;
            }

            if (lastCode == ExitCode.INTERRUPT) {
                breakLine(stageNum, "interrupted during stage " + stageNum, plan);
                return;
            }

            int cost = validator.stageCost(stage);
            charged += cost;
            terminalExecuted |= (lastCode == ExitCode.SUCCESS && catalog.get(stage.last().verb()).terminal());
            bus.post(new GameEvent.StageResult(stageNum, line.stages().size(), stage.render(), lastCode, cost, round.ap() - charged));
        }

        completeLine(plan, charged, paidExecuted, terminalExecuted);
    }

    private static boolean shouldRun(Ast.Op op, ExitCode lastCode) {
        return switch (op) {
            case AND -> lastCode == ExitCode.SUCCESS;
            case OR -> lastCode != ExitCode.SUCCESS;
            case NONE, SEQ -> true;
        };
    }

    private void breakLine(int stageNum, String reason, Validator.Plan plan) {
        int forfeited = round.rules().breakCharge(plan.allocation(), round.ap());
        round.spend(forfeited);
        bus.post(new GameEvent.LineBroke(stageNum, reason, plan.allocation(),
                round.rules().breakPenalty(), forfeited, round.ap(), null));
        finishTurn(false);
    }

    private void completeLine(Validator.Plan plan, int charged, boolean paidExecuted, boolean terminalExecuted) {
        if (!plan.free()) {
            round.spend(charged);
            if (paidExecuted && world.thrall().alive()) {
                interrupts.fire();
            }
            bus.post(new GameEvent.LineComplete(charged, plan.tax(), plan.allocation() - charged, round.ap()));
        }
        finishTurn(terminalExecuted);
    }

    private void finishTurn(boolean terminalExecuted) {
        if (!world.thrall().alive()) {
            bus.post(new GameEvent.ThrallDied());
        } else if (round.ap() == 0 || terminalExecuted) {
            endRound();
        }
    }

    public void endRound() {
        List<String> worldLog = tick.get();
        bus.post(new GameEvent.RoundEnd(round.reset(), worldLog));
        bus.post(world.thrall().alive() ? new GameEvent.RoundStart(round.roundNo(), round.ap()) : new GameEvent.ThrallDied());
    }
}