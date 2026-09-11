package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.World;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ConsoleView implements View {

    public static final int MAP_WIDTH = 34;
    public static final int TELEMETRY_WIDTH = 52;
    public static final int LOG_LINES = 10;
    private static final int INNER_TOTAL_WIDTH = MAP_WIDTH + 2 + 1 + TELEMETRY_WIDTH + 2; // 91
    private static final int LOG_CONTENT_WIDTH = INNER_TOTAL_WIDTH - 2;                   // 89

    private final PrintStream out;
    private final MapPaneRenderer mapPane;
    private final TelemetryPaneRenderer telemetryPane;
    private final Deque<String> log = new ArrayDeque<>();

    public ConsoleView() {
        this(System.out);
    }

    public ConsoleView(PrintStream out) {
        this.out = out;
        this.mapPane = new MapPaneRenderer();
        this.telemetryPane = new TelemetryPaneRenderer();
    }

    private void emit(String text) {
        for (String line : text.split("\n")) {
            log.addLast(line);
            while (log.size() > LOG_LINES) {
                log.removeFirst();
            }
        }
    }

    @Override
    public void handle(GameEvent e) {
        switch (e) {
            case GameEvent.Narrative n -> emit(n.text());
            case GameEvent.Audit a -> emit(Ansi.style("[AUDIT] ", Ansi.BRIGHT_CYAN) + a.text());
            case GameEvent.Redraw r -> {}

            case GameEvent.Rejected r -> {
                emit(Ansi.style("✗ " + r.code(), Ansi.BRIGHT_RED) + " — " + r.reason());
                if (r.hint() != null) emit("    " + Ansi.style(r.hint(), Ansi.DIM));
                emit("    0 AP spent. Line not counted.");
            }
            case GameEvent.LineStart s ->
                    emit(Ansi.style("> ", Ansi.BRIGHT_WHITE, Ansi.BOLD) + s.raw() + "  "
                            + Ansi.style("[alloc " + s.allocation() + " AP, tax " + s.tax() + ", "
                            + (s.chain() ? "CHAIN" : "SINGLE") + "]", Ansi.DIM));

            case GameEvent.StageResult s ->
                    emit(String.format("  [%d/%d] %-30s %-10s (%d AP)  AP %d",
                            s.index(), s.total(), s.render(), s.code(), s.charged(), Math.max(0, s.apLeft())));

            case GameEvent.StageSkipped s ->
                    emit(String.format("  [%d]   %-30s SKIPPED     (%s)",
                            s.index(), s.render(), s.why()));

            case GameEvent.LineComplete c ->
                    emit(Ansi.style("✓ LINE COMPLETE", Ansi.BRIGHT_GREEN)
                            + String.format(" — %d AP charged, %d tax, %d returned unused. AP %d.",
                            c.charged(), c.tax(), c.returnedUnused(), c.apLeft()));

            case GameEvent.LineBroke b -> {
                emit(Ansi.style("✗ BREAK", Ansi.BRIGHT_RED, Ansi.BOLD) + " — " + b.reason());
                emit(String.format("    Forfeited: %d AP allocation + %d AP penalty = %d AP.",
                        b.allocation(), b.penalty(), b.forfeited()));
                if (b.hint() != null) emit("    Tip: " + b.hint());
            }
            case GameEvent.InterruptFired i ->
                    emit(Ansi.style("⚠ INTERRUPT", Ansi.BRIGHT_RED, Ansi.BOLD)
                            + " — " + i.source() + " (" + i.description() + "). " + i.damage() + " dmg.");

            case GameEvent.RoundEnd r -> {
                emit(Ansi.style("── ROUND SETTLED ── "
                        + (r.wasted() > 0 ? r.wasted() + " AP destroyed unspent." : "All AP spent."), Ansi.DIM));
                r.worldLog().forEach(this::emit);
            }
            case GameEvent.RoundStart s ->
                    emit(Ansi.style("── ROUND " + s.round() + " ── AP " + s.ap() + "/" + s.ap(), Ansi.BRIGHT_CYAN));

            case GameEvent.ThrallDied d ->
                    emit(Ansi.style("*** THE THRALL COLLAPSES. THE LINK GOES DARK. ***", Ansi.BRIGHT_RED, Ansi.BOLD));
        }
    }

    @Override
    public void frame(World w, RoundState round) {
        out.print(Ansi.CLEAR_SCREEN);
        out.print(renderFrameToString(w, round));
        out.flush();
    }

    public String renderFrameToString(World w, RoundState round) {
        StringBuilder sb = new StringBuilder();

        // 1. Top border
        String topTitleLeft = " MAP VIEWPORT ";
        String topTitleRight = " THRALL TELEMETRY ";
        String borderTopLeft = "──" + topTitleLeft + "─".repeat(Math.max(0, MAP_WIDTH + 2 - 2 - topTitleLeft.length()));
        String borderTopRight = "──" + topTitleRight + "─".repeat(Math.max(0, TELEMETRY_WIDTH + 2 - 2 - topTitleRight.length()));

        sb.append(Ansi.style("┌" + borderTopLeft + "┬" + borderTopRight + "┐", Ansi.DIM)).append('\n');

        // 2. Dual Pane content
        List<String> mapLines = mapPane.render(w, MAP_WIDTH);
        List<String> telemetryLines = telemetryPane.render(w, round, TELEMETRY_WIDTH);
        int maxRows = Math.max(mapLines.size(), telemetryLines.size());

        for (int i = 0; i < maxRows; i++) {
            String left = i < mapLines.size() ? mapLines.get(i) : Ansi.padRight("", MAP_WIDTH);
            String right = i < telemetryLines.size() ? telemetryLines.get(i) : Ansi.padRight("", TELEMETRY_WIDTH);

            sb.append(Ansi.style("│ ", Ansi.DIM))
              .append(left)
              .append(Ansi.style(" │ ", Ansi.DIM))
              .append(right)
              .append(Ansi.style(" │", Ansi.DIM))
              .append('\n');
        }

        // 3. Middle split-bottom border
        sb.append(Ansi.style("├" + "─".repeat(MAP_WIDTH + 2) + "┴" + "─".repeat(TELEMETRY_WIDTH + 2) + "┤", Ansi.DIM)).append('\n');

        // 4. Log buffer rows
        List<String> logSnapshot = new ArrayList<>(log);
        for (int i = 0; i < LOG_LINES; i++) {
            String entry = i < logSnapshot.size() ? logSnapshot.get(i) : "";
            sb.append(Ansi.style("│ ", Ansi.DIM))
              .append(Ansi.padRight(entry, LOG_CONTENT_WIDTH))
              .append(Ansi.style(" │", Ansi.DIM))
              .append('\n');
        }

        // 5. Hint footer separator & content
        sb.append(Ansi.style("├" + "─".repeat(INNER_TOTAL_WIDTH) + "┤", Ansi.DIM)).append('\n');

        String hints = Ansi.style("[help]", Ansi.BRIGHT_WHITE) + " manual | "
                + Ansi.style("[scan]", Ansi.BRIGHT_WHITE) + " targets | "
                + Ansi.style("[inspect <id>]", Ansi.BRIGHT_WHITE) + " detail | "
                + Ansi.style("[pass]", Ansi.BRIGHT_WHITE) + " end turn | "
                + Ansi.style("[quit]", Ansi.BRIGHT_WHITE) + " exit";

        sb.append(Ansi.style("│ ", Ansi.DIM))
          .append(Ansi.padRight(hints, LOG_CONTENT_WIDTH))
          .append(Ansi.style(" │", Ansi.DIM))
          .append('\n');

        sb.append(Ansi.style("└" + "─".repeat(INNER_TOTAL_WIDTH) + "┘", Ansi.DIM)).append('\n');

        return sb.toString();
    }

    public List<String> logEntries() {
        return List.copyOf(log);
    }
}
