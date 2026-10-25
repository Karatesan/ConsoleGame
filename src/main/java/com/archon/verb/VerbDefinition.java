package com.archon.verb;

import com.archon.command.FlagSpec;

import java.util.List;
import java.util.Set;

/** One command's implementation and authoritative metadata. */
public record VerbDefinition(
        String name,
        Verb action,
        List<FlagSpec> options,
        ApCost price,
        boolean terminal,
        Set<MaterialKind> input,
        Set<MaterialKind> output,
        Documentation documentation
) {
    public VerbDefinition {
        options = List.copyOf(options);
        input = Set.copyOf(input);
        output = Set.copyOf(output);
    }

    public boolean free() {
        return price instanceof ApCost.Fixed(int ap) && ap == 0;
    }

    public FlagSpec findOption(String flagName, boolean isShort) {
        for (FlagSpec spec : options) {
            if (flagName.equals(isShort ? spec.shortName() : spec.name())) {
                return spec;
            }
        }
        return null;
    }

    public record Documentation(
            String category,
            String operands,
            String summary,
            String description,
            List<String> examples
    ) {
        public Documentation {
            examples = List.copyOf(examples);
        }
    }
}