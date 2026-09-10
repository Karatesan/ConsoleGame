package com.archon.command;

import java.util.*;
import java.util.regex.Pattern;

public final class CommandParser {

    private static final Pattern NUMERIC = Pattern.compile("-?\\d+");

    public static final class ParseError extends RuntimeException {
        public final String hint;
        public ParseError(String msg, String hint) {
            super(msg);
            this.hint = hint;
        }
    }

    public Ast.Line parse(String raw) {
        List<String> tokens = lex(raw);
        if (tokens.isEmpty()) return new Ast.Line(List.of(), false, raw);

        List<Ast.Stage> stages = new ArrayList<>();
        List<Ast.Invocation> pipeline = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Ast.Op pendingOp = Ast.Op.NONE;
        boolean dryRun = false;

        for (String t : tokens) {
            boolean isPipe = "|".equals(t);
            boolean isSep = ";".equals(t) || "&&".equals(t) || "||".equals(t);

            if (isPipe || isSep) {
                if (current.isEmpty()) {
                    throw new ParseError("operator with no command",
                            "every operator must sit between two commands");
                }
                Ast.Invocation inv = buildInvocation(current);
                if (inv.hasFlag("dry-run")) dryRun = true;
                pipeline.add(inv);
                current.clear();

                if (isSep) {
                    stages.add(new Ast.Stage(List.copyOf(pipeline), pendingOp));
                    pipeline.clear();
                    pendingOp = switch (t) {
                        case "&&" -> Ast.Op.AND;
                        case "||" -> Ast.Op.OR;
                        default   -> Ast.Op.SEQ;
                    };
                }
            } else {
                current.add(t);
            }
        }

        // Domknięcie bufora po zakończeniu pętli
        if (!current.isEmpty()) {
            Ast.Invocation inv = buildInvocation(current);
            if (inv.hasFlag("dry-run")) dryRun = true;
            pipeline.add(inv);
        } else if (!pipeline.isEmpty() || pendingOp != Ast.Op.NONE) {
            throw new ParseError("operator with no command",
                    "a command line cannot end with an operator or pipe");
        }

        if (pipeline.isEmpty() && stages.isEmpty()) {
            return new Ast.Line(List.of(), false, raw);
        }

        if (!pipeline.isEmpty()) {
            stages.add(new Ast.Stage(List.copyOf(pipeline), pendingOp));
        }

        return new Ast.Line(stages, dryRun, raw);
    }

    private Ast.Invocation buildInvocation(List<String> tokens) {
        String verb = tokens.getFirst().toLowerCase();
        List<String> args = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();

        int i = 1;
        while (i < tokens.size()) {
            String t = tokens.get(i);

            if (t.startsWith("--")) {
                i = parseLongFlag(tokens, i, flags);
            } else if (t.startsWith("-") && t.length() > 1 && !isNumeric(t)) {
                i = parseShortFlags(tokens, i, flags);
            } else {
                args.add(t);
                i++;
            }
        }

        return new Ast.Invocation(verb, List.copyOf(args), Map.copyOf(flags));
    }

    private int parseLongFlag(List<String> tokens, int index, Map<String, String> flags) {
        String t = tokens.get(index);
        String body = t.substring(2);
        String name = body;
        String inlineValue = null;

        int eq = body.indexOf('=');
        if (eq >= 0) {
            name = body.substring(0, eq);
            inlineValue = body.substring(eq + 1);
        }

        FlagSpec spec = FlagSpec.BY_NAME.get(name);
        if (spec == null) throw new ParseError("unknown flag --" + name, knownFlags());

        if (inlineValue != null) {
            flags.put(name, inlineValue);
            return index + 1;
        }

        ValueResult result = resolveFlagValue(spec, "--" + name, tokens, index);
        flags.put(name, result.value);
        return result.nextIndex;
    }

    private int parseShortFlags(List<String> tokens, int index, Map<String, String> flags) {
        String group = tokens.get(index).substring(1);
        int nextIndex = index + 1;

        for (int c = 0; c < group.length(); c++) {
            String sh = String.valueOf(group.charAt(c));
            FlagSpec spec = FlagSpec.BY_SHORT.get(sh);
            if (spec == null) throw new ParseError("unknown flag -" + sh, knownFlags());

            boolean isLastInGroup = (c == group.length() - 1);
            if (spec.takesValue() && !isLastInGroup) {
                throw new ParseError("-" + sh + " requires a value and must be at the end of the flag group", null);
            }

            ValueResult result = resolveFlagValue(spec, "-" + sh, tokens, index);
            flags.put(spec.name(), result.value);
            nextIndex = result.nextIndex;
        }

        return nextIndex;
    }

    private record ValueResult(String value, int nextIndex) {}

    private ValueResult resolveFlagValue(FlagSpec spec, String flagPrefixAndName, List<String> tokens, int currentIndex) {
        if (!spec.takesValue()) {
            return new ValueResult("", currentIndex + 1);
        }

        int nextIdx = currentIndex + 1;
        if (nextIdx < tokens.size() && !isOperandBreak(tokens.get(nextIdx))) {
            return new ValueResult(tokens.get(nextIdx), nextIdx + 1);
        }
        if (spec.bareDefault() != null) {
            return new ValueResult(spec.bareDefault(), currentIndex + 1);
        }

        throw new ParseError(flagPrefixAndName + " requires a value", null);
    }

    private static boolean isOperandBreak(String t) {
        return t.equals("|") || t.equals(";") || t.equals("&&") || t.equals("||") || t.startsWith("-");
    }

    private static boolean isNumeric(String t) {
        return NUMERIC.matcher(t).matches();
    }

    private static String knownFlags() {
        return "known flags: " + String.join(", ", FlagSpec.BY_NAME.keySet());
    }

    private List<String> lex(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                sb.append(c);
                continue;
            }
            if (Character.isWhitespace(c)) {
                flush(out, sb);
                continue;
            }
            if (c == ';' || c == '|' || c == '&') {
                flush(out, sb);
                if ((c == '|' || c == '&') && i + 1 < raw.length() && raw.charAt(i + 1) == c) {
                    out.add("" + c + c);
                    i++;
                } else {
                    out.add(String.valueOf(c));
                }
                continue;
            }
            sb.append(c);
        }

        if (quoted) throw new ParseError("unbalanced quote", null);
        flush(out, sb);
        return out;
    }

    private void flush(List<String> out, StringBuilder sb) {
        if (!sb.isEmpty()) {
            out.add(sb.toString());
            sb.setLength(0);
        }
    }
}