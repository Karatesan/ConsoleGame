package com.archon.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CommandParser {

    private final InvocationParser invocations;

    public CommandParser(InvocationParser invocations) {
        this.invocations = Objects.requireNonNull(invocations, "invocations");
    }

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

        for (String token : tokens) {
            boolean pipe = "|".equals(token);
            boolean separator = ";".equals(token) || "&&".equals(token) || "||".equals(token);

            if (pipe || separator) {
                if (current.isEmpty()) {
                    throw new ParseError("operator with no command",
                            "every operator must sit between two commands");
                }
                Ast.Invocation invocation = invocations.parse(current);
                if (invocation.hasFlag(FlagSpec.AUDIT.name())) dryRun = true;
                pipeline.add(invocation);
                current.clear();

                if (separator) {
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

        if (!current.isEmpty()) {
            Ast.Invocation invocation = invocations.parse(current);
            if (invocation.hasFlag(FlagSpec.AUDIT.name())) dryRun = true;
            pipeline.add(invocation);
        } else if (!pipeline.isEmpty() || pendingOp != Ast.Op.NONE) {
            throw new ParseError("operator with no command",
                    "a command line cannot end with an operator or pipe");
        }

        if (!pipeline.isEmpty()) {
            stages.add(new Ast.Stage(List.copyOf(pipeline), pendingOp));
        }
        return new Ast.Line(List.copyOf(stages), dryRun, raw);
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