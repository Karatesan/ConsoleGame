package com.archon.verb;

import com.archon.command.FlagSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One command's implementation and authoritative metadata. */
public record VerbDefinition(
        String name,
        Verb action,
        List<FlagSpec> options,
        ApCost price,
        boolean free,
        boolean terminal,
        Set<MaterialKind> input,
        Set<MaterialKind> output,
        Documentation documentation
) {
    public VerbDefinition {
        if (name == null || !name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("invalid command name: " + name);
        }
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(documentation, "documentation");
        options = List.copyOf(options);
        input = Set.copyOf(input);
        output = Set.copyOf(output);

        if (free && !(price instanceof ApCost.Fixed fixed && fixed.ap() == 0)) {
            throw new IllegalArgumentException("free command must have a fixed zero AP cost: " + name);
        }

        Set<String> names = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        names.add(FlagSpec.AUDIT.name());
        aliases.add(FlagSpec.AUDIT.shortName());
        for (FlagSpec option : options) {
            if (!names.add(option.name())) {
                throw new IllegalArgumentException("duplicate or reserved option: " + option.name());
            }
            if (option.shortName() != null && !aliases.add(option.shortName())) {
                throw new IllegalArgumentException("duplicate or reserved short option: " + option.shortName());
            }
        }

        String pricedOption = switch (price) {
            case ApCost.Fixed ignored -> null;
            case ApCost.ByPresence presence -> presence.flag();
            case ApCost.ByValue values -> values.flag();
        };
        if (pricedOption != null
                && options.stream().noneMatch(option -> option.name().equals(pricedOption))) {
            throw new IllegalArgumentException("cost refers to an undeclared option: " + pricedOption);
        }
    }

    /** Authored prose only: names, options, prices and material claims live above. */
    public record Documentation(
            String category,
            String operands,
            String summary,
            String description,
            List<String> examples
    ) {
        public Documentation {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(operands, "operands");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(description, "description");
            examples = List.copyOf(examples);
        }
    }
}