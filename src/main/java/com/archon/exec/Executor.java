package com.archon.exec;

import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.List;

/**
 * High-level orchestration façade implementing Action Economy Spec v3.0 §8.
 * Delegates pipeline execution to StageRunner/PipelineRunner and AP settlement
 * to SettlementManager.
 */
public final class Executor {

    public enum Kind {NOOP, FREE, AUDIT, REJECTED, COMPLETE, BROKE}

    public record Outcome(
            Kind kind, ExitCode lastCode, int tax, int allocation,
            int charged, int forfeited, int apAfter, int linesUsed,
            boolean roundOver) {}

    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final CommandParser parser = new CommandParser();
    private final Validator validator;
    private final Auditor auditor;
    private final PipelineRunner pipelineRunner;
    private final StageRunner stageRunner;
    private final SettlementManager settlementManager;

    public Executor(World world, EventBus bus, RoundState round) {
        this.world = world;
        this.bus = bus;
        this.round = round;
        this.validator = new Validator(world, bus);
        this.auditor = new Auditor(world, bus, round);
        this.pipelineRunner = new PipelineRunner(world, bus);
        this.stageRunner = new StageRunner(world, bus, pipelineRunner);
        this.settlementManager = new SettlementManager(world, bus, round);
    }

    public RoundState round() { return round; }

    public Outcome submit(String raw) {
        // ---- 1. parse (free on failure) ----
        Ast.Line line;
        try {
            line = parser.parse(raw);
        } catch (CommandParser.ParseError e) {
            bus.post(new GameEvent.Rejected(ExitCode.INVALID, e.getMessage(), e.hint));
            return out(Kind.REJECTED, ExitCode.INVALID, 0, 0, 0, 0, false);
        }
        if (line.stages().isEmpty()) return out(Kind.NOOP, ExitCode.SUCCESS, 0, 0, 0, 0, false);

        // ---- 2. audits are free and never count as a line ----
        if (line.dryRun()) {
            auditor.audit(line);
            return out(Kind.AUDIT, ExitCode.SUCCESS, 0, allocationOf(line), 0, 0, false);
        }

        // ---- 3. free-only lines bypass the economy entirely ----
        if (allFree(line)) {
            stageRunner.runFreeLine(line);
            return out(Kind.FREE, ExitCode.SUCCESS, 0, 0, 0, 0, SettlementManager.endsWithTerminal(line));
        }

        // ---- 4. validate (free on failure, does not consume a line) ----
        Check v = validator.validate(line);
        if (!v.valid()) {
            bus.post(new GameEvent.Rejected(v.code(), v.reason(), v.hint()));
            return out(Kind.REJECTED, v.code(), 0, allocationOf(line), 0, 0, false);
        }

        int allocation = allocationOf(line);
        int tax = round.taxForNextLine();

        // ---- 5. affordability (free on failure) ----
        if (allocation + tax > round.ap()) {
            bus.post(new GameEvent.Rejected(ExitCode.BLOCKED, String.format(
                    "insufficient AP — line %d this round: %d AP tax + %d AP allocation = %d. Available: %d.",
                    round.linesUsed() + 1, tax, allocation, allocation + tax, round.ap()),
                    tax > 0 ? "chaining this onto your previous line would have avoided the tax" : "reduce allocation or use lighter flags"));
            return out(Kind.REJECTED, ExitCode.BLOCKED, tax, allocation, 0, 0, false);
        }

        // ---- 6. commit: tax is charged now and is never refunded ----
        round.spend(tax);
        round.countLine();
        world.thrallMovedThisLine = false;
        bus.post(new GameEvent.LineStart(line.raw(), round.linesUsed(), allocation, tax, line.isChain()));

        // ---- 7. execute stages, left to right ----
        StageRunner.ExecutionTrace trace = stageRunner.execute(line, round);

        // ---- 8. settle ----
        return settlementManager.settle(line, allocation, tax, trace);
    }

    public void endRound() {
        int wasted = round.ap();
        List<String> worldLog = world.tick();
        round.reset();
        bus.post(new GameEvent.RoundEnd(wasted, worldLog));
        bus.post(new GameEvent.RoundStart(round.roundNo(), round.ap()));
    }

    // ---- cost model ----

    public static int stageCost(Ast.Stage stage) {
        Verb v = Verbs.get(stage.last().verb());
        return v == null ? 0 : v.apCost(stage.last());
    }

    public static int allocationOf(Ast.Line line) {
        return line.stages().stream().mapToInt(Executor::stageCost).sum();
    }

    private static boolean allFree(Ast.Line line) {
        return line.stages().stream().flatMap(s -> s.pipeline().stream()).allMatch(i -> {
            Verb v = Verbs.get(i.verb());
            return v != null && v.free();
        });
    }

    private Outcome out(Kind k, ExitCode c, int tax, int alloc, int charged, int forfeited, boolean roundOver) {
        return new Outcome(k, c, tax, alloc, charged, forfeited, round.ap(), round.linesUsed(), roundOver);
    }

    public static boolean endsWithTerminal(Ast.Line line) {
        return SettlementManager.endsWithTerminal(line);
    }
}