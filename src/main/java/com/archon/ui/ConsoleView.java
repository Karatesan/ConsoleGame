package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.Thrall;
import com.archon.model.World;

public final class ConsoleView implements View {

    private boolean forceMap = true;
    private String lastMap = "";
    private String lastStatus = "";

    private void emit(String label, String text) {
        for (String line : text.split("\\R")) {
            System.out.printf("  %-6s %s%n", label, line);
            label = "|";
        }
    }

    private void indent(String text) {
        for (String line : text.split("\\R")) {
            System.out.printf("           %s%n", line);
        }
    }

    @Override
    public void handle(GameEvent event) {
        switch (event) {
            case GameEvent.Narrative narrative -> indent(narrative.text());
            case GameEvent.Redraw redraw -> forceMap = true;

            case GameEvent.Rejected rejected -> {
                emit("REJECT", rejected.code().name() + " — " + rejected.reason());
                if (rejected.hint() != null) {
                    emit("HINT", rejected.hint());
                }
                emit("COST", "No AP spent; paid-line count unchanged.");
            }
            case GameEvent.LineStart start -> emit("RUN",
                    String.format("%s  (alloc %d AP, tax %d AP)", start.raw(), start.allocation(), start.tax()));

            case GameEvent.StageStart start -> {
                if (start.total() > 1) {
                    emit("[" + start.index() + "/" + start.total() + "]", start.render());
                }
            }

            case GameEvent.StageResult result -> {
                String charge = result.charged() > 0 ? " (" + result.charged() + " AP)" : "";
                System.out.printf("         -> %s%s%n", result.code().name(), charge);
            }

            case GameEvent.StageSkipped skipped -> {
                emit("[" + skipped.index() + "/" + skipped.total() + "]", skipped.render());
                System.out.printf("         -> SKIPPED (%s)%n", skipped.why());
            }

            case GameEvent.LineComplete complete -> {
                int total = complete.charged() + complete.tax();
                emit("COST", String.format("%d AP spent (%d AP stages + %d AP tax) -> %d AP left",
                        total, complete.charged(), complete.tax(), complete.apLeft()));
            }

            case GameEvent.LineBroke broke -> {
                emit("BREAK", broke.reason());
                emit("COST", "Forfeited " + broke.forfeited() + " AP -> " + broke.apLeft() + " AP left");
                indent("Allocation " + broke.allocation() + " + penalty " + broke.penalty() + ", capped by AP after tax.");
                indent("Replaces stage charges; excludes commitment tax.");
                if (broke.hint() != null) {
                    emit("HINT", broke.hint());
                }
            }
            case GameEvent.InterruptFired interrupt -> emit("REACT",
                    interrupt.source() + " — " + interrupt.description() + " (" + interrupt.damage() + " dmg)");

            case GameEvent.RoundEnd end -> emit("ROUND", "Ended (unspent AP discarded: " + end.wasted() + ")");
            case GameEvent.EnemyPhaseStart start -> { }
            case GameEvent.Thinking thinking -> {
                System.out.print("\r  ENEMY  " + ".".repeat(thinking.dots()));
                if (thinking.dots() == 3) System.out.println();
                System.out.flush();
            }
            case GameEvent.ActorBeat beat -> {
                emit(beat.id(), beat.name() + (beat.from().equals(beat.to()) ? "" : " " + beat.from() + " -> " + beat.to()));
                beat.lines().forEach(this::indent);
            }
            case GameEvent.EnvBeat beat -> beat.lines().forEach(this::indent);
            case GameEvent.RoundStart start -> emit("ROUND", start.round() + " begins (AP " + start.ap() + ")");

            case GameEvent.ThrallDied died -> {
                emit("DEATH", "THE THRALL COLLAPSES. THE LINK GOES DARK.");
                forceMap = true;
            }
        }
    }

    @Override
    public void frame(World world, RoundState round) {
        String map = grid(new Scene(world), null);
        String status = hud(world, round);
        if (!forceMap && map.equals(lastMap) && status.equals(lastStatus)) {
            return;
        }

        System.out.printf("%n--- %s ---%n",
                world.thrall().alive() ? "STATE (Round " + round.roundNo() + ")" : "RUN ENDED");
        System.out.print(status);
        if (forceMap || !world.thrall().alive() || !map.equals(lastMap)) {
            System.out.println();
            System.out.print(map);
        }
        System.out.println("----------------------------------------------------------------");

        lastMap = map;
        lastStatus = status;
        forceMap = false;
    }

    @Override
    public void scene(Scene scene, Scene prev) {
        System.out.print(grid(scene, prev));
        lastMap = grid(scene, null);
    }

    /** Ids on the map; "id*" = moved this beat, "(id)" = where it came from. */
    private String grid(Scene s, Scene prev) {
        StringBuilder out = new StringBuilder("     ");
        for (int x = 0; x < s.w; x++) out.append(String.format("%-5d", x));
        out.append('\n');
        for (int y = 0; y < s.h; y++) {
            out.append(String.format("%3d  ", y));
            for (int x = 0; x < s.w; x++) out.append(String.format("%-5s", cell(s, prev, x, y)));
            out.append('\n');
        }
        return out.toString();
    }

    private String cell(Scene s, Scene prev, int x, int y) {
        for (int i = 0; i < s.n; i++) {
            if (s.hp[i] <= 0) continue;
            String label = i == 0 ? "@" : s.id[i];
            if (s.x[i] == x && s.y[i] == y) return prev != null && s.moved(i, prev) ? label + "*" : label;
            if (prev != null && s.moved(i, prev)) {
                int p = prev.indexOf(s.id[i]);
                if (prev.x[p] == x && prev.y[p] == y) return "(" + label + ")";
            }
        }
        int t = y * s.w + x;
        return s.wall[t] ? "####"
                : s.burning[t] ? "*"
                : s.superheatedSteam[t] ? "!"
                : s.oilMist[t] ? "?"
                : s.steam[t] ? "^"
                : s.tar[t] ? "&"
                : s.acid[t] ? "a"
                : s.ice[t] ? "="
                : s.water[t] ? "w"
                : s.oil[t] ? "~"
                : s.loot[t] ? "%" : ".";
    }

    private String hud(World world, RoundState round) {
        Thrall thrall = world.thrall();
        String pips = ("* ".repeat(Math.max(0, round.ap()))
                + "o ".repeat(Math.max(0, round.rules().roundAp() - round.ap()))).stripTrailing();
        String channel = thrall.channeledPayload == null
                ? "none"
                : thrall.channeledPayload.name() + " (" + thrall.channelTurns + "t)";
        String taxInfo = thrall.alive()
                ? String.format("   (Paid lines: %d | Next tax: %d AP)",
                        round.linesUsed(), round.rules().taxForNextLine(round.linesUsed()))
                : "";

        return String.format(
                "HP %d/%d   AP [%s] %d/%d   Magic %d   Temp %dC   Channel: %s%s%n"
                        + "Hands: [R] %s   [L] %s   Pack: %d/%d%n"
                        + "Status: Guard %s | Arrow %s | Conditions: %s%n",
                thrall.hp(), thrall.maxHp(), pips, round.ap(), round.rules().roundAp(),
                thrall.magic, thrall.temp, channel, taxInfo,
                name(thrall.inventory().equipped(EquipmentSlot.HAND_RIGHT)),
                name(thrall.inventory().equipped(EquipmentSlot.HAND_LEFT)),
                thrall.inventory().pack().size(), Inventory.PACK_MAX,
                thrall.isGuarded() ? "raised" : "down",
                thrall.isNocked() ? "nocked" : "not nocked",
                Scene.formatShortConds(thrall));
    }

    private String name(Item item) {
        return item == null ? "empty" : item.name();
    }
}