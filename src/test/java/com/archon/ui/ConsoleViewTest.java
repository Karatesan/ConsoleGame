package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.verb.ExitCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleViewTest {

    @BeforeEach
    void setUp() {
        Ansi.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        Ansi.setEnabled(true);
    }

    @Test
    void testLogBufferingCapsAtLogLines() {
        ConsoleView view = new ConsoleView();
        for (int i = 0; i < 20; i++) {
            view.handle(new GameEvent.Narrative("message " + i));
        }

        List<String> entries = view.logEntries();
        assertEquals(ConsoleView.LOG_LINES, entries.size());
        assertEquals("message 10", entries.get(0));
        assertEquals("message 19", entries.get(entries.size() - 1));
    }

    @Test
    void testEventHandlingFormatsCleanly() {
        ConsoleView view = new ConsoleView();

        view.handle(new GameEvent.LineStart("strike o1 -p heavy", 1, 3, 0, false));
        view.handle(new GameEvent.StageResult(1, 1, "strike o1 -p heavy", ExitCode.SUCCESS, 3, 2));
        view.handle(new GameEvent.LineComplete(3, 0, 0, 2));
        view.handle(new GameEvent.Audit("syntax checked"));
        view.handle(new GameEvent.Rejected(ExitCode.BLOCKED, "wall in the way", "try another direction"));
        view.handle(new GameEvent.LineBroke("interrupted by goblin", 3, 1, 4, "take cover"));
        view.handle(new GameEvent.InterruptFired("g1", "readied shot", 6));
        view.handle(new GameEvent.RoundEnd(1, List.of("fire spread")));
        view.handle(new GameEvent.RoundStart(2, 5));
        view.handle(new GameEvent.ThrallDied());

        List<String> entries = view.logEntries();
        String allText = String.join("\n", entries.stream().map(Ansi::strip).toList());

        assertTrue(allText.contains("> strike o1 -p heavy"));
        assertTrue(allText.contains("strike o1 -p heavy"));
        assertTrue(allText.contains("LINE COMPLETE"));
        assertTrue(allText.contains("[AUDIT] syntax checked"));
        assertTrue(allText.contains("✗ BLOCKED [3] — wall in the way"));
        assertTrue(allText.contains("✗ BREAK — interrupted by goblin"));
        assertTrue(allText.contains("⚠ INTERRUPT — g1"));
        assertTrue(allText.contains("── ROUND SETTLED ── 1 AP destroyed unspent."));
        assertTrue(allText.contains("fire spread"));
        assertTrue(allText.contains("── ROUND 2 ── AP 5/5"));
        assertTrue(allText.contains("*** THE THRALL COLLAPSES. THE LINK GOES DARK. ***"));
    }

    @Test
    void testRenderFrameToStringProducesBalancedGrid() {
        World world = Scenario.testRoom(new Dice.Always(true));
        RoundState round = new RoundState();
        ConsoleView view = new ConsoleView();

        view.handle(new GameEvent.Narrative("System ready."));

        String frame = view.renderFrameToString(world, round);
        assertNotNull(frame);

        String stripped = Ansi.strip(frame);

        // Header titles
        assertTrue(stripped.contains("MAP VIEWPORT"));
        assertTrue(stripped.contains("THRALL TELEMETRY"));

        // Left map indicators
        assertTrue(stripped.contains("@"));
        assertTrue(stripped.contains("Pos: (3, 4)"));

        // Right telemetry indicators
        assertTrue(stripped.contains("HP: ["));
        assertTrue(stripped.contains("AP: ["));
        assertTrue(stripped.contains("── TARGET RADAR ──"));
        assertTrue(stripped.contains("[o1] Orc Guard"));

        // Hint bar
        assertTrue(stripped.contains("[help] manual"));
        assertTrue(stripped.contains("[pass] end turn"));

        // Every row between borders has matching visual width
        String[] lines = frame.split("\n");
        int expectedWidth = Ansi.length(lines[0]); // top border width
        assertTrue(expectedWidth > 80, "Dashboard should comfortably fit standard 90+ char terminal");

        for (String line : lines) {
            assertEquals(expectedWidth, Ansi.length(line),
                    "Each rendered frame line must match the dashboard width");
        }
    }

    @Test
    void testFrameOutputsClearScreenAndContent() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);

        ConsoleView view = new ConsoleView(ps);
        World world = Scenario.testRoom(new Dice.Always(true));
        RoundState round = new RoundState();

        view.frame(world, round);

        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.startsWith(Ansi.CLEAR_SCREEN), "Frame output must begin with clear screen escape sequence");
        assertTrue(output.contains("MAP VIEWPORT"));
        assertTrue(output.contains("THRALL TELEMETRY"));
    }
}
