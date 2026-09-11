package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.World;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ConsoleView implements View {

    private static final int LOG_LINES = 14;
    private static final String HEADER = "===================== ARCHON — Action Economy Prototype =====================";
    private static final String SEPARATOR = "-----------------------------------------------------------------------------";
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*[a-zA-Z]");

    private final PrintStream out;
    private final MapPaneRenderer mapPaneRenderer;
    private final TelemetryPaneRenderer telemetryPaneRenderer;
    private final Deque<String> log = new ArrayDeque<>();

    public ConsoleView() {
        this(System.out);
    }

    public ConsoleView(PrintStream out) {
        this(out, new MapPaneRenderer(), new TelemetryPaneRenderer());
    }

    public ConsoleView(PrintStream out, MapPaneRenderer mapPaneRenderer, TelemetryPaneRenderer telemetryPaneRenderer) {
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.mapPaneRenderer = Objects.requireNonNull(mapPaneRenderer, "mapPaneRenderer must not be null");
        this.telemetryPaneRenderer = Objects.requireNonNull(telemetryPaneRenderer, "telemetryPaneRenderer must not be null");
    }

    private void emit(String text) {
        if (text == null) return;
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
            case GameEvent.Audit a -> emit(a.text());
            case GameEvent.Redraw ignored -> {}

            case GameEvent.Rejected r -> {
                emit("✗ " + r.code() + " — " + r.reason());
                if (r.hint() != null) emit("    " + r.hint());
                emit("    0 AP spent. Line not counted.");
            }
            case GameEvent.LineStart s ->
                    emit("> " + s.raw() + "   [alloc " + s.allocation() + " AP, tax " + s.tax() + ", " + (s.chain() ? "CHAIN" : "SINGLE") + "]");

            case GameEvent.StageResult s ->
                    emit(String.format("  ✓ [%d/%d] %-36s %-12s (%d AP)  AP %d", s.index(), s.total(), s.render(),
                            s.code(), s.charged(), Math.max(0, s.apLeft())));

            case GameEvent.StageSkipped s ->
                    emit(String.format("  ⚠ [%d]   %-36s SKIPPED      (%s)", s.index(), s.render(), s.why()));

            case GameEvent.LineComplete c ->
                    emit(String.format("✓ LINE COMPLETE — %d AP charged, %d tax, %d returned unused. AP %d.", c.charged(),
                            c.tax(), c.returnedUnused(), c.apLeft()));

            case GameEvent.LineBroke b -> {
                emit("✗ BREAK — " + b.reason());
                emit(String.format("    Forfeited: %d AP allocation + %d AP penalty = %d AP.", b.allocation(),
                        b.penalty(), b.forfeited()));
                if (b.hint() != null) emit("    Tip: " + b.hint());
            }
            case GameEvent.InterruptFired i ->
                    emit("⚠ INTERRUPT — " + i.source() + " (" + i.description() + "). " + i.damage() + " dmg.");

            case GameEvent.RoundEnd r -> {
                emit(r.wasted() > 0 ? "-- round ends. " + r.wasted() + " AP destroyed unspent." : "-- round ends. All AP spent.");
                r.worldLog().forEach(this::emit);
            }
            case GameEvent.RoundStart s ->
                    emit("== ROUND " + s.round() + " — AP " + s.ap() + "/" + s.ap());

            case GameEvent.ThrallDied d ->
                    emit("☠ *** THE THRALL COLLAPSES. THE LINK GOES DARK. ***");
        }
    }

    @Override
    public void frame(World w, RoundState round) {
        out.print("\033[H\033[2J");
        out.flush();
        out.print(renderFrameToString(w, round));
        out.flush();
    }

    public String renderFrameToString(World w, RoundState round) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');

        List<String> mapLines = mapPaneRenderer.render(w);
        List<String> telemetryLines = telemetryPaneRenderer.render(w, round);

        int mapWidth = 0;
        for (String line : mapLines) {
            int len = stripAnsi(line).length();
            if (len > mapWidth) {
                mapWidth = len;
            }
        }

        int rows = Math.max(mapLines.size(), telemetryLines.size());
        for (int i = 0; i < rows; i++) {
            String left = i < mapLines.size() ? mapLines.get(i) : "";
            String right = i < telemetryLines.size() ? telemetryLines.get(i) : "";
            int visibleLeft = stripAnsi(left).length();
            int pad = Math.max(0, mapWidth - visibleLeft);

            String strippedLeft = stripAnsi(left);
            String strippedRight = stripAnsi(right);
            if (hasRightBorder(strippedLeft) || hasLeftBorder(strippedRight)) {
                sb.append(left).append(" ".repeat(pad)).append("  ").append(right).append('\n');
            } else {
                sb.append(left).append(" ".repeat(pad)).append(" │ ").append(right).append('\n');
            }
        }

        sb.append(SEPARATOR).append('\n');
        for (String line : log) {
            sb.append("  ").append(line).append('\n');
        }
        sb.append(SEPARATOR).append('\n');

        return sb.toString();
    }

    public List<String> logEntries() {
        return List.copyOf(log);
    }

    private static String stripAnsi(String s) {
        if (s == null) return "";
        return ANSI_PATTERN.matcher(s).replaceAll("");
    }

    private static boolean hasRightBorder(String s) {
        if (s.isEmpty()) return false;
        char c = s.charAt(s.length() - 1);
        return c == '│' || c == '|' || c == '║' || c == '┐' || c == '┘' || c == '┤' || c == '╗' || c == '╝' || c == '╣';
    }

    private static boolean hasLeftBorder(String s) {
        if (s.isEmpty()) return false;
        char c = s.charAt(0);
        return c == '│' || c == '|' || c == '║' || c == '┌' || c == '└' || c == '├' || c == '╔' || c == '╚' || c == '╠';
    }
}