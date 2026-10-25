package com.archon.verb;

import com.archon.command.FlagSpec;
import com.archon.exec.EconomyRules;

import java.util.*;
import java.util.stream.Collectors;

public final class CommandManual {

    private static final List<String> TOPICS = List.of("operators", "address", "economy", "flags");

    private final EconomyRules rules;
    private VerbCatalog catalog;

    public CommandManual(EconomyRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /** Composition-only initialization, before this manual is published for use. */
    public void bind(VerbCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        if (this.catalog != null) {
            throw new IllegalStateException("command manual is already bound");
        }
        this.catalog = catalog;
    }

    /** Null or blank selects the index. Unknown queries return a diagnostic. */
    public String render(String query) {
        if (catalog == null) {
            throw new IllegalStateException("command manual has not been bound");
        }
        if (query == null || query.isBlank()) {
            return renderIndex();
        }

        String name = query.strip().toLowerCase(Locale.ROOT);
        VerbDefinition definition = catalog.get(name);
        if (definition != null) {
            return renderVerb(definition);
        }

        String topic = switch (name) {
            case "operators" -> operators();
            case "address" -> addressing();
            case "economy" -> economy();
            case "flags" -> flags();
            default -> null;
        };
        return topic == null ? "no such command or topic: " + query : topic.stripTrailing();
    }

    private String renderIndex() {
        StringBuilder out = new StringBuilder("""
                ARCHON COMMAND MANUAL
                Type 'help <verb>' for a command page, or 'help <topic>' for a reference guide.
                
                """);

        Map<String, List<VerbDefinition>> categories = new LinkedHashMap<>();
        for (VerbDefinition definition : catalog.all()) {
            categories.computeIfAbsent(definition.documentation().category(), ignored -> new ArrayList<>()).add(
                    definition);
        }

        for (Map.Entry<String, List<VerbDefinition>> category : categories.entrySet()) {
            out.append(category.getKey()).append(":\n");
            for (VerbDefinition definition : category.getValue()) {
                out.append("  ").append(synopsis(definition)).append(" — ").append(
                        definition.documentation().summary()).append('\n');
                out.append("    ").append(definition.price().describe());
                if (definition.free()) out.append("; free");
                if (definition.terminal()) out.append("; terminal");
                out.append('\n');
            }
            out.append('\n');
        }

        out.append("REFERENCE TOPICS:\n  ");
        out.append(TOPICS.stream().map(topic -> "help " + topic).collect(Collectors.joining(" | ")));
        return out.toString();
    }

    private String renderVerb(VerbDefinition definition) {
        VerbDefinition.Documentation doc = definition.documentation();
        StringBuilder out = new StringBuilder();

        out.append("NAME:\n  ").append(definition.name()).append(" — ").append(doc.summary()).append("\n\n");
        out.append("SYNOPSIS:\n  ").append(synopsis(definition));
        for (FlagSpec option : definition.options()) {
            out.append(" [").append(optionSyntax(option)).append(']');
        }
        out.append("\n\n");
        out.append("AP COST:\n  ").append(definition.price().describe()).append('\n');
        out.append("  Free: ").append(definition.free() ? "yes" : "no").append('\n');
        out.append("  Terminal: ").append(definition.terminal() ? "yes" : "no").append("\n\n");
        out.append("CATEGORY:\n  ").append(doc.category()).append("\n\n");

        out.append("DESCRIPTION:\n");
        for (String line : doc.description().split("\\R")) {
            out.append("  ").append(line).append('\n');
        }

        out.append("\nPIPELINE:\n");
        out.append("  Accepted kinds: ").append(kinds(definition.input())).append('\n');
        out.append("  Possible output kinds: ").append(kinds(definition.output())).append('\n');
        if (!definition.output().isEmpty()) {
            out.append("  Output is possible, not guaranteed on every invocation.\n");
        }

        out.append("\nOPTIONS:\n");
        for (FlagSpec option : definition.options()) {
            appendOption(out, option);
        }

        if (!doc.examples().isEmpty()) {
            out.append("\nEXAMPLES:\n");
            for (String example : doc.examples()) {
                out.append("  ").append(example).append('\n');
            }
        }

        return out.toString().stripTrailing();
    }

    private static String synopsis(VerbDefinition definition) {
        String operands = definition.documentation().operands();
        return definition.name() + (operands.isBlank() ? "" : " " + operands);
    }

    private static String optionSyntax(FlagSpec option) {
        String name = option.shortName() == null ? "--" + option.name() : "-" + option.shortName();
        if (!option.takesValue()) {
            return name;
        }

        String value = "<" + option.valueName() + ">";
        return name + " " + (option.bareDefault() == null ? value : "[" + value + "]");
    }

    private static void appendOption(StringBuilder out, FlagSpec option) {
        out.append("  ");
        if (option.shortName() != null) {
            out.append('-').append(option.shortName()).append(", ");
        }
        out.append("--").append(option.name());
        if (option.takesValue()) {
            out.append(option.bareDefault() == null ? " <" : " [<").append(option.valueName()).append(
                    option.bareDefault() == null ? ">" : ">]");
        }
        out.append("\n    ").append(option.description());
        if (option.bareDefault() != null) {
            out.append(" Bare option value: ").append(option.bareDefault()).append('.');
        }
        out.append('\n');
    }

    private static String kinds(Set<MaterialKind> kinds) {
        return kinds.isEmpty() ? "none" : kinds.stream().sorted().map(
                kind -> kind.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
    }

    private String flags() {
        StringBuilder out = new StringBuilder("""
                TOPIC: COMMAND FLAGS
                Options belong to individual commands. An option supported by one command
                is not automatically accepted by another.
                
                GLOBAL LINE OPTION:
                """);

        for (VerbDefinition definition : catalog.all()) {
            if (definition.options().isEmpty()) continue;
            out.append('\n').append(definition.name()).append(":\n");
            for (FlagSpec option : definition.options()) {
                appendOption(out, option);
            }
            out.append("  Pricing: ").append(definition.price().describe()).append('\n');
        }

        out.append("""
                
                PARSING:
                  Short boolean options may be grouped.
                  An option taking a value must be last in a short-option group.
                  Repeated options use the final value.
                  --name=value supplies an inline value.
                
                CURRENT LIMITATIONS:
                  There is no end-of-options '--' marker.
                  A separated value beginning with '-' is not consumed as an option value.
                  Boolean options are enabled by presence; '--careful=false' still enables it.
                """);
        return out.toString();
    }

    private static String operators() {
        return """
                TOPIC: COMMAND OPERATORS
                
                ALL LINES, INCLUDING FREE COMMANDS:
                  ;    Continue after any ordinary outcome.
                       Example: step e ; strike o1
                  &&   Continue only after SUCCESS.
                       Successful hits count as SUCCESS, not only kills.
                  ||   Recover after MISS, PARTIAL, or gameplay BLOCKED.
                       Example: strike o1 || guard
                  |    Pass actual material output within one pipeline stage.
                       Example: siphon b1/contents | pour @o1/floor
                
                BRANCHING:
                  Evaluate stages left to right. Skips preserve the last executed outcome.
                  In 'a && b || c', c runs if a fails or an executed b fails.
                  Ordinary failure does not break a line. Interruption does, without recovery.
                  Malformed commands are rejected before commitment, even in skipped branches.
                  Missing, unsuitable, or currently unreachable targets are gameplay failures.
                
                PIPELINES:
                  Every non-final command must declare possible output.
                  Its consumer must accept every declared possible output kind.
                  Stop at the first non-success, preserving its outcome.
                  Successful production of no material blocks the next consumer.
                  There is no implicit forwarding of consumed input or fallback to operands.
                  Materials do not cross ;, &&, or ||. Earlier effects are not rolled back.
                  Pour emits nothing and Ignite accepts nothing. Sequence them instead:
                    siphon b1/contents | pour @o1/floor && ignite @o1/floor
                
                TERMINAL COMMANDS:
                  Must be the final invocation, including on free-only lines.
                  End the round only when actually executed successfully.
                  A skipped terminal or failed upstream producer does not end the round.
                
                REACTIONS:
                  Check between executed paid stages and after normally completed paid work.
                  Free and skipped stages add no checkpoints. Pipelines add no internal checks.
                  A post-completion reaction does damage but does not retroactively break a line.
                """;
    }

    private static String addressing() {
        return """
                TOPIC: ADDRESSING SYNTAX
                
                TILES:
                  @x,y                 Absolute tile, for example @3,4.
                  @self                The thrall's current tile.
                  @<entityId>          The tile occupied by an entity, for example @o1.
                  @x,y/floor           Explicit floor layer.
                  @x,y/ceiling         Explicit ceiling layer.
                  @<dir><distance>     Relative tile, for example @e2 or @sw1.
                
                THRALL INVENTORY:
                  /pack                The pack location, not a selected item.
                  /pack/<item>         A selected packed item, for example /pack/torch.
                
                ENTITIES, BODY PARTS, AND EQUIPMENT:
                  <entityId>           The entity itself.
                  <entity>/body/head   An explicit body-part target.
                  <entity>/body/torso
                  <entity>/body/arm.l
                  <entity>/body/arm.r
                  <entity>/body/legs
                
                  <entity>/head        Equipment location, not the head body part.
                  <entity>/hand/right  Right-hand equipment.
                  <entity>/hand/left   Left-hand equipment.
                
                EXAMPLES:
                  inspect o1/body/head
                  strike o1/body/head
                  strike o1/hand/right
                  take o1/hand/right
                
                EMPTY LOCATIONS:
                  An empty equipment slot, empty prop contents, or pack root is a location,
                  not an item. Commands requiring an item may be blocked by those targets.
                  Resolution does not imply that a command can reach or use a target.
                """;
    }

    private String economy() {
        return """
                TOPIC: ACTION ECONOMY
                
                ROUND BUDGET:
                  Each round begins with %d AP.
                  Unspent AP is discarded when the round ends.
                
                LINE TAX:
                  Untaxed paid lines per round: %d.
                  Tax on each subsequent paid line: %d AP.
                  This is fixed, not escalating; commitment tax is never refunded.
                  A submission containing paid work is paid even if that work is later skipped.
                
                ALLOCATION:
                  A pipeline allocates its final invocation's price, not the sum inside it.
                  A line allocates every stage, including conditional stages.
                  Allocation plus tax must be affordable before commitment.
                  Singles permit adaptation; complex lines commit more work in advance.
                
                COMPLETION AND RECOVERY:
                  Every attempted ordinary stage pays its normal stage price, including
                  MISS, PARTIAL, BLOCKED, and pipelines whose producer fails.
                  An executed fallback also pays its price. Skipped stages pay nothing.
                  Ordinary failure without successful recovery still has no break penalty.
                  Rejected submissions spend nothing and consume no paid line.
                
                INTERRUPTIONS:
                  A broken line forfeits its allocation plus %d AP, capped by AP after tax.
                  This replaces ordinary trace charging rather than adding to it.
                  The trace records half the interrupted stage price, rounded down.
                  This applies before a stage begins and during an interrupted pipeline.
                
                FREE COMMANDS:
                  Free-only lines use the same language without AP, tax, paid-line counting,
                  movement resets, or runner-managed reactions.
                  Free commands inside paid lines do not remove the commitment tax.
                
                PASS:
                  A successfully executed terminal requests the end of the round.
                  The application discards remaining AP when advancing the round.
                """.formatted(rules.roundAp(), rules.untaxedLines(), rules.lineTax(), rules.breakPenalty());
    }
}