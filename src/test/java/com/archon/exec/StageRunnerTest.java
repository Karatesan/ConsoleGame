package com.archon.exec;

import com.archon.app.Scenario;
import com.archon.command.Ast;
import com.archon.command.CommandParser;
import com.archon.event.EventBus;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.verb.ExitCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StageRunnerTest {

    @Test
    void testSequentialStagesExecution() {
        World world = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState round = new RoundState();
        PipelineRunner pipeRunner = new PipelineRunner(world, bus);
        StageRunner runner = new StageRunner(world, bus, pipeRunner);
        CommandParser parser = new CommandParser();

        Ast.Line line = parser.parse("strike o1 ; step w");
        StageRunner.ExecutionTrace trace = runner.execute(line, round);

        assertFalse(trace.broke());
        assertEquals(3, trace.charged());
        assertEquals(ExitCode.SUCCESS, trace.lastCode());
    }

    @Test
    void testAndOperatorSkipsOnNonSuccess() {
        World world = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState round = new RoundState();
        PipelineRunner pipeRunner = new PipelineRunner(world, bus);
        StageRunner runner = new StageRunner(world, bus, pipeRunner);
        CommandParser parser = new CommandParser();

        // strike d1 yields PARTIAL (not killed), so && step n should be skipped
        Ast.Line line = parser.parse("strike d1 -p heavy && step n");
        StageRunner.ExecutionTrace trace = runner.execute(line, round);

        assertFalse(trace.broke());
        assertEquals(3, trace.charged()); // step n was skipped, cost 3 only
        assertEquals(ExitCode.PARTIAL, trace.lastCode());
    }

    @Test
    void testBoundaryInterruptBreaksExecution() {
        World world = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState round = new RoundState();
        PipelineRunner pipeRunner = new PipelineRunner(world, bus);
        StageRunner runner = new StageRunner(world, bus, pipeRunner);
        CommandParser parser = new CommandParser();

        // Goblin archer triggers ON_MOVEMENT_IN_LOS
        Ast.Line line = parser.parse("step e ; step e ; strike g1");
        StageRunner.ExecutionTrace trace = runner.execute(line, round);

        assertTrue(trace.broke());
        assertTrue(trace.breakStage() > 0);
        assertNotNull(trace.breakReason());
    }
}
