package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.World;

import java.util.Objects;

public final class SettlementManager {

    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final ExecutionPolicy policy;
    private final Interrupts interrupts;

    public SettlementManager(
            World world,
            EventBus bus,
            RoundState round,
            ExecutionPolicy policy,
            Interrupts interrupts
    ) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.round = Objects.requireNonNull(round, "round");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.interrupts = Objects.requireNonNull(interrupts, "interrupts");
    }

    public Executor.Outcome settle(
            Ast.Line line,
            int allocation,
            int tax,
            StageRunner.ExecutionTrace trace
    ) {
        int forfeited = 0;
        if (trace.broke()) {
            forfeited = round.rules().breakCharge(allocation, round.ap());
            round.spend(forfeited);
            bus.post(new GameEvent.LineBroke(
                    trace.breakStage(),
                    trace.breakReason(),
                    allocation,
                    round.rules().breakPenalty(),
                    forfeited,
                    round.ap(),
                    trace.breakHint()
            ));
        } else {
            round.spend(trace.charged());
            interrupts.fire();

            bus.post(new GameEvent.LineComplete(
                    trace.charged(),
                    tax,
                    allocation - trace.charged(),
                    round.ap()
            ));
        }

        if (!world.thrall().alive()) {
            bus.post(new GameEvent.ThrallDied());
            return new Executor.Outcome(
                    trace.broke() ? Executor.Kind.BROKE : Executor.Kind.COMPLETE,
                    trace.lastCode(),
                    tax,
                    allocation,
                    trace.charged(),
                    forfeited,
                    round.ap(),
                    round.linesUsed(),
                    false
            );
        }

        boolean roundOver = round.ap() <= 0 || (!trace.broke() && policy.endsWithTerminal(line));
        return new Executor.Outcome(
                trace.broke() ? Executor.Kind.BROKE : Executor.Kind.COMPLETE,
                trace.lastCode(),
                tax,
                allocation,
                trace.charged(),
                forfeited,
                round.ap(),
                round.linesUsed(),
                roundOver
        );
    }
}