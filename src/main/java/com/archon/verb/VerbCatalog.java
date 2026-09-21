package com.archon.verb;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, insertion-ordered command registration. */
public final class VerbCatalog {

    private final Map<String, VerbDefinition> verbs;

    public VerbCatalog(Collection<VerbDefinition> registrations) {
        Objects.requireNonNull(registrations, "registrations");

        Map<String, VerbDefinition> entries = new LinkedHashMap<>();
        for (VerbDefinition definition : registrations) {
            Objects.requireNonNull(definition, "definition");
            if (entries.putIfAbsent(definition.name(), definition) != null) {
                throw new IllegalArgumentException("duplicate verb name: " + definition.name());
            }
        }
        verbs = Collections.unmodifiableMap(entries);
    }

    /** Returns null for an unknown command name. */
    public VerbDefinition get(String name) {
        return verbs.get(name);
    }

    public Collection<VerbDefinition> all() {
        return verbs.values();
    }

    public Set<String> names() {
        return verbs.keySet();
    }
}