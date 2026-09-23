package com.archon.system.combat;

import java.util.Objects;

/** Facts captured when one readied reaction fires; damage retains its authored meaning. */
public record Reaction(String sourceId, String sourceName, String description, int damage) {

    public Reaction {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(description, "description");
        if (sourceId.isBlank() || sourceName.isBlank() || description.isBlank()) {
            throw new IllegalArgumentException("reaction reporting fields must not be blank");
        }
        if (damage < 0) {
            throw new IllegalArgumentException("reaction damage must be nonnegative");
        }
    }
}