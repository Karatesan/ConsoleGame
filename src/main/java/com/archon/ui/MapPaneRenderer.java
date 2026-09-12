package com.archon.ui;

import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the map viewport with coordinate indices, styled glyphs, and tile inspector.
 */
public final class MapPaneRenderer {

    public static final int DEFAULT_MIN_WIDTH = 34;

    public List<String> render(World world) {
        return render(world, DEFAULT_MIN_WIDTH);
    }

    public List<String> render(World world, int targetWidth) {
        List<String> lines = new ArrayList<>();
        if (world == null) {
            lines.add(Ansi.padRight("No world data", targetWidth));
            return lines;
        }

        // 1. Column numbers header
        StringBuilder colHeader = new StringBuilder("    ");
        for (int x = 0; x < world.w; x++) {
            colHeader.append(String.format("%2d", x));
        }
        lines.add(Ansi.padRight(Ansi.style(colHeader.toString(), Ansi.DIM), targetWidth));

        // 2. Map grid rows
        for (int y = 0; y < world.h; y++) {
            StringBuilder row = new StringBuilder();
            row.append(Ansi.style(String.format("%2d  ", y), Ansi.DIM));

            for (int x = 0; x < world.w; x++) {
                Vec2 p = new Vec2(x, y);
                World.Tile tile = world.tile(p);
                Entity entity = world.entityAt(p);
                row.append(formatCell(tile, entity)).append(' ');
            }
            lines.add(Ansi.padRight(row.toString(), targetWidth));
        }

        // 3. Tile / Thrall Context Inspector
        Thrall thrall = world.thrall;
        if (thrall != null) {
            World.Tile currentTile = world.tile(thrall.pos);
            String posLine = String.format("Pos: (%d, %d) | Terrain: %s",
                    thrall.pos.x, thrall.pos.y, currentTile.wall ? "wall" : "floor");
            lines.add(Ansi.padRight(Ansi.style(posLine, Ansi.BRIGHT_BLACK), targetWidth));

            String groundInfo = currentTile.ground.isEmpty() ? "none" : currentTile.ground.toString();
            String tileTags = currentTile.tags.isEmpty() ? "none" : currentTile.tags.toString();
            String detailLine = String.format("Tile: %s | Ground: %s", tileTags, groundInfo);
            lines.add(Ansi.padRight(Ansi.style(detailLine, Ansi.BRIGHT_BLACK), targetWidth));
        }

        return lines;
    }

    private String formatCell(World.Tile tile, Entity entity) {
        if (tile.wall) {
            return Ansi.style("#", Ansi.BRIGHT_BLACK, Ansi.BOLD);
        }
        if (entity != null) {
            return switch (entity.kind) {
                case CREATURE -> {
                    if (entity.glyph == '@') {
                        yield Ansi.style("@", Ansi.BRIGHT_GREEN, Ansi.BOLD);
                    }
                    yield Ansi.style(String.valueOf(entity.glyph), Ansi.BRIGHT_RED, Ansi.BOLD);
                }
                case DOOR -> Ansi.style(String.valueOf(entity.glyph), Ansi.BRIGHT_YELLOW, Ansi.BOLD);
                case PROP -> Ansi.style(String.valueOf(entity.glyph), Ansi.YELLOW);
            };
        }
        if (tile.has(Tag.BURNING)) {
            return Ansi.style("*", Ansi.BRIGHT_RED, Ansi.BOLD);
        }
        if (tile.has(Tag.OIL)) {
            return Ansi.style("~", Ansi.YELLOW);
        }
        if (!tile.ground.isEmpty()) {
            return Ansi.style("%", Ansi.BRIGHT_CYAN);
        }
        return Ansi.style("·", Ansi.BRIGHT_BLACK);
    }
}
