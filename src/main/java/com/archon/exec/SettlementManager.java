package com.archon.exec;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.World;

import java.util.Objects;
import java.util.Optional;

public final class SettlementManager {
    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final Interrupts interrupts;

    public SettlementManager(World world, EventBus bus, RoundState round, Interrupts interrupts) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.round = Objects.requireNonNull(round, "round");
        this.interrupts = Objects.requireNonNull(interrupts, "interrupts");
    }

    public Executor.Outcome settle(SubmissionQuote quote, ExecutionTrace trace) {
        Optional<ExecutionTrace.Stage> interruption = trace.interruption();
        boolean broke = interruption.isPresent();
        int charged = trace.charged();
        int forfeited = 0;

        if (broke) {
            ExecutionTrace.Stage stage = interruption.orElseThrow();
            forfeited = quote.free() ? 0 : round.rules().breakCharge(quote.allocation(), round.ap());
            round.spend(forfeited);
            bus.post(new GameEvent.LineBroke(stage.index(), stage.reason(), quote.allocation(),
                    quote.free() ? 0 : round.rules().breakPenalty(), forfeited, round.ap(), null));
        } else if (!quote.free()) {
            round.spend(charged);
            if (trace.paidExecuted() && world.thrall().alive()) {
                interrupts.fire();
            }
            bus.post(new GameEvent.LineComplete(charged, quote.tax(), quote.allocation() - charged, round.ap()));
        }

        boolean alive = world.thrall().alive();
        if (!alive) {
            bus.post(new GameEvent.ThrallDied());
        }
        boolean roundOver = alive && ((!quote.free() && round.ap() == 0) || (!broke && trace.terminalExecuted()));
        Executor.Kind kind = broke ? Executor.Kind.BROKE : quote.free() ? Executor.Kind.FREE : Executor.Kind.COMPLETE;
        return new Executor.Outcome(kind, trace.lastCode(), quote.tax(), quote.allocation(), charged, forfeited,
                round.ap(), round.linesUsed(), roundOver);
    }
}