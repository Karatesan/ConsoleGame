package com.archon.exec;

import com.archon.app.ExecutionSetup;
import com.archon.app.Scenario;
import com.archon.command.CommandParser;
import com.archon.command.InvocationParser;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.*;
import com.archon.system.combat.Reaction;
import com.archon.verb.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class ExecutionContractTest {
    private static final Set<MaterialKind> NONE = Set.of();
    private static final Set<MaterialKind> ITEMS = Set.of(MaterialKind.ITEM);
    private static final Set<MaterialKind> SUBSTANCES = Set.of(MaterialKind.SUBSTANCE);

    private record Rig(World world, RoundState round, List<GameEvent> events, CommandParser parser,
                       SubmissionChecks submissions, StageRunner stages, Executor executor) {}

    private static World emptyWorld() {
        World world = new World(12, 8, new Dice.Always(true));
        world.installThrall(new Thrall(new Vec2(3, 4), 40));
        return world;
    }

    private static VerbDefinition definition(String name, int price, boolean free, boolean terminal,
                                             Set<MaterialKind> input, Set<MaterialKind> output, Verb action) {
        return new VerbDefinition(name, action, List.of(), new ApCost.Fixed(price), free, terminal, input, output,
                new VerbDefinition.Documentation("TEST", "", name, name, List.of()));
    }

    private static Rig rig(World world, EconomyRules rules, VerbCatalog catalog, Supplier<Actor> pending) {
        EventBus bus = new EventBus();
        List<GameEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        RoundState round = new RoundState(rules);
        ExecutionPolicy policy = new ExecutionPolicy(catalog);
        CommandParser parser = new CommandParser(new InvocationParser(catalog));
        SubmissionChecks submissions = new SubmissionChecks(new Validator(world, catalog), policy, round);
        Interrupts interrupts = new Interrupts(() -> Optional.ofNullable(pending.get()).map(
                actor -> new Reaction(actor.id(), actor.name(), actor.readied().description(), 0)), bus);
        StageRunner stages = new StageRunner(bus, new PipelineRunner(new InvocationRunner(world, bus, catalog)), policy,
                interrupts);
        Executor executor = new Executor(world, bus, round, parser, submissions,
                new Auditor(world, bus, round, submissions), stages,
                new SettlementManager(world, bus, round, interrupts), List::of);
        return new Rig(world, round, events, parser, submissions, stages, executor);
    }

    private static Rig rig(VerbDefinition... definitions) {
        return rig(emptyWorld(), EconomyRules.prototype(), new VerbCatalog(List.of(definitions)), () -> null);
    }

    private static VerbDefinition action(String name, ExitCode code, List<String> calls) {
        return definition(name, 1, false, false, NONE, NONE, context -> {
            calls.add(name);
            return code;
        });
    }

    @Test
    void ordinaryFailureAndRecoveryBothPayWithoutBreaking() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(action("attempt", ExitCode.BLOCKED, calls), action("recover", ExitCode.SUCCESS, calls));

        Executor.Outcome outcome = rig.executor().submit("attempt || recover");

        assertEquals(List.of("attempt", "recover"), calls);
        assertEquals(Executor.Kind.COMPLETE, outcome.kind());
        assertEquals(ExitCode.SUCCESS, outcome.lastCode());
        assertEquals(2, outcome.allocation());
        assertEquals(2, outcome.charged());
        assertEquals(0, outcome.forfeited());
        assertEquals(3, outcome.apAfter());
    }

    @Test
    void unrecoveredBlockageCompletesAndPaysForTheAttempt() {
        Rig rig = rig(action("attempt", ExitCode.BLOCKED, new ArrayList<>()));

        Executor.Outcome outcome = rig.executor().submit("attempt");

        assertEquals(Executor.Kind.COMPLETE, outcome.kind());
        assertEquals(ExitCode.BLOCKED, outcome.lastCode());
        assertEquals(1, outcome.charged());
        assertEquals(0, outcome.forfeited());
    }

    @Test
    void skippedAndPreservesFailureForFollowingOr() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(action("a", ExitCode.MISS, calls), action("b", ExitCode.SUCCESS, calls),
                action("c", ExitCode.SUCCESS, calls));

        Executor.Outcome outcome = rig.executor().submit("a && b || c");

        assertEquals(List.of("a", "c"), calls);
        assertEquals(3, outcome.allocation());
        assertEquals(2, outcome.charged());
        assertEquals(3, outcome.apAfter());
    }

    @Test
    void failureInExecutedAndStageReachesRecovery() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(action("a", ExitCode.SUCCESS, calls), action("b", ExitCode.PARTIAL, calls),
                action("c", ExitCode.SUCCESS, calls));

        rig.executor().submit("a && b || c");

        assertEquals(List.of("a", "b", "c"), calls);
    }

    @Test
    void sequenceContinuesAfterOrdinaryFailure() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(action("a", ExitCode.BLOCKED, calls), action("b", ExitCode.SUCCESS, calls));

        rig.executor().submit("a ; b");

        assertEquals(List.of("a", "b"), calls);
    }

    @Test
    void singleSubmissionsPayTaxButACombinedLineDoesNotAddAnInternalTax() {
        Rig singles = rig(action("a", ExitCode.SUCCESS, new ArrayList<>()));
        Rig combined = rig(action("a", ExitCode.SUCCESS, new ArrayList<>()));

        singles.executor().submit("a");
        singles.executor().submit("a");
        combined.executor().submit("a ; a");

        assertEquals(2, singles.round().ap());
        assertEquals(3, combined.round().ap());
        assertEquals(2, singles.round().linesUsed());
        assertEquals(1, combined.round().linesUsed());
    }

    @Test
    void freeLinesUseOperatorsAndReportTheirActualOutcome() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(definition("a", 0, true, false, NONE, NONE, c -> {
            calls.add("a");
            return ExitCode.BLOCKED;
        }), definition("b", 0, true, false, NONE, NONE, c -> {
            calls.add("b");
            return ExitCode.SUCCESS;
        }), definition("c", 0, true, false, NONE, NONE, c -> {
            calls.add("c");
            return ExitCode.MISS;
        }));

        Executor.Outcome outcome = rig.executor().submit("a && b || c");

        assertEquals(List.of("a", "c"), calls);
        assertEquals(Executor.Kind.FREE, outcome.kind());
        assertEquals(ExitCode.MISS, outcome.lastCode());
        assertEquals(5, outcome.apAfter());
        assertEquals(0, outcome.linesUsed());
    }

    @Test
    void mixedSubmissionCountsAsPaidEvenWhenPaidWorkIsSkipped() {
        Rig rig = rig(definition("info", 0, true, false, NONE, NONE, c -> ExitCode.SUCCESS),
                action("paid", ExitCode.SUCCESS, new ArrayList<>()));
        rig.round().countLine();

        Executor.Outcome outcome = rig.executor().submit("info || paid");

        assertEquals(Executor.Kind.COMPLETE, outcome.kind());
        assertEquals(1, outcome.tax());
        assertEquals(0, outcome.charged());
        assertEquals(4, outcome.apAfter());
        assertEquals(2, outcome.linesUsed());
    }

    @Test
    void producerFailurePreservesItsOutcomeAndSkipsConsumer() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(definition("produce", 1, false, false, NONE, ITEMS, c -> ExitCode.MISS),
                definition("consume", 2, false, false, ITEMS, NONE, c -> {
                    calls.add("consume");
                    return ExitCode.SUCCESS;
                }), action("recover", ExitCode.SUCCESS, calls));

        Executor.Outcome outcome = rig.executor().submit("produce | consume || recover");

        assertEquals(List.of("recover"), calls);
        assertEquals(3, outcome.charged());
        assertTrue(rig.events().stream().anyMatch(
                event -> event instanceof GameEvent.StageResult result && result.code() == ExitCode.MISS));
    }

    @Test
    void interruptedProducerNeverReachesConsumerOrRecovery() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(definition("produce", 1, false, false, NONE, ITEMS, c -> ExitCode.INTERRUPT),
                definition("consume", 2, false, false, ITEMS, NONE, c -> {
                    calls.add("consume");
                    return ExitCode.SUCCESS;
                }), action("recover", ExitCode.SUCCESS, calls));

        Executor.Outcome outcome = rig.executor().submit("produce | consume || recover");

        assertTrue(calls.isEmpty());
        assertEquals(Executor.Kind.BROKE, outcome.kind());
        assertEquals(ExitCode.INTERRUPT, outcome.lastCode());
        assertEquals(1, outcome.charged());
        assertEquals(4, outcome.forfeited());
        assertEquals(1, outcome.apAfter());
    }

    @Test
    void absentOutputBlocksConsumerWithoutSubstitutingItsOperand() {
        List<String> calls = new ArrayList<>();
        Rig rig = rig(definition("produce", 1, false, false, NONE, ITEMS, c -> ExitCode.SUCCESS),
                definition("consume", 2, false, false, ITEMS, NONE, c -> {
                    calls.add("consume");
                    return ExitCode.SUCCESS;
                }), action("recover", ExitCode.SUCCESS, calls));

        Executor.Outcome outcome = rig.executor().submit("produce | consume substitute || recover");

        assertEquals(List.of("recover"), calls);
        assertEquals(3, outcome.charged());
        assertEquals(Executor.Kind.COMPLETE, outcome.kind());
    }

    @Test
    void consumedMaterialIsNotAutomaticallyForwarded() {
        List<String> calls = new ArrayList<>();
        Material material = new Material.OfItem(Item.flask("oil", "oil", Tag.OIL));
        Rig rig = rig(definition("produce", 1, false, false, NONE, ITEMS, c -> {
            c.materialOut = material;
            return ExitCode.SUCCESS;
        }), definition("consume", 1, false, false, ITEMS, ITEMS, c -> {
            assertSame(material, c.materialIn);
            calls.add("consume");
            return ExitCode.SUCCESS;
        }), definition("sink", 1, false, false, ITEMS, NONE, c -> {
            calls.add("sink");
            return ExitCode.SUCCESS;
        }));

        Executor.Outcome outcome = rig.executor().submit("produce | consume | sink");

        assertEquals(List.of("consume"), calls);
        assertEquals(ExitCode.BLOCKED, outcome.lastCode());
        assertEquals(1, outcome.charged());
    }

    @Test
    void validationUsesKindsAndNoFakeMaterial() {
        List<String> checks = new ArrayList<>();
        Verb consumer = new Verb() {
            @Override
            public Check validateStructural(ValidationContext context) {
                assertTrue(context.pipedInput);
                assertNull(context.materialIn);
                checks.add("validated");
                return Check.ok();
            }

            @Override
            public ExitCode execute(VerbContext context) {
                return ExitCode.SUCCESS;
            }
        };
        Rig rig = rig(definition("produce", 1, false, false, NONE, ITEMS, c -> ExitCode.SUCCESS),
                definition("consume", 1, false, false, ITEMS, NONE, consumer));

        SubmissionChecks.Review review = rig.submissions().review(rig.parser().parse("produce | consume"));

        assertTrue(review.check().valid());
        assertEquals(List.of("validated"), checks);
    }

    @Test
    void allPossibleOutputKindsMustBeAccepted() {
        Rig rig = rig(definition("produce", 1, false, false, NONE, Set.of(MaterialKind.ITEM, MaterialKind.SUBSTANCE),
                c -> ExitCode.SUCCESS), definition("consume", 1, false, false, ITEMS, NONE, c -> ExitCode.SUCCESS));

        Executor.Outcome outcome = rig.executor().submit("produce | consume");

        assertEquals(Executor.Kind.REJECTED, outcome.kind());
        assertEquals(ExitCode.INVALID, outcome.lastCode());
        assertEquals(5, outcome.apAfter());
        assertEquals(0, outcome.linesUsed());
    }

    @Test
    void undeclaredOutputAndRuntimeInvalidAreProgrammingErrors() {
        Rig wrongOutput = rig(definition("bad", 1, false, false, NONE, ITEMS, c -> {
            c.materialOut = new Material.OfSubstance(Tag.OIL);
            return ExitCode.SUCCESS;
        }));
        Rig invalid = rig(action("bad", ExitCode.INVALID, new ArrayList<>()));

        assertThrows(IllegalStateException.class, () -> wrongOutput.executor().submit("bad"));
        assertThrows(IllegalStateException.class, () -> invalid.executor().submit("bad"));
    }

    @Test
    void programmingExceptionsRemainVisible() {
        RuntimeException defect = new IllegalArgumentException("defect");
        Rig execution = rig(definition("bad", 1, false, false, NONE, NONE, c -> {
            throw defect;
        }));
        Verb invalidValidator = new Verb() {
            @Override
            public Check validateStructural(ValidationContext context) {
                throw defect;
            }

            @Override
            public ExitCode execute(VerbContext context) {
                return ExitCode.SUCCESS;
            }
        };
        Rig validation = rig(definition("bad", 1, false, false, NONE, NONE, invalidValidator));

        assertSame(defect, assertThrows(RuntimeException.class, () -> execution.executor().submit("bad")));
        assertSame(defect, assertThrows(RuntimeException.class, () -> validation.executor().submit("bad")));
    }

    @Test
    void terminalRequiresSuccessfulExecutionAndLegalPlacement() {
        Rig rig = rig(action("ok", ExitCode.SUCCESS, new ArrayList<>()),
                action("fail", ExitCode.BLOCKED, new ArrayList<>()),
                definition("end", 0, true, true, NONE, NONE, c -> ExitCode.SUCCESS));

        assertFalse(rig.executor().submit("ok || end").roundOver());
        assertTrue(rig.executor().submit("fail || end").roundOver());

        Rig free = rig(definition("end", 0, true, true, NONE, NONE, c -> ExitCode.SUCCESS),
                definition("info", 0, true, false, NONE, NONE, c -> ExitCode.SUCCESS));
        assertEquals(Executor.Kind.REJECTED, free.executor().submit("end ; info").kind());
    }

    @Test
    void freeStagesDoNotAddReactionCheckpoints() {
        Actor source = new Actor("r1", "Watcher", 'R', new Vec2(4, 4), new CreatureStats(10, 0, 0));
        source.ready(Actor.Trigger.ON_ADJACENCY, "reacts", 0);
        AtomicInteger checks = new AtomicInteger();
        VerbCatalog catalog = new VerbCatalog(
                List.of(definition("paid", 1, false, false, NONE, NONE, c -> ExitCode.SUCCESS),
                        definition("info", 0, true, false, NONE, NONE, c -> ExitCode.SUCCESS)));
        Rig rig = rig(emptyWorld(), EconomyRules.prototype(), catalog, () -> {
            checks.incrementAndGet();
            return source;
        });

        Executor.Outcome outcome = rig.executor().submit("paid ; info");

        assertEquals(Executor.Kind.COMPLETE, outcome.kind());
        assertEquals(1, checks.get());
        assertEquals(1, outcome.charged());
        assertEquals(0, outcome.forfeited());
    }

    @Test
    void boundaryInterruptionRecordsInterruptedAndUnreachedStages() {
        Actor source = new Actor("r1", "Watcher", 'R', new Vec2(4, 4), new CreatureStats(10, 0, 0));
        source.ready(Actor.Trigger.ON_ADJACENCY, "reacts", 0);
        Rig rig = rig(emptyWorld(), EconomyRules.prototype(),
                new VerbCatalog(List.of(definition("paid", 2, false, false, NONE, NONE, c -> ExitCode.SUCCESS))),
                () -> source);

        ExecutionTrace trace = rig.stages().execute(rig.parser().parse("paid ; paid ; paid"), rig.round());

        assertEquals(ExitCode.INTERRUPT, trace.lastCode());
        assertEquals(3, trace.charged());
        assertEquals(ExecutionTrace.State.EXECUTED, trace.stages().get(0).state());
        assertEquals(ExecutionTrace.State.INTERRUPTED_BEFORE, trace.stages().get(1).state());
        assertEquals(ExecutionTrace.State.NOT_REACHED, trace.stages().get(2).state());
    }

    @Test
    void customRulesAndAuditAgreeWithoutSpendingDuringAudit() {
        Rig rig = rig(emptyWorld(), new EconomyRules(9, 0, 2, 3),
                new VerbCatalog(List.of(definition("stop", 2, false, false, NONE, NONE, c -> ExitCode.INTERRUPT))),
                () -> null);

        Executor.Outcome audit = rig.executor().submit("stop -n");
        assertEquals(Executor.Kind.AUDIT, audit.kind());
        assertEquals(ExitCode.SUCCESS, audit.lastCode());
        assertEquals(9, rig.round().ap());
        assertEquals(0, rig.round().linesUsed());

        Executor.Outcome executed = rig.executor().submit("stop");
        assertEquals(2, executed.tax());
        assertEquals(1, executed.charged());
        assertEquals(5, executed.forfeited());
        assertEquals(2, executed.apAfter());
        assertTrue(rig.events().stream().anyMatch(
                event -> event instanceof GameEvent.Audit report && report.text().contains("= 5 AP")));
    }

    @Test
    void affordabilityIncludesSkippedAllocationsAndOverflowRejectsBeforeCommitment() {
        Rig expensive = rig(definition("a", 3, false, false, NONE, NONE, c -> ExitCode.SUCCESS));
        assertEquals(Executor.Kind.REJECTED, expensive.executor().submit("a || a").kind());

        Rig overflow = rig(definition("a", Integer.MAX_VALUE, false, false, NONE, NONE, c -> ExitCode.SUCCESS));
        Executor.Outcome outcome = overflow.executor().submit("a ; a");

        assertEquals(Executor.Kind.REJECTED, outcome.kind());
        assertEquals(ExitCode.INVALID, outcome.lastCode());
        assertEquals(0, outcome.linesUsed());
        assertEquals(5, outcome.apAfter());
    }

    @Test
    void realBlockedFirstStageRecoversAndInvalidSkippedSyntaxRejects() {
        World world = Scenario.testRoom(new Dice.Always(true));
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor executor = ExecutionSetup.create(world, new EventBus(), round);

        Executor.Outcome recovered = executor.submit("step n || guard");
        assertEquals(Executor.Kind.COMPLETE, recovered.kind());
        assertEquals(2, recovered.charged());

        Executor.Outcome rejected = executor.submit("guard || inspect missing/body/nonsense");
        assertEquals(Executor.Kind.REJECTED, rejected.kind());
        assertEquals(ExitCode.INVALID, rejected.lastCode());
        assertEquals(3, round.ap());
    }

    @Test
    void realSiphonIsFiniteAndItemPipelineKeepsOwnershipCorrect() {
        World world = Scenario.testRoom(new Dice.Always(true));
        RoundState round = new RoundState(EconomyRules.prototype());
        Executor executor = ExecutionSetup.create(world, new EventBus(), round);

        Executor.Outcome poured = executor.submit("siphon b1/contents | pour @self");
        assertEquals(ExitCode.SUCCESS, poured.lastCode());
        assertEquals(1, poured.charged());
        assertTrue(world.tile(world.thrall().pos()).has(Tag.OIL));

        Executor.Outcome empty = executor.submit("siphon b1/contents");
        assertEquals(ExitCode.BLOCKED, empty.lastCode());

        Executor.Outcome dropped = executor.submit("take /pack/flask_water | drop");
        assertEquals(ExitCode.SUCCESS, dropped.lastCode());
        assertTrue(world.thrall().inventory().find("flask_water").isEmpty());
        assertTrue(
                world.tile(world.thrall().pos()).ground().stream().anyMatch(item -> item.id().equals("flask_water")));
    }

    @Test
    void realMovementReactionBreaksRemainingWorkButNotCompletedWork() {
        World singleWorld = Scenario.testRoom(new Dice.Always(true));
        Executor single = ExecutionSetup.create(singleWorld, new EventBus(), new RoundState(EconomyRules.prototype()));
        Executor.Outcome completed = single.submit("step e");
        assertEquals(Executor.Kind.COMPLETE, completed.kind());
        assertEquals(34, singleWorld.thrall().hp());

        World chainWorld = Scenario.testRoom(new Dice.Always(true));
        Executor chain = ExecutionSetup.create(chainWorld, new EventBus(), new RoundState(EconomyRules.prototype()));
        Executor.Outcome interrupted = chain.submit("step e ; guard");
        assertEquals(Executor.Kind.BROKE, interrupted.kind());
        assertEquals(ExitCode.INTERRUPT, interrupted.lastCode());
        assertEquals(3, interrupted.forfeited());
        assertEquals(34, chainWorld.thrall().hp());
    }
}