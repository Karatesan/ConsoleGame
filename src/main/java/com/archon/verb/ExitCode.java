package com.archon.verb;

public enum ExitCode {
    SUCCESS(0), PARTIAL(1), MISS(2), BLOCKED(3), INVALID(4), INTERRUPT(5);

    public final int code;
    ExitCode(int code) { this.code = code; }

    public boolean breaksChain() { return this == BLOCKED || this == INTERRUPT; }
    public boolean charges()     { return this == SUCCESS || this == PARTIAL || this == MISS; }
    @Override public String toString() { return name() + " [" + code + "]"; }
}