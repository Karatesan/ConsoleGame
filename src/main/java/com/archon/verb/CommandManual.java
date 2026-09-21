package com.archon.verb;

import com.archon.command.FlagSpec;
import com.archon.exec.EconomyRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders catalog metadata and authored cross-command reference topics. */
public final class CommandManual {

    private static final List<String> TOPICS =
            List.of("operators", "address", "economy", "flags");

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
        return topic == null
                ? "no such command or topic: " + query
                : topic.stripTrailing();
    }

    private String renderIndex() {
        StringBuilder out = new StringBuilder("""
                ARCHON COMMAND MANUAL
                Type 'help <verb>' for a command page, or 'help <topic>' for a reference guide.

                """);

        Map<String, List<VerbDefinition>> categories = new LinkedHashMap<>();
        for (VerbDefinition definition : catalog.all()) {
            categories.computeIfAbsent(
                    definition.documentation().category(),
                    ignored -> new ArrayList<>()
            ).add(definition);
        }

        for (Map.Entry<String, List<VerbDefinition>> category : categories.entrySet()) {
            out.append(category.getKey()).append(":\n");
            for (VerbDefinition definition : category.getValue()) {
                out.append("  ").append(synopsis(definition))
                        .append(" — ").append(definition.documentation().summary()).append('\n');
                out.append("    ").append(definition.price().describe());
                if (definition.free()) out.append("; free");
                if (definition.terminal()) out.append("; terminal");
                out.append('\n');
            }
            out.append('\n');
        }

        out.append("REFERENCE TOPICS:\n  ");
        out.append(TOPICS.stream()
                .map(topic -> "help " + topic)
                .collect(Collectors.joining(" | ")));
        return out.toString();
    }

    private String renderVerb(VerbDefinition definition) {
        VerbDefinition.Documentation doc = definition.documentation();
        StringBuilder out = new StringBuilder();

        out.append("NAME:\n  ").append(definition.name())
                .append(" — ").append(doc.summary()).append("\n\n");
        out.append("SYNOPSIS:\n  ").append(synopsis(definition));
        for (FlagSpec option : definition.options()) {
            out.append(" [").append(optionSyntax(option)).append(']');
        }
        out.append(" [").append(optionSyntax(FlagSpec.AUDIT)).append("]\n\n");

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
        appendOption(out, FlagSpec.AUDIT);

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
        String name = option.shortName() == null
                ? "--" + option.name()
                : "-" + option.shortName();
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
            out.append(option.bareDefault() == null ? " <" : " [<")
                    .append(option.valueName())
                    .append(option.bareDefault() == null ? ">" : ">]");
        }
        out.append("\n    ").append(option.description());
        if (option.bareDefault() != null) {
            out.append(" Bare option value: ").append(option.bareDefault()).append('.');
        }
        out.append('\n');
    }

    private static String kinds(Set<MaterialKind> kinds) {
        return kinds.isEmpty()
                ? "none"
                : kinds.stream()
                .sorted()
                .map(kind -> kind.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    private String flags() {
        StringBuilder out = new StringBuilder("""
                TOPIC: COMMAND FLAGS
                Options belong to individual commands. An option supported by one command
                is not automatically accepted by another.

                GLOBAL LINE OPTION:
                """);
        appendOption(out, FlagSpec.AUDIT);

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

                PAID OR MIXED LINES:
                  ;    Execute the next stage unless execution has broken.
                       Example: step e ; strike o1

                  &&   Execute the next stage when the preceding result is SUCCESS.
                       Successful melee and ranged hits count as SUCCESS, not only kills.
                       Example: strike o1 && step n

                  ||   Execute the next stage when the preceding result is not SUCCESS.
                       Example: strike o1 || guard

                  |    Pass item or substance output to the next command in one stage.
                       The stage costs only its final invocation.
                       Example: siphon b1/contents | pour @o1/floor

                PIPELINES:
                  Every non-final command must declare material output.
                  Every receiving command must declare a material input slot.
                  An unsuccessful upstream command stops the pipeline.
                  Effects already performed upstream are not rolled back.
                  Pour emits no material, and Ignite accepts none. Sequence them instead:
                    siphon b1/contents | pour @o1/floor && ignite @o1/floor

                CURRENT RECOVERY LIMITATIONS:
                  BLOCKED and INVALID still break paid execution before a fallback can run.
                  A blocked first stage may also be rejected during submission validation.
                  Thus '||' currently handles outcomes such as MISS, but is not yet general
                  recovery from a blocked action.
                  An actual interruption breaks execution.

                FREE-ONLY LINES:
                  Currently use a separate path: every stage executes without evaluating
                  && or ||, and no material is forwarded between commands.

                TERMINAL DETECTION:
                  The final invocation in the submitted syntax determines terminal status.
                  On a completed paid line, a terminal command can end the round even if
                  its stage was skipped. Terminal validation is bypassed on free-only lines.
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
                  not an item. Commands requiring an item may reject those targets.
                  Address resolution does not imply that a command can reach or use a target.
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
                  This is a fixed tax, not an escalating tax.
                  Tax is charged at commitment and is not refunded.
                  A chained line still pays tax if its submission is taxable.

                ALLOCATION:
                  A pipeline allocates the cost of its final invocation.
                  A line allocates the sum of its stage costs.
                  Conditional stages contribute to affordability even if later skipped.

                COMPLETION:
                  Executed stages are charged their stage costs.
                  Skipped stages are not charged.
                  Rejected submissions do not spend AP or consume a paid line.

                BREAKS:
                  After commitment, a broken line forfeits its allocation plus %d AP.
                  Forfeiture is capped by AP remaining after the submission tax.
                  This replaces ordinary completed-line charging; it is not an additional
                  charge on top of the trace's executed-stage total.
                  Interrupt accounting records half the interrupted stage cost, rounded down,
                  but broken-line settlement still uses allocation forfeiture.

                FREE COMMANDS AND AUDITS:
                  Entirely free lines bypass paid-line accounting.
                  A free command inside a paid line does not make the whole line tax-free.
                  Audit never spends AP. Its report is not yet identical to execution in
                  every case, particularly free-only lines and the post-tax forfeiture cap.

                PASS:
                  A terminal ending requests the end of the round.
                  Remaining AP is discarded when the application advances the round.
                """.formatted(
                rules.roundAp(),
                rules.untaxedLines(),
                rules.lineTax(),
                rules.breakPenalty()
        );
    }
}