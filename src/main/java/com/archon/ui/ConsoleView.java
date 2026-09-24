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

import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Override
    public void handle(GameEvent event) {
        switch (event) {
            case GameEvent.Narrative narrative -> emit("|", narrative.text());
            case GameEvent.Audit audit -> emit("AUDIT", audit.text());
            case GameEvent.Redraw redraw -> forceMap = true;

            case GameEvent.Rejected rejected -> {
                emit("REJECT", rejected.code() + " — " + rejected.reason());
                if (rejected.hint() != null) {
                    emit("HINT", rejected.hint());
                }
                emit("COST", "No AP spent; paid-line count unchanged.");
            }
            case GameEvent.LineStart start -> emit("RUN",
                    start.raw() + " | Allocation " + start.allocation() + " AP | Tax " + start.tax() + " AP");

            case GameEvent.StageResult result -> emit("RESULT",
                    result.total() == 1 ? result.code().name() : String.format("%d/%d %s | Trace charge %d AP | %s",
                            result.index(), result.total(), result.code(), result.charged(), result.render()));

            case GameEvent.StageSkipped skipped ->
                    emit("SKIP", skipped.index() + " " + skipped.render() + " | " + skipped.why());

            case GameEvent.LineComplete complete -> emit("COST",
                    String.format("%d AP charged | Tax %d AP | Remaining %d AP", complete.charged(), complete.tax(),
                            complete.apLeft()));

            case GameEvent.LineBroke broke -> {
                emit("BREAK", broke.reason());
                emit("COST", "Forfeited " + broke.forfeited() + " AP | Remaining " + broke.apLeft() + " AP");
                emit("|",
                        "Allocation " + broke.allocation() + " + penalty " + broke.penalty() + ", capped by AP after tax.");
                emit("|", "Replaces stage charges; excludes commitment tax.");
                if (broke.hint() != null) {
                    emit("HINT", broke.hint());
                }
            }
            case GameEvent.InterruptFired interrupt -> emit("REACT",
                    interrupt.source() + " — " + interrupt.description() + " | " + interrupt.damage() + " dmg");

            case GameEvent.RoundEnd end -> {
                emit("ROUND", "Ended | Unspent AP discarded: " + end.wasted());
                end.worldLog().forEach(line -> emit("|", line));
            }
            case GameEvent.RoundStart start -> emit("ROUND", start.round() + " begins | AP " + start.ap());

            case GameEvent.ThrallDied died -> {
                emit("DEATH", "THE THRALL COLLAPSES. THE LINK GOES DARK.");
                forceMap = true;
            }
        }
    }

    @Override
    public void frame(World world, RoundState round) {
        String map = grid(world);
        String status = hud(world, round);
        if (!forceMap && map.equals(lastMap) && status.equals(lastStatus)) {
            return;
        }

        System.out.printf("%n--- %s ---%n", world.thrall().alive() ? "STATE" : "RUN ENDED");
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

    private String grid(World world) {
        StringBuilder output = new StringBuilder("    ");
        for (int x = 0; x < world.width(); x++) {
            output.append(x % 10).append(' ');
        }
        output.append('\n');

        for (int y = 0; y < world.height(); y++) {
            output.append(String.format("%3d ", y));
            for (int x = 0; x < world.width(); x++) {
                Vec2 position = new Vec2(x, y);
                GameMap.Tile tile = world.tile(position);
                Entity entity = SpatialService.entityAt(world, position);
                char glyph = '.';

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
                }

                output.append(glyph).append(' ');
            }
            output.append('\n');
        }

        return output.toString();
    }

    private String hud(World world, RoundState round) {
        Thrall thrall = world.thrall();
        String conditions = Stream.of(Tag.BURNING, Tag.WET, Tag.FROZEN, Tag.CORRODED, Tag.PRONE, Tag.STUNNED,
                Tag.BLIND).filter(thrall::has).map(Tag::name).collect(Collectors.joining(", "));
        String pips = "* ".repeat(round.ap()) + "o ".repeat(round.rules().roundAp() - round.ap());

        return "Round " + round.roundNo() + "   HP " + thrall.hp() + "/" + thrall.maxHp() + "   AP [" + pips.stripTrailing() + "] " + round.ap() + "/" + round.rules().roundAp() + "\nRight: " + name(
                thrall.inventory().equipped(EquipmentSlot.HAND_RIGHT)) + "   Left: " + name(thrall.inventory().equipped(
                EquipmentSlot.HAND_LEFT)) + "   Pack: " + thrall.inventory().pack().size() + "/" + Inventory.PACK_MAX + "\nConditions: " + (conditions.isEmpty() ? "none" : conditions) + "   Guard: " + (thrall.isGuarded() ? "raised" : "down") + "   Arrow: " + (thrall.isNocked() ? "nocked" : "not nocked") + (thrall.alive() ? "\nPaid lines: " + round.linesUsed() + "   Next tax: " + round.rules().taxForNextLine(
                round.linesUsed()) + " AP" : "") + "\n";
    }

    private String name(Item item) {
        return item == null ? "empty" : item.name();
    }
}