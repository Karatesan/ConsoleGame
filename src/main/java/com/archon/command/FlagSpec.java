package com.archon.command;

import java.util.LinkedHashMap;
import java.util.Map;

public record FlagSpec(
        String name,
        String shortName,
        String valueName,
        String bareDefault,
        String description
) {
    public static final FlagSpec AUDIT = new FlagSpec(
            "dry-run", "n", null, null, "Audit the entire line without executing or spending AP.");

    public FlagSpec {
        java.util.Objects.requireNonNull(description, "description");
        if (name == null || !name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("invalid option name: " + name);
        }
        if (shortName != null && !shortName.matches("[A-Za-z]")) {
            throw new IllegalArgumentException("short option must be one letter");
        }
        if (valueName != null && valueName.isBlank()) {
            throw new IllegalArgumentException("option value name must not be blank");
        }
        if (valueName == null && bareDefault != null) {
            throw new IllegalArgumentException("boolean option cannot have a value default");
        }
    }

    public boolean takesValue() { return valueName != null; }
}