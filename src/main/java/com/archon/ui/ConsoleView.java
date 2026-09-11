package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class ConsoleView implements View {

    private static final int LOG_LINES = 10;

    private final MapPaneRenderer mapPaneRenderer;
    private final TelemetryPaneRenderer telemetryPaneRenderer;
    private final Deque<String> log = new ArrayDeque<>();

    public ConsoleView() {
        this(new MapPaneRenderer(), new TelemetryPaneRenderer());
    }

    public ConsoleView(MapPaneRenderer mapPaneRenderer, TelemetryPaneRenderer telemetryPaneRenderer) {
        this.mapPaneRenderer = mapPaneRenderer;
        this.telemetryPaneRenderer = telemetryPaneRenderer;
    }

    private void emit(String text) {
        for (String line : text.split("\n")) {
            log.addLast(line);
            while (log.size() > LOG_LINES) log.removeFirst();
        }
    }

    @Override
    public void handle(GameEvent e) {
        switch (e) {
            case GameEvent.Narrative n -> emit(n.text());
            case GameEvent.Audit a -> emit(a.text());
            case GameEvent.Redraw r -> {}

            case GameEvent.Rejected r -> {
                emit("x " + r.code() + " — " + r.reason());
                if (r.hint() != null) emit("    " + r.hint());
                emit("    0 AP spent. Line not counted.");
            }
            case GameEvent.LineStart s ->
                    emit("> " + s.raw() + "   [alloc " + s.allocation() + " AP, tax " + s.tax() + ", " + (s.chain() ? "CHAIN" : "SINGLE") + "]");

            case GameEvent.StageResult s ->
                    emit(String.format("  [%d/%d] %-36s %-12s (%d AP)  AP %d", s.index(), s.total(), s.render(),
                            s.code(), s.charged(), Math.max(0, s.apLeft())));

            case GameEvent.StageSkipped s ->
                    emit(String.format("  [%d]   %-36s SKIPPED      (%s)", s.index(), s.render(), s.why()));

            case GameEvent.LineComplete c ->
                    emit(String.format("  LINE COMPLETE — %d AP charged, %d tax, %d returned unused. AP %d.", c.charged(),
                            c.tax(), c.returnedUnused(), c.apLeft()));

            case GameEvent.LineBroke b -> {
                emit("x BREAK — " + b.reason());
                emit(String.format("    Forfeited: %d AP allocation + %d AP penalty = %d AP.", b.allocation(),
                        b.penalty(), b.forfeited()));
                if (b.hint() != null) emit("    Tip: " + b.hint());
            }
            case GameEvent.InterruptFired i ->
                    emit("!! INTERRUPT — " + i.source() + " (" + i.description() + "). " + i.damage() + " dmg.");

            case GameEvent.RoundEnd r -> {
                emit(r.wasted() > 0 ? "-- round ends. " + r.wasted() + " AP destroyed unspent." : "-- round ends. All AP spent.");
                r.worldLog().forEach(this::emit);
            }
            case GameEvent.RoundStart s ->
                    emit("== ROUND " + s.round() + " — AP " + s.ap() + "/" + s.ap());

            case GameEvent.ThrallDied d ->
                    emit("*** THE THRALL COLLAPSES. THE LINK GOES DARK. ***");
        }
    }

    @Override
    public void frame(World w, RoundState round) {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.print(renderFrameToString(w, round));
    }

    public String renderFrameToString(World w, RoundState round) {
        StringBuilder sb = new StringBuilder();

        List<String> mapLines = mapPaneRenderer.render(w);
        List<String> telemetryLines = telemetryPaneRenderer.render(w, round);

        int mapWidth = 0;
        for (String line : mapLines) {
            if (line.length() > mapWidth) {
                mapWidth = line.length();
            }
        }

        int splitRows = Math.max(mapLines.size(), telemetryLines.size());
        for (int i = 0; i < splitRows; i++) {
            String left = i < mapLines.size() ? mapLines.get(i) : "";
            String right = i < telemetryLines.size() ? telemetryLines.get(i) : "";
            sb.append(left);
            if (!right.isEmpty() && left.length() < mapWidth) {
                sb.append(" ".repeat(mapWidth - left.length()));
            }
            sb.append(right).append('\n');
        }

        int logCount = 0;
        for (String line : log) {
            sb.append("  ").append(line).append('\n');
            logCount++;
        }
        while (logCount < LOG_LINES) {
            sb.append('\n');
            logCount++;
        }

        return sb.toString();
    }
}
