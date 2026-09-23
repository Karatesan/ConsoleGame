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

    public record Outcome(Kind kind, ExitCode lastCode, int tax, int allocation, int charged, int forfeited,
                          int apAfter, int linesUsed, boolean roundOver) {}

    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final CommandParser parser;
    private final SubmissionChecks submissions;
    private final Auditor auditor;
    private final StageRunner stageRunner;
    private final SettlementManager settlementManager;
    private final Supplier<List<String>> tick;

    public Executor(World world, EventBus bus, RoundState round, CommandParser parser, SubmissionChecks submissions,
                    Auditor auditor, StageRunner stageRunner, SettlementManager settlementManager,
                    Supplier<List<String>> tick) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.round = Objects.requireNonNull(round, "round");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.auditor = Objects.requireNonNull(auditor, "auditor");
        this.stageRunner = Objects.requireNonNull(stageRunner, "stageRunner");
        this.settlementManager = Objects.requireNonNull(settlementManager, "settlementManager");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    public RoundState round() {
        return round;
    }

    public Outcome submit(String raw) {
        Ast.Line line;
        try {
            line = parser.parse(raw);
        } catch (CommandParser.ParseError error) {
            bus.post(new GameEvent.Rejected(ExitCode.INVALID, error.getMessage(), error.hint));
            return out(Kind.REJECTED, ExitCode.INVALID, 0, 0);
        }
        if (line.stages().isEmpty()) {
            return out(Kind.NOOP, ExitCode.SUCCESS, 0, 0);
        }
        if (line.dryRun()) {
            SubmissionChecks.Review review = auditor.audit(line);
            return out(Kind.AUDIT, review.check().code(), 0, review.quote() == null ? 0 : review.quote().allocation());
        }

        SubmissionChecks.Review review = submissions.review(line);
        SubmissionQuote quote = review.quote();
        if (!review.check().valid()) {
            Check check = review.check();
            bus.post(new GameEvent.Rejected(check.code(), check.reason(), check.hint()));
            return out(Kind.REJECTED, check.code(), quote == null ? 0 : quote.tax(),
                    quote == null ? 0 : quote.allocation());
        }

        if (!quote.free()) {
            round.spend(quote.tax());
            round.countLine();
            world.resetThrallMovement();
            bus.post(new GameEvent.LineStart(line.raw(), round.linesUsed(), quote.allocation(), quote.tax(),
                    line.isChain()));
        }
        return settlementManager.settle(quote, stageRunner.execute(line, round));
    }

    public void endRound() {
        List<String> worldLog = tick.get();
        bus.post(new GameEvent.RoundEnd(round.reset(), worldLog));
        bus.post(world.thrall().alive() ? new GameEvent.RoundStart(round.roundNo(),
                round.ap()) : new GameEvent.ThrallDied());
    }

    private Outcome out(Kind kind, ExitCode code, int tax, int allocation) {
        return new Outcome(kind, code, tax, allocation, 0, 0, round.ap(), round.linesUsed(), false);
    }
}