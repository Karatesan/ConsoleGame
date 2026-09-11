package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryPaneRendererTest {

    @Test
    void testRenderTelemetryAndRadar() {
        World world = Scenario.testRoom(new Dice.Always(true));
        RoundState round = new RoundState();
        TelemetryPaneRenderer renderer = new TelemetryPaneRenderer();

        int targetWidth = 50;
        List<String> lines = renderer.render(world, round, targetWidth);

        assertFalse(lines.isEmpty());
        String fullOutput = String.join("\n", lines.stream().map(Ansi::strip).toList());

        // HP and AP checks
        assertTrue(fullOutput.contains("HP: ["));
        assertTrue(fullOutput.contains("40/40"));
        assertTrue(fullOutput.contains("AP: ["));
        assertTrue(fullOutput.contains("5/5"));
        assertTrue(fullOutput.contains("Line 0"));

        // Gear checks
        assertTrue(fullOutput.contains("R-HAND: rusted cleaver (6-10 dmg)"));
        assertTrue(fullOutput.contains("L-HAND: torch"));

        // Radar section
        assertTrue(fullOutput.contains("── TARGET RADAR ──"));
        assertTrue(fullOutput.contains("[o1] Orc Guard"));
        assertTrue(fullOutput.contains("[g1] Goblin Archer"));
        assertTrue(fullOutput.contains("READIED: fires on movement in line of sight (6 dmg)"));
        assertTrue(fullOutput.contains("[b1] Oil Barrel"));
        assertTrue(fullOutput.contains("[br1] Brazier"));
        assertTrue(fullOutput.contains("[d1] Oak Door"));

        // All lines should match or exceed targetWidth in visual length
        for (String line : lines) {
            assertEquals(targetWidth, Ansi.length(line), "Line visual length must match targetWidth");
        }
    }

    @Test
    void testGuardedStatusDisplayed() {
        World world = Scenario.testRoom(new Dice.Always(true));
        world.thrall.guarded = true;
        RoundState round = new RoundState();
        TelemetryPaneRenderer renderer = new TelemetryPaneRenderer();

        List<String> lines = renderer.render(world, round, 50);
        String fullOutput = String.join("\n", lines.stream().map(Ansi::strip).toList());
        assertTrue(fullOutput.contains("[GUARDED]"));
    }

    @Test
    void testNullWorldHandledGracefully() {
        TelemetryPaneRenderer renderer = new TelemetryPaneRenderer();
        List<String> lines = renderer.render(null, null, 40);
        assertEquals(1, lines.size());
        assertTrue(Ansi.strip(lines.get(0)).contains("No telemetry link"));
    }
}
