package com.archon.verb;

import java.util.*;

/**
 * Central repository and formatter for game command manuals and reference guides.
 */
public final class CommandManual {

    private static final Map<String, VerbDoc> VERBS = new LinkedHashMap<>();
    private static final Map<String, String> TOPICS = new LinkedHashMap<>();

    private static void reg(VerbDoc doc) {
        VERBS.put(doc.name(), doc);
    }

    private static void topic(String name, String content) {
        TOPICS.put(name.toLowerCase(), content);
    }

    static {
        // --- Combat ---
        reg(new VerbDoc(
                "strike",
                "Melee attack or disarm attempt",
                "COMBAT",
                "strike [target] [-a part] [-p light|normal|heavy] [-f]",
                "1 AP (light), 2 AP (normal), 3 AP (heavy)",
                "Strikes an adjacent enemy with the wielded weapon or bare limbs.\n"
                        + "If target is omitted, automatically strikes the sole adjacent hostile.\n"
                        + "Targeting an item held in an enemy's hand (e.g. 'strike o1/hand/right') executes\n"
                        + "a disarm attempt, knocking the weapon to the ground.",
                List.of(
                        new VerbDoc.FlagDoc("-a, --aim <part>", "Aim for a body part: head, torso, arm.l, arm.r, legs."),
                        new VerbDoc.FlagDoc("-p, --power <level>", "Strike power: light (1 AP), normal (2 AP), heavy (3 AP)."),
                        new VerbDoc.FlagDoc("-f, --force", "Brute-force attack attempting to punch through armor."),
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                false,
                false,
                List.of(
                        "strike o1",
                        "strike o1 -a head -p heavy",
                        "strike o1/hand/right",
                        "strike o1 || guard"
                )
        ));

        reg(new VerbDoc(
                "guard",
                "Raise defense (+30% armor until next round)",
                "COMBAT",
                "guard",
                "1 AP",
                "Raises guard, increasing the thrall's armor rating by +30% until the start\n"
                        + "of the next round. Excellent fallback stage in conditional chains.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                false,
                false,
                List.of(
                        "guard",
                        "strike o1 || guard"
                )
        ));

        reg(new VerbDoc(
                "nock",
                "Load arrow into ranged weapon",
                "COMBAT",
                "nock",
                "1 AP",
                "Loads an arrow into the ranged weapon. Nocked status persists across\n"
                        + "rounds until fired or dropped.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                false,
                false,
                List.of(
                        "nock",
                        "nock ; shoot g1"
                )
        ));

        reg(new VerbDoc(
                "shoot",
                "Ranged attack against visible target in line-of-sight",
                "COMBAT",
                "shoot <target> [-a part]",
                "2 AP (Requires nocked arrow)",
                "Fires a nocked arrow at a target creature. Requires unobstructed line of sight\n"
                        + "and an already nocked arrow. Consumes the nocked arrow upon firing.",
                List.of(
                        new VerbDoc.FlagDoc("-a, --aim <part>", "Aim for a body part: head, torso, arm.l, arm.r, legs."),
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                false,
                false,
                List.of(
                        "shoot g1",
                        "shoot o1 -a head",
                        "nock ; shoot g1"
                )
        ));

        // --- Movement ---
        reg(new VerbDoc(
                "step",
                "Advance 1 tile in a cardinal or diagonal direction",
                "MOVEMENT",
                "step <dir> [-c]",
                "1 AP (2 AP with -c / --careful)",
                "Moves the thrall 1 tile in the specified direction: n, s, e, w, ne, nw, se, sw.\n"
                        + "Fails statically if targeting a wall or occupied tile.\n"
                        + "May trigger enemy movement reactions (e.g. archers readied with ON_MOVEMENT_IN_LOS).",
                List.of(
                        new VerbDoc.FlagDoc("-c, --careful", "Move carefully at increased AP cost (2 AP)."),
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                false,
                false,
                List.of(
                        "step e",
                        "step nw -c",
                        "step w ; strike o1"
                )
        ));

        // --- Items & Pipeline ---
        reg(new VerbDoc(
                "take",
                "Pick up item from ground, pack, or target",
                "ITEMS & PIPELINE",
                "take <address>",
                "1 AP (Produces material)",
                "Takes an item into inventory. Can take from ground (@self, @x,y), from pack\n"
                        + "(/pack/item), or attempt to snatch a weapon held by an adjacent enemy (35% chance).\n"
                        + "Produces the item as piped material for downstream pipeline verbs.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                false,
                true,
                List.of(
                        "take blade",
                        "take @self",
                        "take /pack/flask_oil | throw o1",
                        "take o1/hand/right"
                )
        ));

        reg(new VerbDoc(
                "drop",
                "Drop item onto current tile",
                "ITEMS & PIPELINE",
                "drop [item]",
                "1 AP (Accepts material)",
                "Drops the specified item (or material piped in from previous stage) from the thrall's\n"
                        + "hands or pack onto the current tile.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                true,
                false,
                List.of(
                        "drop blade",
                        "take /pack/stone | drop"
                )
        ));

        reg(new VerbDoc(
                "wield",
                "Equip item into hand slot",
                "ITEMS & PIPELINE",
                "wield [item]",
                "1 AP (Accepts material)",
                "Equips an item from the thrall's pack (or material piped in) into an empty hand slot\n"
                        + "(right hand preferred, left hand secondary). Fails if both hands are occupied.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                true,
                false,
                List.of(
                        "wield sword",
                        "take /pack/torch | wield"
                )
        ));

        reg(new VerbDoc(
                "throw",
                "Hurl an item or flask at a target or tile",
                "ITEMS & PIPELINE",
                "throw [item] <target>",
                "2 AP (Accepts material)",
                "Hurls an item or flask at a target entity or tile. If piped material is received,\n"
                        + "only the destination argument is required. Shatters flasks and spills their contents.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                true,
                false,
                List.of(
                        "throw flask_oil o1",
                        "take /pack/flask_oil | throw @3,5"
                )
        ));

        // --- Environment & Elemental ---
        reg(new VerbDoc(
                "siphon",
                "Extract liquid from flask, creature, or ground puddle",
                "ENVIRONMENT & ELEMENTAL",
                "siphon <source>",
                "1 AP (Produces material)",
                "Extracts liquid (OIL or WATER) from an item in pack, an entity's held container,\n"
                        + "or a ground tile. Produces the substance as material for downstream pipeline verbs.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                false,
                true,
                List.of(
                        "siphon b1/contents",
                        "siphon @self",
                        "siphon b1/contents | pour @o1/floor"
                )
        ));

        reg(new VerbDoc(
                "pour",
                "Pour liquid onto a tile or creature",
                "ENVIRONMENT & ELEMENTAL",
                "pour [liquid] <tile>",
                "1 AP (Accepts and produces material)",
                "Spills liquid substance onto a target tile or entity. If liquid is supplied via pipe,\n"
                        + "only destination tile is required. Spreads liquid and flammable/conductive tags across the tile.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                true,
                true,
                List.of(
                        "pour oil @3,4",
                        "siphon b1/contents | pour @o1/floor"
                )
        ));

        reg(new VerbDoc(
                "ignite",
                "Ignite flammable target or tile",
                "ENVIRONMENT & ELEMENTAL",
                "ignite <target>",
                "1 AP (Requires lit source in hand)",
                "Ignites a flammable creature or tile (such as an oil puddle). Requires holding a lit\n"
                        + "item (e.g. lit torch) in hand. Fire deals periodic burn damage and spreads across\n"
                        + "adjacent oil puddles.",
                List.of(
                        new VerbDoc.FlagDoc("-n, --dry-run", "Validate syntax and cost without executing (0 AP).")
                ),
                true,
                false,
                List.of(
                        "ignite o1",
                        "ignite @o1/floor",
                        "siphon b1/contents | pour @o1/floor | ignite @o1/floor"
                )
        ));

        // --- Free Utilities ---
        reg(new VerbDoc(
                "help",
                "Command manual and reference guides",
                "FREE UTILITIES",
                "help [verb | topic]",
                "0 AP (Free action, never counts as a line)",
                "With no arguments, displays the command manual overview and available topics.\n"
                        + "Given a verb name, displays the full manual page for that verb.\n"
                        + "Given a topic name (operators, address, economy, flags), displays the corresponding reference guide.",
                List.of(),
                false,
                false,
                List.of(
                        "help",
                        "help strike",
                        "help operators",
                        "help economy"
                )
        ));

        reg(new VerbDoc(
                "scan",
                "List perceived entities and vital statistics",
                "FREE UTILITIES",
                "scan",
                "0 AP (Free action, never counts as a line)",
                "Perceives all living entities currently in the dungeon. Displays entity ID,\n"
                        + "name, position, HP, burning status, readied reactions, and distance relative to the thrall.",
                List.of(),
                false,
                false,
                List.of("scan")
        ));

        reg(new VerbDoc(
                "inspect",
                "Detailed inspection of entity, item, or tile",
                "FREE UTILITIES",
                "inspect <address>",
                "0 AP (Free action, never counts as a line)",
                "Deeply inspects an addressable target:\n"
                        + "- Entity: shows HP, armor, held weapon, readied reactions, and hit probabilities / damage multipliers for each body part (head, torso, arm.l, arm.r, legs).\n"
                        + "- Item: reveals item tags and contained substances.\n"
                        + "- Tile: reveals wall/floor status, tile tags (e.g. OIL, BURNING), and ground items.",
                List.of(),
                false,
                false,
                List.of(
                        "inspect o1",
                        "inspect o1/hand/right",
                        "inspect @3,4",
                        "inspect /pack/torch"
                )
        ));

        reg(new VerbDoc(
                "status",
                "Display thrall vitals, held equipment, and pack",
                "FREE UTILITIES",
                "status",
                "0 AP (Free action, never counts as a line)",
                "Reports the thrall's current HP, active tags, items held in right and left hands,\n"
                        + "pack inventory capacity, and whether a missile is nocked.",
                List.of(),
                false,
                false,
                List.of("status")
        ));

        reg(new VerbDoc(
                "ls",
                "List contents of pack or ground tile",
                "FREE UTILITIES",
                "ls [address]",
                "0 AP (Free action, never counts as a line)",
                "Lists items at the given address. Defaults to '/pack' if no argument is provided.\n"
                        + "Can also list ground items at a tile address (e.g. '@self' or '@3,5').",
                List.of(),
                false,
                false,
                List.of(
                        "ls",
                        "ls /pack",
                        "ls @self"
                )
        ));

        reg(new VerbDoc(
                "map",
                "Redraw dungeon map and HUD",
                "FREE UTILITIES",
                "map",
                "0 AP (Free action, never counts as a line)",
                "Triggers a full redraw of the dungeon map and HUD display.",
                List.of(),
                false,
                false,
                List.of("map")
        ));

        reg(new VerbDoc(
                "pass",
                "Forfeit remaining AP and end current round",
                "FREE UTILITIES",
                "pass",
                "0 AP (Free action, terminal verb)",
                "Forfeits all remaining AP for the round and ends the thrall's turn immediately.\n"
                        + "Must appear as the terminal (final) stage of a line.",
                List.of(),
                false,
                false,
                List.of(
                        "pass",
                        "strike o1 ; pass"
                )
        ));

        // --- Topic Guides ---
        topic("operators", """
                TOPIC: COMMAND OPERATORS
                Operators allow combining individual actions into pipelines and tactical chains.
                Chaining stages into a single line avoids the +1 AP Line Tax!

                OPERATORS:
                  ;    Sequential execution: executes stage 1, then stage 2 regardless of outcome.
                       Example: 'step e ; strike o1'

                  &&   Conditional AND: executes the next stage ONLY if the previous SUCCEEDED.
                       If the first stage returns PARTIAL, MISS, or BLOCKED, subsequent stages are skipped.
                       Skipped stages refund their allocated AP!
                       Example: 'strike d1 -p heavy && step n' (retreat only if dummy destroyed)

                  ||   Conditional OR (Fallback): executes the next stage ONLY on MISS or failure.
                       If the first stage succeeds, the fallback stage is skipped for 0 AP cost.
                       Example: 'strike o1 || guard' (defend if the swing fails)

                  |    Material Pipeline: feeds the item or substance from the left verb into the right.
                       Pipelined verbs only charge the AP of the final stage!
                       Example: 'siphon b1/contents | pour @o1/floor | ignite @o1/floor'
                """);

        topic("address", """
                TOPIC: ADDRESSING SYNTAX
                Everything addressable in the dungeon is referenced using structured path syntax.

                TILES (prefix '@'):
                  @x,y            Absolute coordinate tile. Example: '@3,4'
                  @self           Current tile where thrall is standing.
                  @<entityId>     Tile occupied by an entity. Example: '@o1', '@g1'
                  @x,y/floor      Explicit floor layer of tile.
                  @x,y/ceiling    Ceiling layer of tile.
                  @<dir><n>       Relative tile offset from thrall. Example: '@e2', '@n1', '@sw1'

                INVENTORY (prefix '/'):
                  /pack/<item>    Item in thrall pack. Example: '/pack/torch', '/pack/flask_oil'

                ENTITIES & BODY PARTS:
                  <entityId>      Target creature. Example: 'o1', 'g1', 'd1'
                  <entity>/<part> Target hit location: head, torso, arm.l, arm.r, legs.
                                  Example: 'o1/head' (high crit), 'o1/legs' (evasion penalty)
                  <entity>/hand/right
                  <entity>/hand/left
                                  Target held item for a disarm attack or snatch attempt.
                                  Example: 'strike o1/hand/right', 'take o1/hand/right'
                """);

        topic("economy", """
                TOPIC: ACTION ECONOMY
                Combat in Archon is managed by the Action Economy Model (Spec v3.0).

                ACTION POINTS (AP):
                  The thrall receives 5 AP at the start of each round.
                  Unspent AP is destroyed when the round ends—it cannot be banked.

                LINE TAX:
                  - 1st command line submitted in a round: +0 AP tax (tax-free).
                  - 2nd command line submitted:            +1 AP line tax.
                  - 3rd command line submitted:            +2 AP line tax.
                  (Tax is charged up front upon line submission and is never refunded).

                WHY CHAIN ACTIONS?
                  By combining multiple stages on one line using operators (;, &&, ||, |),
                  you execute entire combos while paying ZERO line tax!

                BREAK PENALTIES:
                  If a chain is interrupted by an enemy reaction or blocked mid-flight:
                  - Execution stops immediately.
                  - All remaining allocated AP is forfeited.
                  - An additional +1 AP break penalty is charged (capped at remaining AP).

                FREE ACTIONS (0 AP):
                  Free verbs (scan, inspect, status, ls, map, help, pass) never cost AP,
                  never consume a line, and never incur line tax.

                TERMINAL ACTIONS:
                  The 'pass' verb ends the round immediately, forfeiting unspent AP without penalty.
                  It may only appear as the final stage of a line.
                """);

        topic("flags", """
                TOPIC: COMMAND FLAGS
                Flags modify command behavior, AP costs, targeting, or execution modes.

                GLOBAL FLAGS:
                  -n, --dry-run          Chain audit. Validates syntax, computes AP allocation,
                                         checks affordability, and reports exposed enemy reactions
                                         without spending AP or committing a line.

                COMBAT & TARGETING FLAGS:
                  -a, --aim <part>       Aim strike or shoot at a specific body part.
                                         Valid parts: head, torso, arm.l, arm.r, legs.
                  -p, --power <level>    Strike power level:
                                         light  (1 AP, lower damage)
                                         normal (2 AP, standard damage)
                                         heavy  (3 AP, maximum damage)
                  -f, --force            Brute-force melee attack attempting to punch through armor.

                MOVEMENT FLAGS:
                  -c, --careful          Careful movement. 'step' costs 2 AP instead of 1 AP.
                """);
    }

    private CommandManual() {}

    public static String renderIndex() {
        StringBuilder sb = new StringBuilder();
        sb.append("ARCHON COMMAND MANUAL\n");
        sb.append("Type 'help <verb>' for detailed manual page, or 'help <topic>' for reference guides.\n\n");

        List<String> categories = List.of(
                "COMBAT",
                "MOVEMENT",
                "ITEMS & PIPELINE",
                "ENVIRONMENT & ELEMENTAL",
                "FREE UTILITIES"
        );

        for (String cat : categories) {
            sb.append(cat);
            if (cat.equals("FREE UTILITIES")) {
                sb.append(" (0 AP, unlimited use, never count as a line)");
            }
            sb.append(":\n");

            for (VerbDoc doc : VERBS.values()) {
                if (doc.category().equals(cat)) {
                    sb.append(String.format("  %-20s %-8s %s%n",
                            shortSynopsis(doc),
                            shortCost(doc),
                            doc.summary()));
                }
            }
            sb.append('\n');
        }

        sb.append("REFERENCE TOPICS:\n");
        sb.append("  help operators   |   help address   |   help economy   |   help flags");
        return sb.toString();
    }

    private static String shortSynopsis(VerbDoc doc) {
        String syn = doc.synopsis();
        int firstBracket = syn.indexOf('[');
        int firstDash = syn.indexOf(" -");
        int cutoff = -1;
        if (firstBracket >= 0 && firstDash >= 0) cutoff = Math.min(firstBracket, firstDash);
        else if (firstBracket >= 0) cutoff = firstBracket;
        else if (firstDash >= 0) cutoff = firstDash;

        return (cutoff > 0 ? syn.substring(0, cutoff).trim() : syn);
    }

    private static String shortCost(VerbDoc doc) {
        String c = doc.apCostDesc();
        if (c.startsWith("0 AP")) return "0 AP";
        if (c.startsWith("1 AP (2 AP")) return "1-2 AP";
        if (c.startsWith("1 AP (light)")) return "1-3 AP";
        if (c.startsWith("1 AP")) return "1 AP";
        if (c.startsWith("2 AP")) return "2 AP";
        if (c.startsWith("3 AP")) return "3 AP";
        return c.split(" ")[0] + " AP";
    }

    public static String renderVerb(VerbDoc doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("NAME:\n  ").append(doc.name()).append(" — ").append(doc.summary()).append("\n\n");
        sb.append("SYNOPSIS:\n  ").append(doc.synopsis()).append("\n\n");
        sb.append("AP COST:\n  ").append(doc.apCostDesc()).append("\n\n");
        sb.append("CATEGORY:\n  ").append(doc.category()).append("\n\n");
        sb.append("DESCRIPTION:\n");
        for (String line : doc.description().split("\n")) {
            sb.append("  ").append(line).append('\n');
        }
        sb.append("\nPIPELINE:\n");
        sb.append("  Accepts material : ").append(doc.acceptsMaterial() ? "yes" : "no").append('\n');
        sb.append("  Produces material: ").append(doc.producesMaterial() ? "yes" : "no").append('\n');

        if (!doc.flags().isEmpty()) {
            sb.append("\nOPTIONS & FLAGS:\n");
            for (VerbDoc.FlagDoc f : doc.flags()) {
                sb.append(String.format("  %-22s %s%n", f.flag(), f.description()));
            }
        }

        if (!doc.examples().isEmpty()) {
            sb.append("\nEXAMPLES:\n");
            for (String ex : doc.examples()) {
                sb.append("  ").append(ex).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    public static String renderTopic(String topic) {
        return TOPICS.get(topic.toLowerCase().trim());
    }

    public static String lookup(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.toLowerCase().trim();

        VerbDoc doc = VERBS.get(q);
        if (doc != null) return renderVerb(doc);

        String topicDoc = TOPICS.get(q);
        if (topicDoc != null) return topicDoc.stripTrailing();

        return null;
    }

    public static VerbDoc getVerbDoc(String name) {
        return VERBS.get(name.toLowerCase().trim());
    }

    public static Collection<VerbDoc> allVerbs() {
        return Collections.unmodifiableCollection(VERBS.values());
    }

    public static Set<String> verbNames() {
        return Collections.unmodifiableSet(VERBS.keySet());
    }

    public static Set<String> topicNames() {
        return Collections.unmodifiableSet(TOPICS.keySet());
    }
}
