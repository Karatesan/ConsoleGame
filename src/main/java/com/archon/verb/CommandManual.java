package com.archon.verb;

import java.util.*;

/**
 * Central formatter and topic guide registry.
 * Discovers verb documentation dynamically from the Verbs registry.
 */
public final class CommandManual {

    private static final Map<String, String> TOPICS = new LinkedHashMap<>();

    private static void topic(String name, String content) {
        TOPICS.put(name.toLowerCase(), content);
    }

    static {
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

        List<String> categoryOrder = List.of(
                "COMBAT",
                "MOVEMENT",
                "ITEMS & PIPELINE",
                "ENVIRONMENT & ELEMENTAL",
                "FREE UTILITIES"
        );

        Map<String, List<VerbDoc>> byCategory = new LinkedHashMap<>();
        for (String cat : categoryOrder) {
            byCategory.put(cat, new ArrayList<>());
        }

        for (Verb verb : Verbs.all()) {
            VerbDoc doc = verb.doc();
            if (doc != null) {
                byCategory.computeIfAbsent(doc.category(), k -> new ArrayList<>()).add(doc);
            }
        }

        for (Map.Entry<String, List<VerbDoc>> entry : byCategory.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            String cat = entry.getKey();
            sb.append(cat);
            if (cat.equals("FREE UTILITIES")) {
                sb.append(" (0 AP, unlimited use, never count as a line)");
            }
            sb.append(":\n");

            for (VerbDoc doc : entry.getValue()) {
                sb.append(String.format("  %-20s %-8s %s%n",
                        shortSynopsis(doc),
                        shortCost(doc),
                        doc.summary()));
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

        Verb verb = Verbs.get(q);
        if (verb != null && verb.doc() != null) return renderVerb(verb.doc());

        String topicDoc = TOPICS.get(q);
        if (topicDoc != null) return topicDoc.stripTrailing();

        return null;
    }

    public static Set<String> topicNames() {
        return Collections.unmodifiableSet(TOPICS.keySet());
    }
}
