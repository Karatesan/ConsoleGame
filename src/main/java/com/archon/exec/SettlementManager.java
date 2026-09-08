package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Entity;
import com.archon.model.World;
import com.archon.verb.Verbs;

/**
 * Handles AP deduction, break penalty forfeiture, late interrupt triggers,
 * and round-end condition determination.
 */
public final class SettlementManager {

    private final World world;
    private final EventBus bus;
    private final RoundState round;

    public SettlementManager(World world, EventBus bus, RoundState round) {
        this.world = world;
        this.bus = bus;
        this.round = round;
    }

    public Executor.Outcome settle(
            Ast.Line line,
            int allocation,
            int tax,
            StageRunner.ExecutionTrace trace
    ) {
        int forfeited = 0;
        if (trace.broke()) {
            forfeited = Math.min(allocation + RoundState.BREAK_PENALTY, round.ap());
            round.spend(forfeited);
            bus.post(new GameEvent.LineBroke(
                    trace.breakStage(),
                    trace.breakReason(),
                    allocation,
                    RoundState.BREAK_PENALTY,
                    forfeited,
                    round.ap(),
                    trace.breakHint()
            ));
        } else {
            round.spend(trace.charged());
            Entity late = world.pendingInterrupt();
            if (late != null) {
                int dmg = world.resolveInterrupt(late);
                bus.post(new GameEvent.InterruptFired(late.id, late.readied().description(), dmg));
            }
            bus.post(new GameEvent.LineComplete(trace.charged(), tax, allocation - trace.charged(), round.ap()));
        }

        if (!world.thrall.alive()) {
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

        boolean roundOver = round.ap() <= 0 || (!trace.broke() && endsWithTerminal(line));
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

    public static boolean endsWithTerminal(Ast.Line line) {
        if (line.stages().isEmpty()) return false;
        Ast.Stage last = line.stages().get(line.stages().size() - 1);
        com.archon.verb.Verb v = Verbs.get(last.last().verb());
        return v != null && v.terminal();
    }
}
