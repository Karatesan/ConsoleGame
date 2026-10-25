package com.archon.command;

public record FlagSpec(
        String name,
        String shortName,
        String valueName,
        String bareDefault,
        String description
) {
    public boolean takesValue() {
        return valueName != null;
    }
}