package com.archon.exec;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.World;
import com.archon.verb.Check;

import java.util.Objects;

public final class Auditor {

    private final World world;
    private final EventBus bus;
    private final RoundState round;
    private final Validator validator;
    private final ExecutionPolicy policy;

    public Auditor(World world, EventBus bus, RoundState round, Validator validator, ExecutionPolicy policy) {
        this.world = Objects.requireNonNull(world, "world");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.round = Objects.requireNonNull(round, "round");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public void audit(Ast.Line line) {
        int allocation = policy.allocationOf(line);
        int tax = round.rules().taxForNextLine(round.linesUsed());
        StringBuilder sb = new StringBuilder("CHAIN AUDIT — nothing spent.\n");

        sb.append(String.format("  Verbs:      %d  →  %s%n", line.verbCount(), line.isChain() ? "CHAIN" : "SINGLE"));
        sb.append("  Allocation: ").append(allocation).append(" AP   (")
                .append(String.join(" + ", line.stages().stream().map(s -> String.valueOf(policy.stageCost(s))).toList()))
                .append(")\n");
        sb.append(String.format("  Line tax:   %d AP   (line %d this round)%n", tax, round.linesUsed() + 1));
        sb.append(String.format("  Available:  %d AP   → %s%n", round.ap(),
                allocation + tax <= round.ap() ? "AFFORDABLE" : "UNAFFORDABLE"));

        Check v = validator.validate(line);
        sb.append("\n  VALIDITY: ").append(v.valid() ? "OK as of now" : "REJECT — " + v.reason()).append('\n');
        for (int i = 0; i < line.stages().size(); i++) {
            sb.append(String.format("    [%d] %s%n", i + 1, line.stages().get(i).render()));
        }

        sb.append("\n  BREAK EXPOSURE:\n");
        boolean any = false;
        for (Entity entity : world.entities()) {
            if (entity instanceof Actor actor
                    && actor.readied() != null
                    && !actor.isReadiedSpent()) {
                any = true;
                sb.append(String.format("    • %s is READIED (%s, %d dmg) → interrupt at a stage boundary%n",
                        actor.id(), actor.readied().description(), actor.readied().damage()));
            }
        }

        if (line.stages().size() > 1) {
            sb.append("    • ").append(line.stages().size() - 1)
                    .append(" stage boundary/boundaries exposed to interrupts\n");
        }
        if (!any) {
            sb.append("    • no readied reactions visible\n");
        }

        sb.append(String.format("%n  IF BROKEN: forfeit %d AP allocation + %d AP penalty = %d AP.%n",
                allocation, round.rules().breakPenalty(),
                round.rules().breakCharge(allocation, round.ap())));

        bus.audit(sb.toString().stripTrailing());
    }
}