package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.World;

import java.util.Objects;

public final class Auditor {
    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final SubmissionChecks submissions;

    public Auditor(World world, EventBus bus, RoundState round, SubmissionChecks submissions) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.round = Objects.requireNonNull(round, "round");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
    }

    public SubmissionChecks.Review audit(Ast.Line line) {
        SubmissionChecks.Review review = submissions.review(line);
        SubmissionQuote quote = review.quote();
        StringBuilder out = new StringBuilder("CHAIN AUDIT — nothing spent.\n");
        out.append(String.format("  Verbs: %d — %s%n", line.verbCount(), line.isChain() ? "CHAIN" : "SINGLE"));
        out.append("  SUBMISSION: ").append(
                review.check().valid() ? "ACCEPTED; gameplay outcomes are not predicted" : "REJECT — " + review.check().reason()).append(
                '\n');

        if (quote != null) {
            out.append(String.format("  Allocation: %d AP; tax: %d AP%s%n", quote.allocation(), quote.tax(),
                    quote.free() ? " (free-only)" : ""));
            out.append(
                    String.format("  Required: %d AP; available: %d AP — %s%n", quote.requiredAp(), quote.availableAp(),
                            quote.affordable() ? "AFFORDABLE" : "UNAFFORDABLE"));
        }
        for (int i = 0; i < line.stages().size(); i++) {
            out.append(String.format("    [%d] %s%n", i + 1, line.stages().get(i).render()));
        }

        if (!review.check().valid() || quote.free()) {
            out.append("\n  No paid commitment; no tax or forfeiture.\n");
        } else {
            out.append("""
                    
                      Ordinary attempts and executed recovery stages pay their stage prices.
                      Skipped stages are uncharged. Upstream effects are not rolled back.
                      Reactions are checked between executed paid stages, not at free/skipped
                      stages, and once after normally completed paid work.
                    """);
            for (Entity entity : world.entities()) {
                if (entity instanceof Actor actor && actor.alive() && actor.readied() != null && !actor.isReadiedSpent()) {
                    out.append(String.format("    • %s is READIED (%s, %d dmg)%n", actor.id(),
                            actor.readied().description(), actor.readied().damage()));
                }
            }
            out.append("  Listed readiness is potential exposure, not a trigger prediction.\n");
            out.append(String.format("  IF INTERRUPTED: min(%d allocation + %d penalty, %d AP after tax) = %d AP.%n",
                    quote.allocation(), round.rules().breakPenalty(), quote.availableAp() - quote.tax(),
                    quote.forfeiture()));
            out.append("  This replaces trace charging; commitment tax is additional.\n");
        }
        bus.audit(out.toString().stripTrailing());
        return review;
    }
}