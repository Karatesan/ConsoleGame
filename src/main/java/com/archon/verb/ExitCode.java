package com.archon.verb;

public enum ExitCode {
    SUCCESS(0), PARTIAL(1), MISS(2), BLOCKED(3), INVALID(4), INTERRUPT(5);

    public final int code;

    ExitCode(int code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return name() + " [" + code + "]";
    }
}