package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.*;

import java.util.*;

/** Registry plus all verb implementations. */
public final class Verbs {

    private static final Map<String, Verb> REGISTRY = new LinkedHashMap<>();

    private static void reg(Verb v) { REGISTRY.put(v.name(), v); }

    public static Verb get(String name) { return REGISTRY.get(name); }
    public static Collection<Verb> all() { return REGISTRY.values(); }
    public static Set<String> names() { return REGISTRY.keySet(); }

    // ---------- helpers ----------

    private static Resolved resolve(VerbContext c, String raw) {
        return Resolved.resolve(Address.parse(raw), c.world);
    }

    private static Entity targetEntity(VerbContext c, String raw) {
        Resolved r = resolve(c, raw);
        return (r instanceof Resolved.OnEntity oe) ? oe.entity() : null;
    }

    private static BodyPart aimPart(Ast.Invocation inv, String targetArg) {
        String aim = inv.flag("aim");
        if (aim != null) {
            BodyPart p = BodyPart.parse(aim);
            if (p != null) return p;
        }
        if (targetArg != null && targetArg.contains("/")) {
            BodyPart p = BodyPart.parse(targetArg.substring(targetArg.indexOf('/') + 1));
            if (p != null) return p;
        }
        return BodyPart.TORSO;
    }

    private static int powerAp(Ast.Invocation inv, int base) {
        String p = inv.flag("power");
        if (p == null) return base;
        return switch (p.toLowerCase()) {
            case "light" -> 1;
            case "heavy" -> 3;
            default -> base;
        };
    }

    private static String soleAdjacentHostile(VerbContext c) {
        List<Entity> adj = c.world.hostilesAdjacentTo(c.thrall.pos);
        return adj.size() == 1 ? adj.get(0).id : null;
    }

    // ==================== FREE / QUERY ====================

    static abstract class FreeVerb implements Verb {
        public boolean free() { return true; }
        public int apCost(Ast.Invocation inv) { return 0; }
        public Check validateStructural(VerbContext c) { return Check.ok(); }
    }

    static final class Scan extends FreeVerb {
        public String name() { return "scan"; }
        public String help() { return "scan — list perceived entities. 0 AP."; }
        public ExitCode execute(VerbContext c) {
            StringBuilder sb = new StringBuilder("VISIBLE:\n");
            for (Entity e : c.world.entities.values()) {
                if (!e.alive()) continue;
                sb.append(String.format("  %-5s %-16s %-7s HP %2d/%-2d %s%s%s%n",
                        e.id, e.name, e.pos.toString(), e.hp, e.maxHp,
                        e.tags.contains(Tag.BURNING) ? "[BURNING] " : "",
                        e.readied != null && !e.readiedSpent ? "[READIED: " + e.readied.description() + "] " : "",
                        e.pos.chebyshev(c.thrall.pos) <= 1 ? "adjacent" : "range " + e.pos.chebyshev(c.thrall.pos)));
            }
            c.say(sb.toString().stripTrailing());
            return ExitCode.SUCCESS;
        }
    }

    static final class Inspect extends FreeVerb {
        public String name() { return "inspect"; }
        public String help() { return "inspect <address> — tags, HP, hit locations. 0 AP."; }
        public ExitCode execute(VerbContext c) {
            String a = c.inv.arg(0);
            if (a == null) { c.say("inspect what?"); return ExitCode.INVALID; }
            Resolved r = resolve(c, a);
            if (r == null) { c.say("cannot perceive " + a); return ExitCode.INVALID; }
            switch (r) {
                case Resolved.OnEntity oe -> {
                    Entity e = oe.entity();
                    StringBuilder sb = new StringBuilder(
                            e.name + " \"" + e.id + "\" — HP " + e.hp + "/" + e.maxHp + ", Armor " + e.armor);
                    sb.append("\n  Tags: ").append(e.tags);
                    if (e.held != null) sb.append("\n  Holding: ").append(e.held.name);
                    if (e.readied != null && !e.readiedSpent)
                        sb.append("\n  READIED: ").append(e.readied.description())
                                .append(" (").append(e.readied.damage()).append(" dmg)");
                    for (BodyPart p : BodyPart.values())
                        sb.append(String.format("%n  %-6s %3d%%  x%.1f", p.path,
                                Math.max(5, 70 + p.hitMod - e.evasion), p.damageMult));
                    c.say(sb.toString());
                }
                case Resolved.OnItem oi -> c.say(oi.item() == null ? "nothing there"
                        : oi.item().name + " — tags " + oi.item().tags
                        + (oi.item().substance != null ? ", contains " + oi.item().substance : ""));
                case Resolved.OnTile ot -> {
                    World.Tile t = c.world.tile(ot.pos());
                    c.say(ot.pos() + " " + (t.wall ? "WALL" : "floor") + " — tags " + t.tags
                            + (t.ground.isEmpty() ? "" : ", ground " + t.ground));
                }
            }
            return ExitCode.SUCCESS;
        }
    }

    static final class Status extends FreeVerb {
        public String name() { return "status"; }
        public String help() { return "status — thrall state. 0 AP."; }
        public ExitCode execute(VerbContext c) {
            Thrall t = c.thrall;
            c.say("HP " + t.hp + "/" + t.maxHp + "  tags " + t.tags
                    + "\n  hand/right: " + slot(t, "hand/right")
                    + "\n  hand/left : " + slot(t, "hand/left")
                    + "\n  pack (" + t.pack.size() + "/" + Thrall.PACK_MAX + "): " + t.pack
                    + "\n  nocked: " + t.nocked);
            return ExitCode.SUCCESS;
        }
        private String slot(Thrall t, String s) { Item i = t.slots.get(s); return i == null ? "empty" : i.name; }
    }

    static final class Ls extends FreeVerb {
        public String name() { return "ls"; }
        public String help() { return "ls <address> — list contents. 0 AP."; }
        public ExitCode execute(VerbContext c) {
            String a = c.inv.arg(0) == null ? "/pack" : c.inv.arg(0);
            Resolved r = resolve(c, a);
            if (r instanceof Resolved.OnItem oi && "pack".equals(oi.container())) {
                c.say(c.thrall.pack.isEmpty() ? "(empty)" :
                        String.join("\n", c.thrall.pack.stream()
                                .map(i -> "  " + i.id + "  " + i.tags).toList()));
                return ExitCode.SUCCESS;
            }
            if (r instanceof Resolved.OnTile ot) {
                c.say(c.world.tile(ot.pos()).ground.toString());
                return ExitCode.SUCCESS;
            }
            c.say("nothing to list at " + a);
            return ExitCode.INVALID;
        }
    }

    static final class Help extends FreeVerb {
        public String name() { return "help"; }
        public String help() { return "help [verb] — syntax. 0 AP."; }
        public ExitCode execute(VerbContext c) {
            String v = c.inv.arg(0);
            if (v == null) {
                c.say("verbs: " + String.join(" ", names())
                        + "\noperators: ;  &&  ||  |     flags: -a -p -f -c -n"
                        + "\nfree: scan inspect status ls help map pass  (never cost AP, never a line)");
            } else {
                Verb verb = get(v);
                c.say(verb == null ? "no such verb: " + v : verb.help());
            }
            return ExitCode.SUCCESS;
        }
    }

    static final class MapCmd extends FreeVerb {
        public String name() { return "map"; }
        public String help() { return "map — redraw grid. 0 AP."; }
        public ExitCode execute(VerbContext c) { c.bus.redraw(); return ExitCode.SUCCESS; }
    }

    // ==================== TERMINAL ====================

    static final class Pass implements Verb {
        public String name() { return "pass"; }
        public String help() { return "pass — forfeit remaining AP, end round. Free, never counts as a line."; }
        public boolean free() { return true; }        // <-- add
        public boolean terminal() { return true; }
        public int apCost(Ast.Invocation inv) { return 0; }
        public Check validateStructural(VerbContext c) { return Check.ok(); }
        public ExitCode execute(VerbContext c) { c.say("Thrall falls still."); return ExitCode.SUCCESS; }
    }

    // ==================== MOVEMENT ====================

    static final class Step implements Verb {
        public String name() { return "step"; }
        public String help() { return "step <dir> [-c] — move 1 tile. 1 AP (2 with -c)."; }
        public int apCost(Ast.Invocation inv) { return inv.hasFlag("careful") ? 2 : 1; }

        public Check validateStructural(VerbContext c) {
            String d = c.inv.arg(0);
            if (d == null) return Check.invalid("step needs a direction", "n s e w ne nw se sw");
            if (Vec2.dir(d) == null) return Check.invalid("unknown direction \"" + d + "\"", "n s e w ne nw se sw");
            return Check.ok();
        }

        public Check validateState(VerbContext c) {
            Vec2 to = c.thrall.pos.plus(Vec2.dir(c.inv.arg(0)));
            if (!c.world.passable(to))
                return Check.blocked("wall or occupant at " + to, "open: " + openDirs(c));
            return Check.ok();
        }

        public ExitCode execute(VerbContext c) {
            Vec2 to = c.thrall.pos.plus(Vec2.dir(c.inv.arg(0)));
            if (!c.world.passable(to)) { c.say("blocked at " + to); return ExitCode.BLOCKED; }
            c.thrall.pos = to;
            c.world.thrallMovedThisLine = true;
            c.say("Thrall advances to " + to + ".");
            return ExitCode.SUCCESS;
        }

        private String openDirs(VerbContext c) {
            List<String> open = new ArrayList<>();
            for (String d : List.of("n", "s", "e", "w", "ne", "nw", "se", "sw"))
                if (c.world.passable(c.thrall.pos.plus(Vec2.dir(d)))) open.add(d);
            return String.join(", ", open);
        }
    }

    // ==================== MELEE ====================

    static final class Strike implements Verb {
        public String name() { return "strike"; }
        public String help() { return "strike [target] [-a part] [-p light|normal|heavy] [-f] — 1/2/3 AP."; }
        public int apCost(Ast.Invocation inv) { return powerAp(inv, 2); }

        public Check validateStructural(VerbContext c) {
            String t = c.inv.arg(0);
            if (t == null) {
                List<Entity> adj = c.world.hostilesAdjacentTo(c.thrall.pos);
                if (adj.isEmpty()) return Check.blocked("nothing adjacent to strike", null);
                if (adj.size() > 1) return Check.invalid("ambiguous target: "
                        + adj.stream().map(e -> e.id).toList(), "name one explicitly");
                return Check.ok();
            }
            if (aimPartInvalid(c, t)) return Check.invalid(
                    "no such hit location on " + t,
                    "valid: head, torso, arm.l, arm.r, legs");
            Resolved r = resolve(c, t);
            if (r == null) return Check.invalid("unknown target \"" + t + "\"", "try: scan");
            return Check.ok();
        }

        private boolean aimPartInvalid(VerbContext c, String target) {
            String aim = c.inv.flag("aim");
            if (aim != null && BodyPart.parse(aim) == null) return true;
            if (target.contains("/")) {
                String p = target.substring(target.indexOf('/') + 1);
                return BodyPart.parse(p) == null && !p.startsWith("hand/");
            }
            return false;
        }

        public Check validateState(VerbContext c) {
            String t = c.inv.arg(0) == null ? soleAdjacentHostile(c) : c.inv.arg(0);
            Entity e = targetEntity(c, t);
            if (e == null) {
                Resolved r = resolve(c, t);
                if (r instanceof Resolved.OnItem) return Check.ok(); // striking a held item
                return Check.blocked("target not present", null);
            }
            int reach = c.thrall.mainHand() != null && c.thrall.mainHand().has(Tag.HEAVY) ? 1 : 1;
            if (e.pos.chebyshev(c.thrall.pos) > reach)
                return Check.blocked(e.id + " out of reach", "step closer first");
            return Check.ok();
        }

        public ExitCode execute(VerbContext c) {
            String targetArg = c.inv.arg(0) == null ? soleAdjacentHostile(c) : c.inv.arg(0);
            if (targetArg == null) { c.say("nothing to strike"); return ExitCode.BLOCKED; }

            Resolved r = resolve(c, targetArg);
            if (r == null) { c.say(targetArg + " is no longer there"); return ExitCode.BLOCKED; }

            // Striking a held item = disarm attempt.
            if (r instanceof Resolved.OnItem oi && oi.container() != null && oi.container().contains("hand")) {
                String ownerId = oi.container().split("/")[0];
                Entity owner = c.world.get(ownerId);
                if (owner == null || owner.held == null) { c.say("nothing to disarm"); return ExitCode.BLOCKED; }
                if (c.world.dice.chance(45)) {
                    c.world.tile(owner.pos).ground.add(owner.held);
                    c.say("The " + owner.held.name + " is knocked from " + owner.name + "'s grip.");
                    owner.held = null;
                    return ExitCode.SUCCESS;
                }
                c.say("The blow glances off " + owner.name + "'s weapon.");
                return ExitCode.MISS;
            }

            Entity e = ((Resolved.OnEntity) r).entity();
            BodyPart part = aimPart(c.inv, targetArg);
            String power = c.inv.flag("power") == null ? "normal" : c.inv.flag("power");

            int hit = 70 + part.hitMod - e.evasion
                    + ("light".equals(power) ? 20 : "heavy".equals(power) ? -20 : 0);
            if (e.guarded && !c.inv.hasFlag("force")) hit -= 25;

            if (!c.world.dice.chance(Math.max(5, Math.min(95, hit)))) {
                c.say("Strike at " + e.name + "'s " + part.path + " — MISS.");
                return ExitCode.MISS;
            }

            Item w = c.thrall.mainHand();
            int base = (w == null ? 3 : w.damage) + c.thrall.strength;
            double mult = part.damageMult * ("light".equals(power) ? 0.6 : "heavy".equals(power) ? 1.6 : 1.0);
            int dmg = Math.max(1, (int) Math.round(base * mult) - e.armor);
            e.hp -= dmg;
            if (c.inv.hasFlag("force") && w != null) w.durability--;

            c.say(String.format("%s strikes %s's %s. %d dmg.",
                    w == null ? "Bare limb" : w.name, e.name, part.path, dmg));

            if (!e.alive()) {
                c.say(e.name + " falls.");
                if (e.held != null) { c.world.tile(e.pos).ground.add(e.held); e.held = null; }
                return ExitCode.SUCCESS;
            }
            return ExitCode.PARTIAL;
        }
    }

    static final class Guard implements Verb {
        public String name() { return "guard"; }
        public String help() { return "guard — +30% armour until next round. 1 AP."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public Check validateStructural(VerbContext c) { return Check.ok(); }
        public ExitCode execute(VerbContext c) {
            c.thrall.guarded = true;
            c.say("Thrall raises its guard.");
            return ExitCode.SUCCESS;
        }
    }

    // ==================== RANGED ====================

    static final class Nock implements Verb {
        public String name() { return "nock"; }
        public String help() { return "nock — load a missile. 1 AP. Persists across rounds."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public Check validateStructural(VerbContext c) { return Check.ok(); }
        public Check validateState(VerbContext c) {
            if (c.thrall.nocked) return Check.blocked("already nocked", null);
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            if (c.thrall.nocked) { c.say("already nocked"); return ExitCode.BLOCKED; }
            c.thrall.nocked = true;
            c.say("Arrow nocked.");
            return ExitCode.SUCCESS;
        }
    }

    static final class Shoot implements Verb {
        public String name() { return "shoot"; }
        public String help() { return "shoot <target> [-a part] — 2 AP. Requires nocked."; }
        public int apCost(Ast.Invocation inv) { return 2; }

        public Check validateStructural(VerbContext c) {
            if (c.inv.arg(0) == null) return Check.invalid("shoot needs a target", "try: scan");
            return Check.ok();
        }
        public Check validateState(VerbContext c) {
            if (!c.thrall.nocked) return Check.blocked("nothing nocked", "try: nock | shoot <target>");
            Entity e = targetEntity(c, c.inv.arg(0));
            if (e == null) return Check.blocked("unknown target", null);
            if (!c.world.lineOfSight(c.thrall.pos, e.pos)) return Check.blocked("no line of fire", null);
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            if (!c.thrall.nocked) { c.say("nothing nocked"); return ExitCode.BLOCKED; }
            Entity e = targetEntity(c, c.inv.arg(0));
            if (e == null) { c.say("target gone"); return ExitCode.BLOCKED; }
            c.thrall.nocked = false;

            int dist = e.pos.chebyshev(c.thrall.pos);
            BodyPart part = aimPart(c.inv, c.inv.arg(0));
            int band = dist <= 1 ? -20 : dist <= 4 ? 0 : dist <= 8 ? -10 : -25;
            int hit = 70 + part.hitMod + band - e.evasion;

            if (!c.world.dice.chance(Math.max(5, Math.min(95, hit)))) {
                c.say("Arrow flies wide of " + e.name + ".");
                return ExitCode.MISS;
            }
            int dmg = Math.max(1, c.world.dice.between(5, 10) - e.armor);
            e.hp -= dmg;
            c.say("Arrow strikes " + e.name + "'s " + part.path + ". " + dmg + " dmg.");
            if (!e.alive()) { c.say(e.name + " falls."); return ExitCode.SUCCESS; }
            return ExitCode.PARTIAL;
        }
    }

    // ==================== ITEMS ====================

    static final class Take implements Verb {
        public String name() { return "take"; }
        public String help() { return "take <address> — pick up. 1 AP. Produces material."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public boolean producesMaterial() { return true; }

        public Check validateStructural(VerbContext c) {
            if (c.inv.arg(0) == null) return Check.invalid("take what?", null);
            return Check.ok();
        }
        public Check validateState(VerbContext c) {
            if (c.thrall.packFull()) return Check.blocked("pack full (" + Thrall.PACK_MAX + ")", "drop something");
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            String a = c.inv.arg(0);
            Resolved r = resolve(c, a);
            Item item = null;

            if (r instanceof Resolved.OnItem oi && oi.item() != null) {
                item = oi.item();
                if ("pack".equals(oi.container())) {
                    // already carried: taking it out is a valid pipeline source
                    c.materialOut = new Material.OfItem(item);
                    c.say("Thrall draws " + item.name + ".");
                    return ExitCode.SUCCESS;
                }
                String owner = oi.container().split("/")[0];
                Entity oe = c.world.get(owner);
                if (oe != null && oe.held == item) {
                    if (!c.world.dice.chance(35)) { c.say("Snatch fails."); return ExitCode.MISS; }
                    oe.held = null;
                }
            } else if (r instanceof Resolved.OnTile ot) {
                World.Tile t = c.world.tile(ot.pos());
                if (t.ground.isEmpty()) { c.say("nothing on the ground there"); return ExitCode.BLOCKED; }
                item = t.ground.remove(0);
            }

            if (item == null) { c.say("cannot take " + a); return ExitCode.BLOCKED; }
            if (c.thrall.packFull()) { c.say("pack full"); return ExitCode.BLOCKED; }
            c.thrall.pack.add(item);
            c.materialOut = new Material.OfItem(item);
            c.say("Thrall takes " + item.name + ".");
            return ExitCode.SUCCESS;
        }
    }

    static final class Drop implements Verb {
        public String name() { return "drop"; }
        public String help() { return "drop <item> — put on current tile. 1 AP."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public boolean acceptsMaterial() { return true; }

        public Check validateStructural(VerbContext c) {
            if (c.inv.arg(0) == null && !c.acceptsPipedMaterial()) return Check.invalid("drop what?", null);
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            Item item = c.itemFromMaterialOrArg(0);
            if (item == null) { c.say("not carrying that"); return ExitCode.BLOCKED; }
            c.thrall.pack.remove(item);
            c.thrall.slots.replaceAll((k, v) -> v == item ? null : v);
            c.world.tile(c.thrall.pos).ground.add(item);
            c.say("Thrall drops " + item.name + ".");
            return ExitCode.SUCCESS;
        }
    }

    static final class Wield implements Verb {
        public String name() { return "wield"; }
        public String help() { return "wield <item> — equip to hand. 1 AP. Accepts material."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public boolean acceptsMaterial() { return true; }

        public Check validateStructural(VerbContext c) {
            if (c.inv.arg(0) == null && !c.acceptsPipedMaterial()) return Check.invalid("wield what?", null);
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            Item item = c.itemFromMaterialOrArg(0);
            if (item == null) { c.say("no such item"); return ExitCode.BLOCKED; }
            String slot = c.thrall.slots.get("hand/right") == null ? "hand/right" : "hand/left";
            if (c.thrall.slots.get(slot) != null) { c.say("both hands full"); return ExitCode.BLOCKED; }
            c.thrall.pack.remove(item);
            c.thrall.slots.put(slot, item);
            c.say("Thrall grips " + item.name + " (" + slot + ").");
            return ExitCode.SUCCESS;
        }
    }

    static final class Throw implements Verb {
        public String name() { return "throw"; }
        public String help() { return "throw <item> <target> — 2 AP. Accepts material from a pipe."; }
        public int apCost(Ast.Invocation inv) { return 2; }
        public boolean acceptsMaterial() { return true; }

        public Check validateStructural(VerbContext c) {
            boolean piped = c.acceptsPipedMaterial();
            int need = piped ? 1 : 2;
            if (c.inv.args().size() < need)
                return Check.invalid("throw needs " + (piped ? "a target" : "an item and a target"),
                        "e.g. take /pack/flask_oil | throw o1");
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            boolean piped = c.materialIn instanceof Material.OfItem;
            Item item = piped ? ((Material.OfItem) c.materialIn).item() : c.thrall.findInPack(c.inv.arg(0));
            String targetArg = piped ? c.inv.arg(0) : c.inv.arg(1);
            if (item == null) { c.say("no such item to throw"); return ExitCode.BLOCKED; }

            Resolved r = resolve(c, targetArg);
            if (r == null) { c.say("cannot resolve " + targetArg); return ExitCode.BLOCKED; }
            Vec2 at = switch (r) {
                case Resolved.OnEntity oe -> oe.entity().pos;
                case Resolved.OnTile ot -> ot.pos();
                case Resolved.OnItem ignored -> c.thrall.pos;
            };
            c.thrall.pack.remove(item);
            c.say("Flask arcs toward " + at + " and shatters.");
            if (item.substance != null) spill(c, at, item.substance);
            return ExitCode.SUCCESS;
        }
    }

    static final class Pour implements Verb {
        public String name() { return "pour"; }
        public String help() { return "pour <liquid> <tile> — 1 AP. Accepts material."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public boolean acceptsMaterial() { return true; }
        public boolean producesMaterial() { return true; }

        public Check validateStructural(VerbContext c) {
            boolean piped = c.acceptsPipedMaterial();
            if (!piped && c.inv.args().size() < 2) return Check.invalid("pour needs a liquid and a tile", null);
            if (piped && c.inv.arg(0) == null) return Check.invalid("pour needs a destination tile", null);
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            Tag substance;
            String targetArg;
            if (c.materialIn instanceof Material.OfSubstance os) { substance = os.substance(); targetArg = c.inv.arg(0); }
            else if (c.materialIn instanceof Material.OfItem oi) { substance = oi.item().substance; targetArg = c.inv.arg(0); }
            else {
                Item it = c.thrall.findInPack(c.inv.arg(0));
                if (it == null || it.substance == null) { c.say("nothing pourable"); return ExitCode.BLOCKED; }
                substance = it.substance; targetArg = c.inv.arg(1);
                c.thrall.pack.remove(it);
            }
            if (substance == null) { c.say("nothing pourable"); return ExitCode.BLOCKED; }
            Resolved r = resolve(c, targetArg);
            if (r == null) { c.say("cannot resolve " + targetArg); return ExitCode.BLOCKED; }
            Vec2 at = (r instanceof Resolved.OnEntity oe) ? oe.entity().pos
                    : (r instanceof Resolved.OnTile ot) ? ot.pos() : c.thrall.pos;
            spill(c, at, substance);
            c.materialOut = new Material.OfSubstance(substance);
            return ExitCode.SUCCESS;
        }
    }

    static final class Siphon implements Verb {
        public String name() { return "siphon"; }
        public String help() { return "siphon <source> — draw liquid. 1 AP. Produces material."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public boolean producesMaterial() { return true; }

        public Check validateStructural(VerbContext c) {
            if (c.inv.arg(0) == null) return Check.invalid("siphon from what?", null);
            return Check.ok();
        }
        public Check validateState(VerbContext c) {
            Tag s = sourceSubstance(c, c.inv.arg(0));
            if (s == null) return Check.blocked("nothing to siphon from " + c.inv.arg(0), "try: inspect " + c.inv.arg(0));
            Entity holder = holderOf(c, c.inv.arg(0));
            if (holder != null && holder.pos.chebyshev(c.thrall.pos) > 1)
                return Check.blocked(holder.id + " out of reach", "step closer");
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            Tag s = sourceSubstance(c, c.inv.arg(0));
            if (s == null) { c.say("nothing to siphon"); return ExitCode.BLOCKED; }
            c.materialOut = new Material.OfSubstance(s);
            c.say("Thrall draws " + s.name().toLowerCase() + ".");
            return ExitCode.SUCCESS;
        }

        private static Tag sourceSubstance(VerbContext c, String arg) {
            Resolved r = resolve(c, arg);
            if (r instanceof Resolved.OnItem oi && oi.item() != null) return oi.item().substance;
            if (r instanceof Resolved.OnEntity oe && oe.entity().held != null) return oe.entity().held.substance;
            if (r instanceof Resolved.OnTile ot) {
                World.Tile t = c.world.tile(ot.pos());
                if (t.has(Tag.OIL)) return Tag.OIL;
                if (t.has(Tag.WATER)) return Tag.WATER;
            }
            return null;
        }
        private static Entity holderOf(VerbContext c, String arg) {
            Address a = Address.parse(arg);
            if (a instanceof Address.EntityAddr ea) return c.world.get(ea.id());
            return null;
        }
    }

    static final class Ignite implements Verb {
        public String name() { return "ignite"; }
        public String help() { return "ignite <target> — 1 AP. Requires a lit source in hand."; }
        public int apCost(Ast.Invocation inv) { return 1; }
        public boolean acceptsMaterial() { return true; }

        public Check validateStructural(VerbContext c) {
            if (c.inv.arg(0) == null) return Check.invalid("ignite what?", null);
            return Check.ok();
        }
        public Check validateState(VerbContext c) {
            if (!hasFlame(c)) return Check.blocked("no lit source in hand", "wield a torch first");
            return Check.ok();
        }
        public ExitCode execute(VerbContext c) {
            if (!hasFlame(c)) { c.say("no flame to hand"); return ExitCode.BLOCKED; }
            Resolved r = resolve(c, c.inv.arg(0));
            if (r == null) { c.say("cannot resolve " + c.inv.arg(0)); return ExitCode.BLOCKED; }
            if (r instanceof Resolved.OnEntity oe) {
                Entity e = oe.entity();
                if (!e.has(Tag.FLAMMABLE)) { c.say(e.name + " will not catch."); return ExitCode.MISS; }
                e.tags.add(Tag.BURNING);
                c.say(e.name + " catches fire.");
                return ExitCode.SUCCESS;
            }
            Vec2 at = ((Resolved.OnTile) r).pos();
            World.Tile t = c.world.tile(at);
            if (!t.has(Tag.OIL) && !t.has(Tag.FLAMMABLE)) { c.say("nothing to burn at " + at); return ExitCode.MISS; }
            t.tags.add(Tag.BURNING);
            Entity occupant = c.world.entityAt(at);
            if (occupant != null && occupant.has(Tag.FLAMMABLE)) occupant.tags.add(Tag.BURNING);
            c.say("Fire takes hold at " + at + ".");
            return ExitCode.SUCCESS;
        }
        private static boolean hasFlame(VerbContext c) {
            return c.thrall.slots.values().stream().anyMatch(i -> i != null && i.has(Tag.LIT));
        }
    }

    private static void spill(VerbContext c, Vec2 at, Tag substance) {
        World.Tile t = c.world.tile(at);
        if (t == null) return;
        t.tags.add(Tag.LIQUID);
        t.tags.add(substance);
        if (substance == Tag.OIL) t.tags.add(Tag.FLAMMABLE);
        if (substance == Tag.WATER) t.tags.add(Tag.CONDUCTIVE);
        Entity occ = c.world.entityAt(at);
        if (occ != null && substance == Tag.OIL) occ.tags.add(Tag.FLAMMABLE);
        c.say(substance.name().toLowerCase() + " spreads across " + at + ".");
    }

    static {
        reg(new Scan()); reg(new Inspect()); reg(new Status()); reg(new Ls());
        reg(new Help()); reg(new MapCmd()); reg(new Pass());
        reg(new Step()); reg(new Strike()); reg(new Guard());
        reg(new Nock()); reg(new Shoot());
        reg(new Take()); reg(new Drop()); reg(new Wield());
        reg(new Throw()); reg(new Pour()); reg(new Siphon()); reg(new Ignite());
    }

    private Verbs() {}
}