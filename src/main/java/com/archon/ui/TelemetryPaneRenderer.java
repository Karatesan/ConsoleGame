package com.archon.ui;

import com.archon.exec.RoundState;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders Thrall vitals (HP bar, AP pips, gear, status) and live Target Radar.
 */
public final class TelemetryPaneRenderer {

    public static final int DEFAULT_MIN_WIDTH = 48;
    private static final int HP_BAR_SEGMENTS = 16;

    public List<String> render(World world, RoundState round) {
        return render(world, round, DEFAULT_MIN_WIDTH);
    }

    public List<String> render(World world, RoundState round, int targetWidth) {
        List<String> lines = new ArrayList<>();
        if (world == null || world.thrall == null) {
            lines.add(Ansi.padRight("No telemetry link", targetWidth));
            return lines;
        }

        Thrall t = world.thrall;

        // 1. HP bar
        lines.add(Ansi.padRight(renderHp(t), targetWidth));

        // 2. AP pips
        if (round != null) {
            lines.add(Ansi.padRight(renderAp(round), targetWidth));
        }

        // 3. Equipment & Inventory
        Item rightHand = t.inventory().getEquipped(EquipmentSlot.HAND_RIGHT);
        lines.add(Ansi.padRight("R-HAND: " + formatItem(rightHand), targetWidth));

        Item leftHand = t.inventory().getEquipped(EquipmentSlot.HAND_LEFT);
        lines.add(Ansi.padRight("L-HAND: " + formatItem(leftHand), targetWidth));

        lines.add(Ansi.padRight(String.format("PACK  : %d/%d items",
                t.inventory().pack().size(), Inventory.PACK_MAX), targetWidth));

        // 4. Status flags
        lines.add(Ansi.padRight(renderStatus(t), targetWidth));

        // 5. Target Radar section
        lines.add(Ansi.padRight(Ansi.style("── TARGET RADAR ──", Ansi.DIM), targetWidth));
        renderRadar(world, t, lines, targetWidth);

        return lines;
    }

    private String renderHp(Thrall t) {
        double ratio = t.maxHp > 0 ? (double) t.hp / t.maxHp : 0.0;
        int filled = (int) Math.round(ratio * HP_BAR_SEGMENTS);
        filled = Math.max(0, Math.min(HP_BAR_SEGMENTS, filled));
        int empty = HP_BAR_SEGMENTS - filled;

        String color = (ratio > 0.5) ? Ansi.BRIGHT_GREEN : (ratio > 0.25) ? Ansi.YELLOW : Ansi.BRIGHT_RED;
        String bar = Ansi.style("█".repeat(filled), color) + Ansi.style("·".repeat(empty), Ansi.BRIGHT_BLACK);

        return String.format("HP: [%s] %2d/%-2d", bar, t.hp, t.maxHp);
    }

    private String renderAp(RoundState round) {
        StringBuilder pips = new StringBuilder();
        for (int i = 0; i < RoundState.BASE_AP; i++) {
            if (i < round.ap()) {
                pips.append(Ansi.style("● ", Ansi.BRIGHT_CYAN));
            } else {
                pips.append(Ansi.style("○ ", Ansi.BRIGHT_BLACK));
            }
        }
        return String.format("AP: [%s] %d/%d (Line %d, Tax +%d)",
                pips.toString().stripTrailing(),
                round.ap(),
                RoundState.BASE_AP,
                round.linesUsed(),
                round.taxForNextLine());
    }

    private String formatItem(Item item) {
        if (item == null) return Ansi.style("empty", Ansi.BRIGHT_BLACK);
        StringBuilder sb = new StringBuilder(item.name);
        if (item.weaponDmgMax > 0) {
            sb.append(String.format(" (%d-%d dmg)", item.weaponDmgMin, item.weaponDmgMax));
        }
        if (!item.tags.isEmpty()) {
            sb.append(" ").append(Ansi.style(item.tags.toString(), Ansi.DIM));
        }
        return sb.toString();
    }

    private String renderStatus(Thrall t) {
        List<String> statuses = new ArrayList<>();
        if (t.guarded) {
            statuses.add(Ansi.style("[GUARDED]", Ansi.BRIGHT_YELLOW, Ansi.BOLD));
        }
        if (t.nocked != null) {
            statuses.add(Ansi.style("[NOCKED: " + t.nocked.name + "]", Ansi.BRIGHT_CYAN));
        }
        for (Tag tag : t.tags) {
            if (tag == Tag.BURNING) {
                statuses.add(Ansi.style("[BURNING]", Ansi.BRIGHT_RED, Ansi.BOLD));
            } else {
                statuses.add(Ansi.style("[" + tag.name() + "]", Ansi.YELLOW));
            }
        }
        if (statuses.isEmpty()) {
            statuses.add(Ansi.style("[NORMAL]", Ansi.DIM));
        }
        return "STATUS: " + String.join(" ", statuses);
    }

    private void renderRadar(World world, Thrall thrall, List<String> lines, int targetWidth) {
        List<Entity> visible = world.entities.values().stream()
                .filter(Entity::alive)
                .sorted(Comparator.comparingInt((Entity e) -> e.pos.chebyshev(thrall.pos))
                        .thenComparing(e -> e.id))
                .toList();

        if (visible.isEmpty()) {
            lines.add(Ansi.padRight(Ansi.style("  (no contacts detected)", Ansi.DIM), targetWidth));
            return;
        }

        for (Entity e : visible) {
            int dist = e.pos.chebyshev(thrall.pos);
            String distLabel = (dist <= 1) ? Ansi.style("Adj", Ansi.BRIGHT_YELLOW)
                    : Ansi.style("Dist " + dist, Ansi.DIM);

            String idLabel = Ansi.style("[" + e.id + "]", Ansi.BRIGHT_WHITE, Ansi.BOLD);

            if (e.kind == Entity.Kind.CREATURE) {
                String glyphLabel = Ansi.style(String.valueOf(e.glyph), Ansi.BRIGHT_RED);
                String hpLabel = String.format("HP %2d/%-2d", e.hp, e.maxHp);
                String mainLine = String.format("%s %-16s (%s) %s %s",
                        idLabel, e.name, glyphLabel, hpLabel, distLabel);
                lines.add(Ansi.padRight(mainLine, targetWidth));

                if (e.readied != null && !e.readiedSpent) {
                    String reaction = String.format("  └─ READIED: %s (%d dmg)",
                            e.readied.description(), e.readied.damage());
                    lines.add(Ansi.padRight(Ansi.style(reaction, Ansi.BRIGHT_RED), targetWidth));
                }
            } else {
                String kindGlyph = Ansi.style(String.valueOf(e.glyph), Ansi.YELLOW);
                String tagsLabel = e.tags.isEmpty() ? "" : Ansi.style(e.tags.toString(), Ansi.DIM);
                String mainLine = String.format("%s %-16s (%s) %s %s",
                        idLabel, e.name, kindGlyph, distLabel, tagsLabel);
                lines.add(Ansi.padRight(mainLine, targetWidth));
            }
        }
    }
}
