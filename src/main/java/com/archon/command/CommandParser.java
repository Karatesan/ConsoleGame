package com.archon.command;

import com.archon.verb.VerbCatalog;
import com.archon.verb.VerbDefinition;

import java.util.*;

public final class CommandParser {

    public static final class ParseError extends RuntimeException {
        public final String hint;
        public ParseError(String msg, String hint) {
            super(msg);
            this.hint = hint;
        }
    }

    private final VerbCatalog catalog;

    public CommandParser(VerbCatalog catalog) {
        this.catalog = catalog;
    }

    public Ast.Line parse(String raw) {
        List<String> tokens = splitToTokens(raw);
        if (tokens.isEmpty()) return new Ast.Line(List.of(), raw);

        List<Ast.Stage> stages = new ArrayList<>();
        List<Ast.Invocation> pipeline = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Ast.Op pendingOp = Ast.Op.NONE;

        for (String token : tokens) {
            boolean pipe = "|".equals(token);
            boolean sep = ";".equals(token) || "&&".equals(token) || "||".equals(token);

            if (pipe || sep) {
                if (current.isEmpty()) {
                    throw new ParseError("operator with no command", "every operator must sit between two commands");
                }
                pipeline.add(parseInvocation(current));
                current.clear();

                if (sep) {
                    stages.add(new Ast.Stage(List.copyOf(pipeline), pendingOp));
                    pipeline.clear();
                    pendingOp = switch (token) {
                        case "&&" -> Ast.Op.AND;
                        case "||" -> Ast.Op.OR;
                        default -> Ast.Op.SEQ;
                    };
                }
            } else {
                current.add(token);
            }
        }

        if (current.isEmpty()) {
            throw new ParseError("operator with no command", "a command line cannot end with an operator or pipe");
        }
        pipeline.add(parseInvocation(current));
        stages.add(new Ast.Stage(List.copyOf(pipeline), pendingOp));

        return new Ast.Line(List.copyOf(stages), raw);
    }

    private Ast.Invocation parseInvocation(List<String> tokens) {
        String commandName = tokens.getFirst().toLowerCase(Locale.ROOT);
        VerbDefinition def = catalog.get(commandName);
        if (def == null) {
            throw new ParseError("unknown verb \"" + commandName + "\"", "verbs: " + String.join(" ", catalog.names()));
        }

        List<String> args = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();

        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("--")) {
                int eq = t.indexOf('=');
                String flagName = eq < 0 ? t.substring(2) : t.substring(2, eq);
                FlagSpec spec = findFlag(def, flagName, false);
                if (eq >= 0) {
                    flags.put(spec.name(), t.substring(eq + 1));
                } else if (spec.takesValue()) {
                    i = readValue(spec, "--" + flagName, tokens, i, flags);
                } else {
                    flags.put(spec.name(), "");
                }
            } else if (t.startsWith("-") && t.length() > 1 && !t.matches("-?\\d+")) {
                String group = t.substring(1);
                for (int c = 0; c < group.length(); c++) {
                    String shortName = String.valueOf(group.charAt(c));
                    FlagSpec spec = findFlag(def, shortName, true);
                    if (spec.takesValue()) {
                        if (c != group.length() - 1) {
                            throw new ParseError("-" + shortName + " requires a value and must be at the end of the flag group", null);
                        }
                        i = readValue(spec, "-" + shortName, tokens, i, flags);
                    } else {
                        flags.put(spec.name(), "");
                    }
                }
            } else {
                args.add(t);
            }
        }
        return new Ast.Invocation(commandName, List.copyOf(args), Collections.unmodifiableMap(flags));
    }

    private int readValue(FlagSpec spec, String spelling, List<String> tokens, int i, Map<String, String> flags) {
        if (i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("-")) {
            flags.put(spec.name(), tokens.get(i + 1));
            return i + 1;
        }
        if (spec.bareDefault() != null) {
            flags.put(spec.name(), spec.bareDefault());
            return i;
        }
        throw new ParseError(spelling + " requires a value", null);
    }

    private FlagSpec findFlag(VerbDefinition def, String name, boolean isShort) {
        FlagSpec spec = def.findOption(name, isShort);
        if (spec != null) return spec;
        List<String> known = def.options().stream().map(s -> "--" + s.name()).toList();
        throw new ParseError("unknown option " + (isShort ? "-" : "--") + name + " for " + def.name(),
                "options: " + String.join(", ", known));
    }

    private List<String> splitToTokens(String raw) {
        List<String> wordsList = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (quoted) {
                currentWord.append(c);
            } else if (Character.isWhitespace(c)) {
                if (!currentWord.isEmpty()) { wordsList.add(currentWord.toString()); currentWord.setLength(0); }
            } else if (c == ';' || c == '|' || c == '&') {
                if (!currentWord.isEmpty()) { wordsList.add(currentWord.toString()); currentWord.setLength(0); }
                if ((c == '|' || c == '&') && i + 1 < raw.length() && raw.charAt(i + 1) == c) {
                    wordsList.add("" + c + c);
                    i++;
                } else {
                    wordsList.add(String.valueOf(c));
                }
            } else {
                currentWord.append(c);
            }
        }
        if (quoted) throw new ParseError("unbalanced quote", null);
        if (!currentWord.isEmpty()) wordsList.add(currentWord.toString());
        return wordsList;
    }
}