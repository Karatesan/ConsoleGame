package com.archon.verb;

import com.archon.command.Ast;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Invocation pricing and its generated documentation. */
public sealed interface ApCost {

    int cost(Ast.Invocation invocation);

    String describe();

    record Fixed(int ap) implements ApCost {
        public Fixed {
            requireCost(ap);
        }

        @Override
        public int cost(Ast.Invocation invocation) {
            return ap;
        }

        @Override
        public String describe() {
            return ap + " AP";
        }
    }

    record ByPresence(String flag, int absent, int present) implements ApCost {
        public ByPresence {
            requireFlag(flag);
            requireCost(absent);
            requireCost(present);
        }

        @Override
        public int cost(Ast.Invocation invocation) {
            return invocation.hasFlag(flag) ? present : absent;
        }

        @Override
        public String describe() {
            return absent + " AP; " + present + " AP with --" + flag;
        }
    }

    record ByValue(String flag, int fallback, Map<String, Integer> values) implements ApCost {
        public ByValue {
            requireFlag(flag);
            requireCost(fallback);
            values = Map.copyOf(values);
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (entry.getKey().isBlank()
                        || !entry.getKey().equals(entry.getKey().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("cost values must use lowercase names");
                }
                requireCost(entry.getValue());
            }
        }

        @Override
        public int cost(Ast.Invocation invocation) {
            String value = invocation.flag(flag);
            return value == null
                    ? fallback
                    : values.getOrDefault(value.toLowerCase(Locale.ROOT), fallback);
        }

        @Override
        public String describe() {
            String choices = values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ": " + entry.getValue() + " AP")
                    .collect(Collectors.joining(", "));
            return fallback + " AP by default; --" + flag + " {" + choices + "}";
        }
    }

    private static void requireCost(int cost) {
        if (cost < 0) {
            throw new IllegalArgumentException("AP cost must not be negative");
        }
    }

    private static void requireFlag(String flag) {
        Objects.requireNonNull(flag, "flag");
        if (!flag.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("invalid cost flag: " + flag);
        }
    }
}