package com.archon.verb;

import java.util.List;

/**
 * Immutable documentation entry for a command verb.
 */
public record VerbDoc(
        String name,
        String summary,
        String category,
        String synopsis,
        String apCostDesc,
        String description,
        List<FlagDoc> flags,
        boolean acceptsMaterial,
        boolean producesMaterial,
        List<String> examples
) {
    public record FlagDoc(String flag, String description) {}
}
