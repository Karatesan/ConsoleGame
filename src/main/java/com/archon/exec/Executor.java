package com.archon.exec;

import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.World;
import com.archon.verb.Check;
import com.archon.verb.ExitCode;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class Executor {

    public enum Kind {NOOP, FREE, AUDIT, REJECTED, COMPLETE, BROKE}

    public record Outcome(
            Kind kind, ExitCode lastCode, int tax, int allocation,
            int charged, int forfeited, int apAfter, int linesUsed,
            boolean roundOver) {}

    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final CommandParser parser;
    private final Validator validator;
    private final Auditor auditor;
    private final StageRunner stageRunner;
    private final SettlementManager settlementManager;
    private final ExecutionPolicy policy;
    private final Supplier<List<String>> tick;

    public Executor(
            World world,
            EventBus bus,
            RoundState round,
            CommandParser parser,
            Validator validator,
            Auditor auditor,
            StageRunner stageRunner,
            SettlementManager settlementManager,
            ExecutionPolicy policy,
            Supplier<List<String>> tick
    ) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.round = Objects.requireNonNull(round, "round");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.auditor = Objects.requireNonNull(auditor, "auditor");
        this.stageRunner = Objects.requireNonNull(stageRunner, "stageRunner");
        this.settlementManager = Objects.requireNonNull(settlementManager, "settlementManager");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    public RoundState round() {
        return round;
    }

    public Outcome submit(String raw) {
        // Parse failure is free.
        Ast.Line line;
        try {
            line = parser.parse(raw);
        } catch (CommandParser.ParseError e) {
            bus.post(new GameEvent.Rejected(ExitCode.INVALID, e.getMessage(), e.hint));
            return out(Kind.REJECTED, ExitCode.INVALID, 0, 0, 0, 0, false);
        }
        if (line.stages().isEmpty()) {
            return out(Kind.NOOP, ExitCode.SUCCESS, 0, 0, 0, 0, false);
        }

        // Audits never spend AP or count as a line.
        if (line.dryRun()) {
            auditor.audit(line);
            return out(Kind.AUDIT, ExitCode.SUCCESS, 0, policy.allocationOf(line), 0, 0, false);
        }

        // Preserve the existing separate free execution path.
        if (policy.allFree(line)) {
            stageRunner.runFreeLine(line);
            return out(Kind.FREE, ExitCode.SUCCESS, 0, 0, 0, 0,
                    policy.endsWithTerminal(line));
        }

        Check v = validator.validate(line);
        if (!v.valid()) {
            bus.post(new GameEvent.Rejected(v.code(), v.reason(), v.hint()));
            return out(Kind.REJECTED, v.code(), 0, policy.allocationOf(line), 0, 0, false);
        }

        int allocation = policy.allocationOf(line);
        int tax = round.rules().taxForNextLine(round.linesUsed());

        if (allocation + tax > round.ap()) {
            bus.post(new GameEvent.Rejected(
                    ExitCode.BLOCKED,
                    String.format(
                            "insufficient AP — line %d this round: %d AP tax + %d AP allocation = %d. Available: %d.",
                            round.linesUsed() + 1, tax, allocation, allocation + tax, round.ap()),
                    tax > 0
                            ? "chaining this onto your previous line would have avoided the tax"
                            : "reduce allocation or use lighter flags"));
            return out(Kind.REJECTED, ExitCode.BLOCKED, tax, allocation, 0, 0, false);
        }

        // Commit: line tax is never refunded.
        round.spend(tax);
        round.countLine();
        world.resetThrallMovement();
        bus.post(new GameEvent.LineStart(
                line.raw(), round.linesUsed(), allocation, tax, line.isChain()));

        StageRunner.ExecutionTrace trace = stageRunner.execute(line, round);
        return settlementManager.settle(line, allocation, tax, trace);
    }

    public void endRound() {
        int wasted = round.ap();
        List<String> worldLog = tick.get();
        round.reset();
        bus.post(new GameEvent.RoundEnd(wasted, worldLog));
        bus.post(new GameEvent.RoundStart(round.roundNo(), round.ap()));
    }

    private Outcome out(
            Kind kind,
            ExitCode code,
            int tax,
            int allocation,
            int charged,
            int forfeited,
            boolean roundOver) {
        return new Outcome(
                kind,
                code,
                tax,
                allocation,
                charged,
                forfeited,
                round.ap(),
                round.linesUsed(),
                roundOver);
    }
}