package com.archon.command;

import com.archon.verb.VerbCatalog;
import com.archon.verb.VerbDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Parses a single command's arguments and metadata-declared options. */
public final class InvocationParser {

    private static final Pattern NUMERIC = Pattern.compile("-?\\d+");

    private final VerbCatalog catalog;

    public InvocationParser(VerbCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    Ast.Invocation parse(List<String> tokens) {
        String name = tokens.getFirst().toLowerCase(Locale.ROOT);
        VerbDefinition definition = catalog.get(name);
        if (definition == null) {
            throw new CommandParser.ParseError(
                    "unknown verb \"" + name + "\"",
                    "verbs: " + String.join(" ", catalog.names())
            );
        }

        List<String> args = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();
        int index = 1;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (token.startsWith("--")) {
                index = longFlag(definition, tokens, index, flags);
            } else if (token.startsWith("-") && token.length() > 1
                    && !NUMERIC.matcher(token).matches()) {
                index = shortFlags(definition, tokens, index, flags);
            } else {
                args.add(token);
                index++;
            }
        }

        return new Ast.Invocation(
                name,
                List.copyOf(args),
                Collections.unmodifiableMap(new LinkedHashMap<>(flags))
        );
    }

    private int longFlag(
            VerbDefinition definition,
            List<String> tokens,
            int index,
            Map<String, String> flags
    ) {
        String body = tokens.get(index).substring(2);
        int equals = body.indexOf('=');
        String name = equals < 0 ? body : body.substring(0, equals);
        FlagSpec spec = find(definition, name, false);

        if (equals >= 0) {
            flags.put(spec.name(), body.substring(equals + 1));
            return index + 1;
        }

        Value result = value(spec, "--" + name, tokens, index);
        flags.put(spec.name(), result.text());
        return result.next();
    }

    private int shortFlags(
            VerbDefinition definition,
            List<String> tokens,
            int index,
            Map<String, String> flags
    ) {
        String group = tokens.get(index).substring(1);
        int next = index + 1;
        for (int offset = 0; offset < group.length(); offset++) {
            String name = String.valueOf(group.charAt(offset));
            FlagSpec spec = find(definition, name, true);
            if (spec.takesValue() && offset != group.length() - 1) {
                throw new CommandParser.ParseError(
                        "-" + name + " requires a value and must be at the end of the flag group",
                        null
                );
            }

            Value result = value(spec, "-" + name, tokens, index);
            flags.put(spec.name(), result.text());
            next = result.next();
        }
        return next;
    }

    private FlagSpec find(VerbDefinition definition, String name, boolean shortForm) {
        FlagSpec audit = FlagSpec.AUDIT;
        if (name.equals(shortForm ? audit.shortName() : audit.name())) {
            return audit;
        }
        for (FlagSpec spec : definition.options()) {
            if (name.equals(shortForm ? spec.shortName() : spec.name())) {
                return spec;
            }
        }

        List<String> known = new ArrayList<>();
        known.add("--" + audit.name());
        for (FlagSpec spec : definition.options()) {
            known.add("--" + spec.name());
        }
        throw new CommandParser.ParseError(
                "unknown option " + (shortForm ? "-" : "--") + name + " for " + definition.name(),
                "options: " + String.join(", ", known)
        );
    }

    private record Value(String text, int next) {}

    private Value value(FlagSpec spec, String spelling, List<String> tokens, int index) {
        if (!spec.takesValue()) {
            return new Value("", index + 1);
        }

        int next = index + 1;
        if (next < tokens.size() && !operandBreak(tokens.get(next))) {
            return new Value(tokens.get(next), next + 1);
        }
        if (spec.bareDefault() != null) {
            return new Value(spec.bareDefault(), next);
        }
        throw new CommandParser.ParseError(spelling + " requires a value", null);
    }

    private boolean operandBreak(String token) {
        return token.equals("|") || token.equals(";")
                || token.equals("&&") || token.equals("||") || token.startsWith("-");
    }
}