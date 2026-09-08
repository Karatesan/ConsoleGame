package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.*;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ConsoleView implements View {

    private static final int LOG_LINES = 14;

    /**
     * STREAM: print as events arrive (safe everywhere, incl. IDE consoles).
     * FULLSCREEN: buffer events, clear screen, redraw map+HUD+log as one frame.
     */
    private final boolean fullscreen;
    private final Deque<String> log = new ArrayDeque<>();
    private boolean dirty = true;

    public ConsoleView() {this(false);}

    public ConsoleView(boolean fullscreen) {this.fullscreen = fullscreen;}

    private void emit(String text) {
        for (String line : text.split("\n")) {
            if (fullscreen) {
                log.addLast(line);
                while (log.size() > LOG_LINES) log.removeFirst();
            } else {
                System.out.println("  " + line);
            }
        }
    }

    @Override
    public void handle(GameEvent e) {
        switch (e) {
            case GameEvent.Narrative n -> emit(n.text());
            case GameEvent.Audit a -> emit(a.text());
            case GameEvent.Redraw r -> dirty = true;

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

            case GameEvent.LineComplete c -> {
                emit(String.format("  LINE COMPLETE — %d AP charged, %d tax, %d returned unused. AP %d.", c.charged(),
                        c.tax(), c.returnedUnused(), c.apLeft()));
                dirty = true;
            }
            case GameEvent.LineBroke b -> {
                emit("x BREAK — " + b.reason());
                emit(String.format("    Forfeited: %d AP allocation + %d AP penalty = %d AP.", b.allocation(),
                        b.penalty(), b.forfeited()));
                if (b.hint() != null) emit("    Tip: " + b.hint());
                dirty = true;
            }
            case GameEvent.InterruptFired i ->
                    emit("!! INTERRUPT — " + i.source() + " (" + i.description() + "). " + i.damage() + " dmg.");

            case GameEvent.RoundEnd r -> {
                emit(r.wasted() > 0 ? "-- round ends. " + r.wasted() + " AP destroyed unspent." : "-- round ends. All AP spent.");
                r.worldLog().forEach(this::emit);
                dirty = true;
            }
            case GameEvent.RoundStart s -> {
                emit("== ROUND " + s.round() + " — AP " + s.ap() + "/" + s.ap());
                dirty = true;
            }
            case GameEvent.ThrallDied d -> {
                emit("*** THE THRALL COLLAPSES. THE LINK GOES DARK. ***");
                dirty = true;
            }
        }
    }

    @Override
    public void frame(World w, RoundState round) {
        if (fullscreen) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
            header();
            System.out.print(grid(w));
            System.out.print(hud(w, round));
            separator();
            log.forEach(l -> System.out.println("  " + l));
            separator();
            dirty = false;
            return;
        }
        // streaming: only redraw the board when the world actually changed
        if (!dirty) return;
        dirty = false;
        header();
        System.out.print(grid(w));
        System.out.print(hud(w, round));
        separator();
    }

    private void header() {
        System.out.println();
        System.out.println("===================== ARCHON — Action Economy Prototype =====================");
    }

    private void separator() {
        System.out.println("-----------------------------------------------------------------------------");
    }

    private String grid(World w) {
        StringBuilder sb = new StringBuilder("    ");
        for (int x = 0; x < w.w; x++) sb.append(x % 10).append(' ');
        sb.append('\n');
        for (int y = 0; y < w.h; y++) {
            sb.append(String.format("%3d ", y));
            for (int x = 0; x < w.w; x++) {
                Vec2 p = new Vec2(x, y);
                World.Tile t = w.tile(p);
                Entity e = w.entityAt(p);
                char c;
                if (t.wall) c = '#';
                else if (e != null) c = e.glyph;
                else if (t.has(Tag.BURNING)) c = '*';
                else if (t.has(Tag.OIL)) c = '~';
                else if (!t.ground.isEmpty()) c = '%';
                else c = '.';
                sb.append(c).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String hud(World w, RoundState r) {
        Thrall t = w.thrall;
        StringBuilder pips = new StringBuilder();
        for (int i = 0; i < RoundState.BASE_AP; i++) pips.append(i < r.ap() ? '*' : 'o').append(' ');
        return String.format("""
                        ROUND %d   HP %d/%d   AP [%s] %d/%d   LINE %d (next tax: +%d AP)
                        hand/right: %-18s hand/left: %-18s pack %d/%d
                        """, r.roundNo(), t.hp, t.maxHp, pips.toString().trim(), r.ap(), RoundState.BASE_AP, r.linesUsed(),
                r.taxForNextLine(), name(t.slots.get("hand/right")), name(t.slots.get("hand/left")), t.pack.size(),
                Thrall.PACK_MAX);
    }

    private String name(Item i) {return i == null ? "empty" : i.name;}
}