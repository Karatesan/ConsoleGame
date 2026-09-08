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

class SettlementManagerTest {

    @Test
    void testNormalCompletionChargesAp() {
        World world = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState round = new RoundState();
        SettlementManager settlement = new SettlementManager(world, bus, round);
        CommandParser parser = new CommandParser();

        Ast.Line line = parser.parse("strike o1");
        int allocation = 2;
        int tax = 0;
        StageRunner.ExecutionTrace trace = new StageRunner.ExecutionTrace(
                ExitCode.PARTIAL,
                2,
                false,
                -1,
                null,
                null
        );

        Executor.Outcome outcome = settlement.settle(line, allocation, tax, trace);

        assertEquals(Executor.Kind.COMPLETE, outcome.kind());
        assertEquals(2, outcome.charged());
        assertEquals(0, outcome.forfeited());
        assertEquals(3, round.ap());
    }

    @Test
    void testBrokenLineForfeitsAllocationPlusPenalty() {
        World world = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState round = new RoundState();
        SettlementManager settlement = new SettlementManager(world, bus, round);
        CommandParser parser = new CommandParser();

        Ast.Line line = parser.parse("strike o1 -p heavy ; take o1/hand/right");
        int allocation = 4;
        int tax = 0;
        StageRunner.ExecutionTrace trace = new StageRunner.ExecutionTrace(
                ExitCode.BLOCKED,
                3,
                true,
                2,
                "runtime BLOCKED",
                "hint"
        );

        Executor.Outcome outcome = settlement.settle(line, allocation, tax, trace);

        assertEquals(Executor.Kind.BROKE, outcome.kind());
        // Forfeited = min(4 allocation + 1 penalty, 5 ap) = 5
        assertEquals(5, outcome.forfeited());
        assertEquals(0, round.ap());
    }

    @Test
    void testEndsWithTerminalPassVerb() {
        CommandParser parser = new CommandParser();
        Ast.Line passLine = parser.parse("pass");
        assertTrue(SettlementManager.endsWithTerminal(passLine));

        Ast.Line normalLine = parser.parse("strike o1");
        assertFalse(SettlementManager.endsWithTerminal(normalLine));
    }
}
