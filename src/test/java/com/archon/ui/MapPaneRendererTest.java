package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.model.Dice;
import com.archon.model.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapPaneRendererTest {

    @Test
    void testRenderOutputsGridAndInspector() {
        World world = Scenario.testRoom(new Dice.Always(true));
        MapPaneRenderer renderer = new MapPaneRenderer();

        int targetWidth = 36;
        List<String> lines = renderer.render(world, targetWidth);

        assertFalse(lines.isEmpty());
        // Header (1) + Grid rows (8) + Inspector (2) = 11 lines
        assertEquals(world.h + 3, lines.size());

        // Header contains column indicators
        String header = Ansi.strip(lines.get(0));
        assertTrue(header.contains("0"));
        assertTrue(header.contains("11"));

        // Combined plain text of all lines
        String fullOutput = String.join("\n", lines.stream().map(Ansi::strip).toList());

        // Contains thrall and key entities
        assertTrue(fullOutput.contains("@"), "Should contain thrall glyph");
        assertTrue(fullOutput.contains("O"), "Should contain orc glyph");
        assertTrue(fullOutput.contains("G"), "Should contain goblin archer glyph");
        assertTrue(fullOutput.contains("B"), "Should contain barrel glyph");
        assertTrue(fullOutput.contains("+"), "Should contain door glyph");
        assertTrue(fullOutput.contains("#"), "Should contain wall glyph");

        // Inspector lines at the bottom
        String posLine = Ansi.strip(lines.get(lines.size() - 2));
        assertTrue(posLine.contains("Pos: (3, 4)"));
        assertTrue(posLine.contains("Terrain: floor"));

        // All lines should match or exceed targetWidth in visual length
        for (String line : lines) {
            assertEquals(targetWidth, Ansi.length(line), "Line visual length must match targetWidth");
        }
    }

    @Test
    void testRenderNullWorldDoesNotThrow() {
        MapPaneRenderer renderer = new MapPaneRenderer();
        List<String> lines = renderer.render(null, 30);
        assertEquals(1, lines.size());
        assertTrue(Ansi.strip(lines.get(0)).contains("No world data"));
    }
}
