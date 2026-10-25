package com.archon.verb;

import com.archon.command.Ast;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public sealed interface ApCost {

    int cost(Ast.Invocation invocation);
    String describe();

    record Fixed(int ap) implements ApCost {
        @Override public int cost(Ast.Invocation invocation) { return ap; }
        @Override public String describe() { return ap + " AP"; }
    }

    record ByPresence(String flag, int absent, int present) implements ApCost {
        @Override public int cost(Ast.Invocation invocation) { return invocation.hasFlag(flag) ? present : absent; }
        @Override public String describe() { return absent + " AP; " + present + " AP with --" + flag; }
    }

    record ByValue(String flag, int fallback, Map<String, Integer> values) implements ApCost {
        public ByValue { values = Map.copyOf(values); }

        @Override
        public int cost(Ast.Invocation invocation) {
            String value = invocation.flag(flag);
            return value == null ? fallback : values.getOrDefault(value.toLowerCase(Locale.ROOT), fallback);
        }

        @Override
        public String describe() {
            String choices = values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + ": " + e.getValue() + " AP")
                    .collect(Collectors.joining(", "));
            return fallback + " AP by default; --" + flag + " {" + choices + "}";
        }
    }
}