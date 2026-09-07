package com.archon.exec;

import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Entity;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.List;

/**
 * Implements Action Economy Spec v3.0 §8 verbatim.
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

    public Executor(World world, EventBus bus, RoundState round) {
        this.world = world;
        this.bus = bus;
        this.round = round;
        this.validator = new Validator(world, bus);
        this.auditor = new Auditor(world, bus, round);
    }

    public RoundState round() {return round;}

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
            for (Ast.Stage st : line.stages())
                for (Ast.Invocation inv : st.pipeline())
                    Verbs.get(inv.verb()).execute(new VerbContext(world, inv, bus, null, true));
            return out(Kind.FREE, ExitCode.SUCCESS, 0, 0, 0, 0, endsWithTerminal(line));
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
        List<Ast.Stage> stages = line.stages();
        ExitCode last = ExitCode.SUCCESS;
        int charged = 0, breakStage = -1;
        boolean broke = false;
        String breakReason = null, breakHint = null;

        for (int i = 0; i < stages.size(); i++) {
            Ast.Stage stage = stages.get(i);
            int cost = stageCost(stage);

            // 7a. operator branching — never a break
            if (i > 0) {
                boolean run = switch (stage.op()) {
                    case AND -> last == ExitCode.SUCCESS;
                    case OR -> last != ExitCode.SUCCESS;
                    default -> true;
                };
                if (!run) {
                    bus.post(new GameEvent.StageSkipped(i + 1, stage.render(),
                            stage.op() == Ast.Op.AND ? "&& requires SUCCESS" : "|| requires failure"));
                    continue;
                }

                // 7b. interrupt check at the stage boundary
                Entity source = world.pendingInterrupt();
                if (source != null) {
                    int dmg = world.resolveInterrupt(source);
                    bus.post(new GameEvent.InterruptFired(source.id, source.readied.description(), dmg));
                    charged += cost / 2;
                    breakStage = i + 1;
                    breakReason = source.name + " (READIED) interrupted the sequence";
                    breakHint = "it was flagged READIED in your last scan";
                    broke = true;
                    break;
                }
            }

            ExitCode code = runStage(stage, i == 0);
            last = code;

            if (code == ExitCode.BLOCKED || code == ExitCode.INVALID) {
                breakStage = i + 1;
                breakReason = "runtime BLOCKED at stage " + (i + 1) + ": " + stage.render();
                breakHint = "use && to make this stage conditional on the previous one";
                broke = true;
                break;
            }
            if (code == ExitCode.INTERRUPT) {
                charged += cost / 2;
                breakStage = i + 1;
                breakReason = "interrupted during stage " + (i + 1);
                broke = true;
                break;
            }

            charged += cost;
            bus.post(new GameEvent.StageResult(i + 1, stages.size(), stage.render(), code, cost, round.ap() - charged));
        }

        // ---- 8. settle ----
        // ---- 8. settle ----
        int forfeited = 0;
        if (broke) {
            forfeited = Math.min(allocation + RoundState.BREAK_PENALTY, round.ap());
            round.spend(forfeited);
            bus.post(new GameEvent.LineBroke(breakStage, breakReason, allocation,
                    RoundState.BREAK_PENALTY, forfeited, round.ap(), breakHint));
        } else {
            round.spend(charged);
            Entity late = world.pendingInterrupt();
            if (late != null) {
                int dmg = world.resolveInterrupt(late);
                bus.post(new GameEvent.InterruptFired(late.id, late.readied.description(), dmg));
            }
            bus.post(new GameEvent.LineComplete(charged, tax, allocation - charged, round.ap()));
        }

        if (!world.thrall.alive()) {
            bus.post(new GameEvent.ThrallDied());
            return out(broke ? Kind.BROKE : Kind.COMPLETE, last, tax, allocation, charged, forfeited, false);
        }

        boolean roundOver = round.ap() <= 0 || (!broke && endsWithTerminal(line));
        return out(broke ? Kind.BROKE : Kind.COMPLETE, last, tax, allocation, charged, forfeited, roundOver);
    }

    /**
     * A pipeline is one atomic stage: upstream verbs are free and must succeed.
     */
    private ExitCode runStage(Ast.Stage stage, boolean firstStage) {
        Material material = null;
        ExitCode code = ExitCode.SUCCESS;
        List<Ast.Invocation> pipe = stage.pipeline();

        for (int p = 0; p < pipe.size(); p++) {
            Ast.Invocation inv = pipe.get(p);
            Verb verb = Verbs.get(inv.verb());
            VerbContext ctx = new VerbContext(world, inv, bus, material, firstStage);
            try {
                code = verb.execute(ctx);
            } catch (RuntimeException ex) {
                bus.narrate("the working falters: " + ex.getMessage());
                return ExitCode.BLOCKED;
            }
            boolean lastInPipe = p == pipe.size() - 1;
            if (!lastInPipe && code != ExitCode.SUCCESS) return ExitCode.BLOCKED;
            material = ctx.materialOut;
        }
        return code;
    }

    public void endRound() {
        int wasted = round.ap();
        List<String> worldLog = world.tick();
        round.reset();
        bus.post(new GameEvent.RoundEnd(wasted, worldLog));
        bus.post(new GameEvent.RoundStart(round.roundNo(), round.ap()));
    }

    // ---- cost model ----

    /**
     * Pipelines cost only their final verb: upstream stages are free.
     */
    public static int stageCost(Ast.Stage stage) {
        Verb v = Verbs.get(stage.last().verb());
        return v == null ? 0 : v.apCost(stage.last());   // unknown verbs cost nothing; validator rejects them
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

    private static boolean endsWithTerminal(Ast.Line line) {
        Ast.Stage last = line.stages().get(line.stages().size() - 1);
        Verb v = Verbs.get(last.last().verb());
        return v != null && v.terminal();
    }
}