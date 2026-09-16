package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.GameMap;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

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

    public ConsoleView() {
        this(false);
    }

    public ConsoleView(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    private void emit(String text) {
        for (String line : text.split("\n")) {
            if (fullscreen) {
                log.addLast(line);
                while (log.size() > LOG_LINES) {
                    log.removeFirst();
                }
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
                if (r.hint() != null) {
                    emit("    " + r.hint());
                }
                emit("    0 AP spent. Line not counted.");
            }
            case GameEvent.LineStart s ->
                    emit("> " + s.raw() + "   [alloc " + s.allocation() + " AP, tax " + s.tax() + ", "
                            + (s.chain() ? "CHAIN" : "SINGLE") + "]");

            case GameEvent.StageResult s ->
                    emit(String.format("  [%d/%d] %-36s %-12s (%d AP)  AP %d", s.index(), s.total(), s.render(),
                            s.code(), s.charged(), Math.max(0, s.apLeft())));

            case GameEvent.StageSkipped s ->
                    emit(String.format("  [%d]   %-36s SKIPPED      (%s)", s.index(), s.render(), s.why()));

            case GameEvent.LineComplete c -> {
                emit(String.format("  LINE COMPLETE — %d AP charged, %d tax, %d returned unused. AP %d.",
                        c.charged(), c.tax(), c.returnedUnused(), c.apLeft()));
                dirty = true;
            }
            case GameEvent.LineBroke b -> {
                emit("x BREAK — " + b.reason());
                emit(String.format("    Forfeited: %d AP allocation + %d AP penalty = %d AP.", b.allocation(),
                        b.penalty(), b.forfeited()));
                if (b.hint() != null) {
                    emit("    Tip: " + b.hint());
                }
                dirty = true;
            }
            case GameEvent.InterruptFired i ->
                    emit("!! INTERRUPT — " + i.source() + " (" + i.description() + "). " + i.damage() + " dmg.");

            case GameEvent.RoundEnd r -> {
                emit(r.wasted() > 0
                        ? "-- round ends. " + r.wasted() + " AP destroyed unspent."
                        : "-- round ends. All AP spent.");
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
    public void frame(World world, RoundState round) {
        if (fullscreen) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
            header();
            System.out.print(grid(world));
            System.out.print(hud(world, round));
            separator();
            log.forEach(line -> System.out.println("  " + line));
            separator();
            dirty = false;
            return;
        }

        // Streaming: only redraw the board when the world actually changed.
        if (!dirty) {
            return;
        }

        dirty = false;
        header();
        System.out.print(grid(world));
        System.out.print(hud(world, round));
        separator();
    }

    private void header() {
        System.out.println();
        System.out.println("===================== ARCHON — Action Economy Prototype =====================");
    }

    private void separator() {
        System.out.println("-----------------------------------------------------------------------------");
    }

    private String grid(World world) {
        StringBuilder sb = new StringBuilder("    ");
        for (int x = 0; x < world.width(); x++) {
            sb.append(x % 10).append(' ');
        }
        sb.append('\n');

        for (int y = 0; y < world.height(); y++) {
            sb.append(String.format("%3d ", y));
            for (int x = 0; x < world.width(); x++) {
                Vec2 position = new Vec2(x, y);
                GameMap.Tile tile = world.tile(position);
                Entity entity = SpatialService.entityAt(world, position);
                char glyph;

                if (tile.isWall()) {
                    glyph = '#';
                } else if (entity != null) {
                    glyph = entity.glyph();
                } else if (tile.has(Tag.BURNING)) {
                    glyph = '*';
                } else if (tile.has(Tag.OIL)) {
                    glyph = '~';
                } else if (!tile.ground().isEmpty()) {
                    glyph = '%';
                } else {
                    glyph = '.';
                }

                sb.append(glyph).append(' ');
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private String hud(World world, RoundState round) {
        Thrall thrall = world.thrall();
        StringBuilder pips = new StringBuilder();

        for (int i = 0; i < RoundState.BASE_AP; i++) {
            pips.append(i < round.ap() ? '*' : 'o').append(' ');
        }

        return String.format("""
                        ROUND %d   HP %d/%d   AP [%s] %d/%d   LINE %d (next tax: +%d AP)
                        hand/right: %-18s hand/left: %-18s pack %d/%d
                        """,
                round.roundNo(),
                thrall.hp(),
                thrall.maxHp(),
                pips.toString().trim(),
                round.ap(),
                RoundState.BASE_AP,
                round.linesUsed(),
                round.taxForNextLine(),
                name(thrall.inventory().equipped(EquipmentSlot.HAND_RIGHT)),
                name(thrall.inventory().equipped(EquipmentSlot.HAND_LEFT)),
                thrall.inventory().pack().size(),
                Inventory.PACK_MAX);
    }

    private String name(Item item) {
        return item == null ? "empty" : item.name();
    }
}