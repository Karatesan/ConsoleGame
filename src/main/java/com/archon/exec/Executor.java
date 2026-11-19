package com.archon.exec;

import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;
import com.archon.model.Tag;
import com.archon.model.World;
import com.archon.system.combat.Reaction;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileReactions;
import com.archon.verb.*;

import java.util.ArrayList;
import java.util.List;

public final class Executor {

    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final VerbCatalog catalog;
    private final CommandParser parser;
    private final Validator validator;
    private final Interrupts interrupts;
    private final Runnable tick;

    public Executor(World world, EventBus bus, RoundState round, VerbCatalog catalog,
                    Interrupts interrupts, Runnable tick) {
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

        if (isBlockedByFrozenOrStunned(line)) {
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
            if (!world.thrall().alive()) {
                break;
            }
            Ast.Stage stage = line.stages().get(i);
            int stageNum = i + 1;

            if (!shouldRun(stage.op(), lastCode)) {
                String reason = stage.op() == Ast.Op.AND ? "&& requires SUCCESS" : "|| requires ordinary failure";
                bus.post(new GameEvent.StageSkipped(stageNum, line.stages().size(), stage.render(), reason));
                continue;
            }

            if (paidExecuted && triggersInterrupts(stage)) {
                Reaction reaction = interrupts.fire().orElse(null);
                if (reaction != null) {
                    onInterruptDamage(reaction);
                    breakLine(stageNum, reaction.sourceName() + " (READIED) interrupted the sequence", plan);
                    return;
                }
            }

            int cost = validator.stageCost(stage);
            bus.post(new GameEvent.StageStart(stageNum, line.stages().size(), stage.render()));
            Actor thrall = world.thrall();
            boolean releaseChannelPipe = thrall != null && stage.pipeline().size() > 1
                    && "channel".equals(stage.pipeline().get(0).verb())
                    && !stage.pipeline().get(0).hasFlag("force")
                    && !stage.pipeline().get(0).hasFlag("blood");
            Tag savedChannel = releaseChannelPipe ? thrall.channeledPayload : null;
            int savedChannelTurns = releaseChannelPipe ? thrall.channelTurns : 0;

            int failedAt = -1;
            Material material = null;
            for (int p = 0; p < stage.pipeline().size(); p++) {
                Ast.Invocation inv = stage.pipeline().get(p);
                if (p > 0 && material == null) {
                    bus.narrate("no material was produced for " + inv.verb());
                    lastCode = ExitCode.BLOCKED;
                    failedAt = p;
                    break;
                }

                VerbDefinition def = catalog.get(inv.verb());
                VerbContext ctx = new VerbContext(world, inv, bus, material, round, p < stage.pipeline().size() - 1);
                lastCode = def.action().execute(ctx);
                boolean invPaid = !def.free() && !"pass".equals(inv.verb())
                        && !("channel".equals(inv.verb()) && inv.hasFlag("blood"));
                paidExecuted |= invPaid;

                if (lastCode != ExitCode.SUCCESS) {
                    failedAt = p;
                    break;
                }
                material = ctx.materialOut;
            }
            if (releaseChannelPipe && lastCode == ExitCode.BLOCKED && failedAt == 1) {
                thrall.channeledPayload = savedChannel;
                thrall.channelTurns = savedChannelTurns;
            } else if (lastCode == ExitCode.BLOCKED && thrall != null && thrall.alive()
                    && material instanceof Material.OfSubstance(Tag sub)) {
                List<String> unconsumedLog = new ArrayList<>();
                if (sub == Tag.FIRE || sub == Tag.INFERNO) {
                    ThermalState.detonateUnstableSiphon(world, thrall, sub, unconsumedLog);
                } else if (sub == Tag.WATER || sub == Tag.OIL || sub == Tag.ACID) {
                    unconsumedLog.add("Uncontained " + ThermalState.payloadLabel(sub) + " spills over " + thrall.name() + "!");
                    TileReactions.spillSubstance(world, thrall.pos(), sub, unconsumedLog);
                }
                for (int u = 0; u < unconsumedLog.size(); u++) {
                    bus.narrate(unconsumedLog.get(u));
                }
            }

            if (lastCode == ExitCode.INTERRUPT) {
                breakLine(stageNum, "interrupted during stage " + stageNum, plan);
                return;
            }

            charged += cost;
            Ast.Invocation lastInv = stage.last();
            boolean isTerminal = catalog.get(lastInv.verb()).terminal()
                    && !("pass".equals(lastInv.verb()) && lastInv.hasFlag("roll"));
            terminalExecuted |= (lastCode == ExitCode.SUCCESS && isTerminal);
            bus.post(new GameEvent.StageResult(stageNum, line.stages().size(), stage.render(), lastCode, cost, round.ap() - charged));
        }

        completeLine(plan, charged, paidExecuted, terminalExecuted);
    }

    private boolean triggersInterrupts(Ast.Stage stage) {
        return validator.paidStage(stage) && !"pass".equals(stage.last().verb());
    }

    private boolean isBlockedByFrozenOrStunned(Ast.Line line) {
        Actor thrall = world.thrall();
        if (thrall == null || (!thrall.has(Tag.FROZEN) && !thrall.has(Tag.STUNNED))) {
            return false;
        }
        List<Ast.Stage> stages = line.stages();
        for (int s = 0; s < stages.size(); s++) {
            List<Ast.Invocation> pipeline = stages.get(s).pipeline();
            for (int p = 0; p < pipeline.size(); p++) {
                Ast.Invocation inv = pipeline.get(p);
                VerbDefinition def = catalog.get(inv.verb());
                boolean allowedPlainPass = "pass".equals(inv.verb()) && !inv.hasFlag("roll");
                boolean allowedUtility = def.free() && !"pass".equals(inv.verb())
                        && !"channel".equals(inv.verb()) && !inv.hasFlag("blood");
                if (!allowedPlainPass && !allowedUtility) {
                    String state = thrall.has(Tag.FROZEN) ? "frozen" : "stunned";
                    bus.post(new GameEvent.Rejected(ExitCode.BLOCKED,
                            "Thrall is " + state + " and cannot act.",
                            "only free inspection commands and pass are allowed"));
                    return true;
                }
            }
        }
        return false;
    }

    private void onInterruptDamage(Reaction reaction) {
        if (reaction.damage() > 0 && world.thrall() != null && world.thrall().alive()) {
            List<String> concLog = new ArrayList<>();
            ThermalState.onCreatureDamaged(world, world.thrall(), reaction.damage(), false, concLog);
            for (int i = 0; i < concLog.size(); i++) {
                bus.narrate(concLog.get(i));
            }
        }
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
            round.spend(Math.min(charged, round.ap()));
            if (paidExecuted && world.thrall().alive()) {
                Reaction reaction = interrupts.fire().orElse(null);
                if (reaction != null) {
                    onInterruptDamage(reaction);
                }
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
        bus.post(new GameEvent.EnemyPhaseStart());
        tick.run();
        bus.post(new GameEvent.RoundEnd(round.reset()));
        bus.post(world.thrall().alive() ? new GameEvent.RoundStart(round.roundNo(), round.ap()) : new GameEvent.ThrallDied());
    }
}