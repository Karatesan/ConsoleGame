package com.archon.command;

import java.util.*;

public final class CommandParser {

    public static final class ParseError extends RuntimeException {
        public final String hint;
        public ParseError(String msg, String hint) { super(msg); this.hint = hint; }
    }

    public Ast.Line parse(String raw) {
        List<String> tokens = lex(raw);
        if (tokens.isEmpty()) return new Ast.Line(List.of(), false, raw);

        List<Ast.Stage> stages = new ArrayList<>();
        List<Ast.Invocation> pipeline = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Ast.Op pendingOp = Ast.Op.NONE;
        boolean dryRun = false;

        for (int i = 0; i <= tokens.size(); i++) {
            String t = i < tokens.size() ? tokens.get(i) : null;
            boolean isPipe = "|".equals(t);
            boolean isSep = ";".equals(t) || "&&".equals(t) || "||".equals(t);

            if (t == null || isPipe || isSep) {
                if (current.isEmpty()) {
                    if (t == null && pipeline.isEmpty() && stages.isEmpty()) break;
                    throw new ParseError("operator with no command",
                            "every operator must sit between two commands");
                }
                Ast.Invocation inv = buildInvocation(current);
                if (inv.hasFlag("dry-run")) dryRun = true;
                pipeline.add(inv);
                current = new ArrayList<>();

                if (t == null || isSep) {
                    stages.add(new Ast.Stage(List.copyOf(pipeline), pendingOp));
                    pipeline = new ArrayList<>();
                    if (t != null) pendingOp = switch (t) {
                        case "&&" -> Ast.Op.AND;
                        case "||" -> Ast.Op.OR;
                        default   -> Ast.Op.SEQ;
                    };
                }
                continue;
            }
            current.add(t);
        }
        if (!pipeline.isEmpty())
            throw new ParseError("unterminated pipeline", "a line may not end with |");
        return new Ast.Line(stages, dryRun, raw);
    }

    private Ast.Invocation buildInvocation(List<String> tokens) {
        String verb = tokens.get(0).toLowerCase();
        List<String> args = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();

        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("--")) {
                String body = t.substring(2);
                String name, value = null;
                int eq = body.indexOf('=');
                if (eq >= 0) { name = body.substring(0, eq); value = body.substring(eq + 1); }
                else name = body;
                FlagSpec spec = FlagSpec.BY_NAME.get(name);
                if (spec == null) throw new ParseError("unknown flag --" + name, knownFlags());
                if (spec.takesValue() && value == null) {
                    if (i + 1 < tokens.size() && !isOperandBreak(tokens.get(i + 1))) value = tokens.get(++i);
                    else if (spec.bareDefault() != null) value = spec.bareDefault();
                    else throw new ParseError("--" + name + " requires a value", null);
                }
                flags.put(name, value == null ? "" : value);
            } else if (t.startsWith("-") && t.length() > 1 && !isNumeric(t)) {
                String group = t.substring(1);
                for (int c = 0; c < group.length(); c++) {
                    String sh = String.valueOf(group.charAt(c));
                    FlagSpec spec = FlagSpec.BY_SHORT.get(sh);
                    if (spec == null) throw new ParseError("unknown flag -" + sh, knownFlags());
                    String value = "";
                    if (spec.takesValue()) {
                        boolean last = c == group.length() - 1;
                        if (last && i + 1 < tokens.size() && !isOperandBreak(tokens.get(i + 1))) value = tokens.get(++i);
                        else if (spec.bareDefault() != null) value = spec.bareDefault();
                        else throw new ParseError("-" + sh + " requires a value", null);
                    }
                    flags.put(spec.name(), value);
                }
            } else {
                args.add(t);
            }
        }
        return new Ast.Invocation(verb, List.copyOf(args), Map.copyOf(flags));
    }

    private static boolean isOperandBreak(String t) {
        return t.equals("|") || t.equals(";") || t.equals("&&") || t.equals("||") || t.startsWith("-");
    }
    private static boolean isNumeric(String t) { return t.matches("-?\\d+"); }
    private static String knownFlags() { return "known flags: " + String.join(", ", FlagSpec.BY_NAME.keySet()); }

    private List<String> lex(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') { quoted = !quoted; continue; }
            if (quoted) { sb.append(c); continue; }
            if (Character.isWhitespace(c)) { flush(out, sb); continue; }
            if (c == ';' || c == '|' || c == '&' || c == '>') {
                flush(out, sb);
                if ((c == '|' || c == '&') && i + 1 < raw.length() && raw.charAt(i + 1) == c) {
                    out.add("" + c + c); i++;
                } else out.add(String.valueOf(c));
                continue;
            }
            sb.append(c);
        }
        if (quoted) throw new ParseError("unbalanced quote", null);
        flush(out, sb);
        return out;
    }
    private void flush(List<String> out, StringBuilder sb) {
        if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
    }
}