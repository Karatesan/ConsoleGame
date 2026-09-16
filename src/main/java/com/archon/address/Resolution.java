package com.archon.address;

import java.util.Objects;

/** Result of resolving a parsed command address against the current world. */
public sealed interface Resolution permits Resolution.Found, Resolution.Failure {
    record Found(Resolved target) implements Resolution {
        public Found {
            Objects.requireNonNull(target, "target");
        }
    }

    record Failure(Reason reason, String detail) implements Resolution {
        public Failure {
            Objects.requireNonNull(reason, "reason");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("failure detail cannot be null or blank");
            }
        }
    }

    enum Reason {
        INVALID_SYNTAX,
        INVALID_TILE_SPEC,
        OUT_OF_BOUNDS,
        UNKNOWN_ENTITY,
        DEAD_ENTITY,
        INVALID_PATH,
        UNSUPPORTED_LAYER
    }
}