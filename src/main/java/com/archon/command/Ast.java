package com.archon.command;

import java.util.List;
import java.util.Map;

public final class Ast {
    private Ast() {}

    public enum Op { NONE, SEQ, AND, OR }

    public record Invocation(String verb, List<String> args, Map<String, String> flags) {
        public String flag(String name) { return flags.get(name); }
        public boolean hasFlag(String name) { return flags.containsKey(name); }
        public String arg(int i) { return i < args.size() ? args.get(i) : null; }

        public String render() {
            StringBuilder sb = new StringBuilder(verb);
            args.forEach(a -> sb.append(' ').append(a));
            flags.forEach((k, v) -> sb.append(" --").append(k).append(v.isEmpty() ? "" : "=" + v));
            return sb.toString();
        }
    }

    public record Stage(List<Invocation> pipeline, Op op) {
        public Invocation last() { return pipeline.getLast(); }
        public String render() {
            return String.join(" | ", pipeline.stream().map(Invocation::render).toList());
        }
    }

    public record Line(List<Stage> stages, String raw) {
        public int verbCount() { return stages.stream().mapToInt(s -> s.pipeline().size()).sum(); }
        public boolean isChain() { return verbCount() >= 2; }
    }
}