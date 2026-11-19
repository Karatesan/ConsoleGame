package com.archon.exec;

import com.archon.command.Ast;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Tag;
import com.archon.model.World;
import com.archon.verb.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Validator {

    public record Plan(boolean free, int allocation, int tax, int surgeBonus) {
        public Plan(boolean free, int allocation, int tax) {
            this(free, allocation, tax, 0);
        }

        public int requiredAp() {
            return Math.max(0, allocation + tax - surgeBonus);
        }
    }

    private final World world;
    private final VerbCatalog catalog;

    public Validator(World world, VerbCatalog catalog) {
        this.world = world;
        this.catalog = catalog;
    }

    public int stageCost(Ast.Stage stage) {
        Actor thrall = world.thrall();
        return stageCost(stage, thrall != null && thrall.has(Tag.PRONE));
    }

    private int stageCost(Ast.Stage stage, boolean thrallProne) {
        List<Ast.Invocation> pipeline = stage.pipeline();
        int total = 0;
        for (int i = 0; i < pipeline.size(); i++) {
            total += invocationCost(pipeline.get(i), i > 0, thrallProne);
        }
        return total;
    }

    private int invocationCost(Ast.Invocation inv, boolean piped, boolean thrallProne) {
        return switch (inv.verb()) {
            case "bolt" -> {
                if (inv.hasFlag("blood")) yield Balance.AP_BOLT_BLOOD;
                boolean aimed = isAimed(inv);
                if (piped) yield aimed ? Balance.AP_BOLT_KIN : Balance.AP_BOLT_PAYLOAD;
                yield aimed ? Balance.AP_BOLT_KIN_AIMED : Balance.AP_BOLT_KIN;
            }
            case "blast" -> inv.hasFlag("blood") ? Balance.AP_BLAST_BLOOD : Balance.AP_BLAST;
            case "siphon" -> inv.hasFlag("blood") ? Balance.AP_SIPHON_BLOOD : Balance.AP_SIPHON;
            case "push" -> Balance.AP_PUSH;
            case "pull" -> Balance.AP_PULL;
            case "channel" -> inv.hasFlag("blood")
                    ? Balance.AP_CHANNEL_BLOOD
                    : (inv.hasFlag("force") ? Balance.AP_CHANNEL_FORCE : Balance.AP_CHANNEL_THERMAL);
            case "pass" -> inv.hasFlag("roll") ? Balance.AP_PASS_R : 0;
            case "step" -> {
                if (thrallProne) {
                    yield Balance.AP_STAND_UP;
                }
                int cost = catalog.get("step").price().cost(inv);
                Actor thrall = world.thrall();
                if (thrall != null) {
                    if (thrall.has(Tag.CHILLED)) cost += Balance.CHILLED_AP_STEP;
                    if (thrall.has(Tag.CRIPPLED)) cost += Balance.CRIPPLED_AP_STEP;
                }
                yield cost;
            }
            default -> catalog.get(inv.verb()).price().cost(inv);
        };
    }

    private static boolean isAimed(Ast.Invocation inv) {
        return inv.hasFlag("aim") || (inv.arg(0) != null && inv.arg(0).contains("/body/"));
    }

    private static boolean isTorsoAimed(Ast.Invocation inv) {
        String aim = inv.flag("aim");
        if ("torso".equalsIgnoreCase(aim) || "t".equalsIgnoreCase(aim)) {
            return true;
        }
        String arg0 = inv.arg(0);
        return arg0 != null && arg0.toLowerCase(Locale.ROOT).endsWith("/body/torso");
    }

    private static boolean containsStep(Ast.Stage stage) {
        List<Ast.Invocation> pipeline = stage.pipeline();
        for (int i = 0; i < pipeline.size(); i++) {
            if ("step".equals(pipeline.get(i).verb())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPassRoll(Ast.Stage stage) {
        List<Ast.Invocation> pipeline = stage.pipeline();
        for (int i = 0; i < pipeline.size(); i++) {
            Ast.Invocation inv = pipeline.get(i);
            if ("pass".equals(inv.verb()) && inv.hasFlag("roll")) {
                return true;
            }
        }
        return false;
    }

    public boolean paidStage(Ast.Stage stage) {
        if (stageCost(stage) > 0) {
            return true;
        }
        List<Ast.Invocation> pipeline = stage.pipeline();
        for (int i = 0; i < pipeline.size(); i++) {
            Ast.Invocation inv = pipeline.get(i);
            if ("channel".equals(inv.verb()) && inv.hasFlag("blood")) {
                continue;
            }
            if (!catalog.get(inv.verb()).free()) {
                return true;
            }
        }
        return false;
    }

    public Plan quote(Ast.Line line, RoundState round) {
        boolean free = true;
        int allocation = 0;
        int surgeBonus = 0;
        Actor thrall = world.thrall();
        boolean canSurge = thrall != null && !thrall.bloodSurgedThisRound && thrall.hp() > Balance.HP_COST_CHANNEL_BLOOD;
        boolean prone = thrall != null && thrall.has(Tag.PRONE);
        List<Ast.Stage> stages = line.stages();
        for (int i = 0; i < stages.size(); i++) {
            Ast.Stage stage = stages.get(i);
            if (paidStage(stage)) {
                free = false;
            }
            allocation += stageCost(stage, prone);
            if (stage.op() == Ast.Op.NONE || stage.op() == Ast.Op.SEQ || stage.op() == Ast.Op.AND) {
                if (prone && containsStep(stage)) {
                    prone = false;
                } else if (containsPassRoll(stage)) {
                    prone = true;
                }
            }
            if (canSurge && surgeBonus == 0
                    && (stage.op() == Ast.Op.NONE || stage.op() == Ast.Op.SEQ)
                    && stage.pipeline().size() == 1) {
                Ast.Invocation inv = stage.pipeline().get(0);
                if ("channel".equals(inv.verb()) && inv.hasFlag("blood")) {
                    surgeBonus = Balance.BLOOD_SURGE_AP;
                }
            }
        }
        int tax = (free || surgeBonus > 0) ? 0 : round.rules().taxForNextLine(round.linesUsed());
        return new Plan(free, allocation, tax, surgeBonus);
    }

    public Check validateStructure(Ast.Line line) {
        List<Ast.Stage> stages = line.stages();
        for (int s = 0; s < stages.size(); s++) {
            Ast.Stage stage = stages.get(s);
            List<Ast.Invocation> pipeline = stage.pipeline();
            Set<MaterialKind> inputKinds = Set.of();

            for (int p = 0; p < pipeline.size(); p++) {
                Ast.Invocation inv = pipeline.get(p);
                Ast.Invocation prev = p > 0 ? pipeline.get(p - 1) : null;
                VerbDefinition def = catalog.get(inv.verb());
                String verb = inv.verb();

                boolean terminal = def.terminal() && !("pass".equals(verb) && inv.hasFlag("roll"));
                if (terminal && (s != stages.size() - 1 || p != pipeline.size() - 1)) {
                    return Check.invalid("\"" + verb + "\" must be the final command",
                            "terminal verbs cannot be chained further");
                }
                if ("pass".equals(verb) && pipeline.size() > 1) {
                    return Check.invalid("\"pass\" cannot be used in a pipe");
                }
                if (inv.hasFlag("blood") && pipeline.size() > 1) {
                    return Check.invalid("blood magic (-b) cannot be used in a pipe");
                }

                if ("channel".equals(verb)) {
                    Check channelCheck = validateChannel(inv, prev, p, pipeline.size());
                    if (!channelCheck.valid()) return channelCheck;
                }

                boolean thermalPromotionSiphon = "siphon".equals(verb) && p == 1 && "channel".equals(prev.verb())
                        && !prev.hasFlag("force") && !prev.hasFlag("blood") && !inv.hasFlag("blood");
                if ("siphon".equals(verb) && p > 0 && !thermalPromotionSiphon) {
                    return Check.invalid("\"siphon\" can only receive piped input from thermal \"channel\"");
                }

                if ("blast".equals(verb) && p > 0) {
                    if ("pull".equals(prev.verb()) || "take".equals(prev.verb())) {
                        return Check.invalid("\"blast\" cannot receive piped items from \"" + prev.verb() + "\"");
                    }
                    if ("channel".equals(prev.verb()) && prev.hasFlag("force")) {
                        return Check.invalid("\"channel -f\" cannot be piped into \"blast\"");
                    }
                }
                if (("push".equals(verb) || "pull".equals(verb)) && p > 0 && !"channel".equals(prev.verb())) {
                    return Check.invalid("\"" + verb + "\" can only receive piped force from \"channel\"");
                }
                if ("bolt".equals(verb)) {
                    if (isTorsoAimed(inv)) {
                        return Check.invalid("torso is not an aim option for \"bolt\"; use plain \"bolt\"");
                    }
                    if (isAimed(inv)) {
                        if (inv.hasFlag("blood")) {
                            return Check.invalid("\"bolt -b\" cannot be aimed with -a");
                        }
                        if (p > 0 && "siphon".equals(prev.verb())) {
                            return Check.invalid("elemental piped \"bolt\" cannot be aimed with -a");
                        }
                    }
                }

                if (p < pipeline.size() - 1 && def.output().isEmpty()) {
                    return Check.invalid("\"" + verb + "\" produces nothing to pipe",
                            "use a material-producing verb before |");
                }
                if (p > 0 && !thermalPromotionSiphon && !def.input().containsAll(inputKinds)) {
                    return Check.invalid("\"" + verb + "\" cannot receive all producer material kinds",
                            "producer: " + inputKinds + "; accepted: " + def.input());
                }

                Check check = def.action().validateStructural(new ValidationContext(world, inv, p > 0));
                if (!check.valid()) return check;

                inputKinds = def.output();
            }
        }
        return Check.ok();
    }

    private static Check validateChannel(Ast.Invocation inv, Ast.Invocation prev, int p, int size) {
        if (inv.hasFlag("blood") && inv.hasFlag("force")) {
            return Check.invalid("\"channel\" cannot combine -b and -f");
        }
        if (inv.hasFlag("blood")) {
            return size == 1 ? Check.ok() : Check.invalid("\"channel -b\" must be standalone");
        }
        if (inv.hasFlag("force")) {
            return p == 0 ? Check.ok() : Check.invalid("\"channel -f\" cannot receive piped input");
        }
        if (size == 1) {
            return Check.invalid("thermal \"channel\" must be used in a pipe");
        }
        if (p > 0 && p < size - 1) {
            return Check.invalid("thermal \"channel\" cannot sit in the middle of a pipe");
        }
        if (p == size - 1 && size > 2) {
            return Check.invalid("thermal promotion \"channel | siphon\" already stores Inferno without trailing \"| channel\"");
        }
        if (p == size - 1 && !"siphon".equals(prev.verb())) {
            return Check.invalid("thermal \"channel\" can only store payloads piped from \"siphon\"");
        }
        return Check.ok();
    }
}