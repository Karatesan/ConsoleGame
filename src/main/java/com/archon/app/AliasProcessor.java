package com.archon.app;

import com.archon.event.EventBus;

import java.util.HashMap;
import java.util.Map;

public class AliasProcessor {

    public static String ALIAS_PREFIX = "alias";
    private static final int MAX_ALIAS_EXPANSION_DEPTH = 5;

    private final Map<String, String> aliases = new HashMap<>();

    public void defineAlias(String raw, EventBus bus) {
        String body = raw.substring(6).trim();
        int eq = body.indexOf('=');
        if (eq < 0) {
            bus.narrate("usage: alias k=\"strike -p heavy\"");
            return;
        }
        String name = body.substring(0, eq).trim();
        String value = body.substring(eq + 1).trim().replaceAll("^\"|\"$", "");
        aliases.put(name, value);
        bus.narrate("alias " + name + " -> " + value);
    }

    /**
     * Expansion happens before parsing, so verb count and allocation are computed post-expansion.
     */
    public String expandAliases(String raw) {
        String out = raw;
        for (int depth = 0; depth < MAX_ALIAS_EXPANSION_DEPTH; depth++) {
            String[] parts = out.split("(?=\\s*(;|&&|\\|\\||\\|))");
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            for (String part : parts) {
                String trimmed = part.stripLeading();
                String lead = part.substring(0, part.length() - trimmed.length());
                String[] toks = trimmed.split("\\s+", 2);
                String head = toks.length > 0 ? toks[0].replaceAll("^(;|&&|\\|\\||\\|)\\s*", "") : "";
                String opPrefix = toks.length > 0 ? toks[0].substring(0, toks[0].length() - head.length()) : "";
                if (aliases.containsKey(head)) {
                    changed = true;
                    sb.append(lead).append(opPrefix).append(aliases.get(head));
                    if (toks.length > 1) sb.append(' ').append(toks[1]);
                } else sb.append(part);
            }
            out = sb.toString();
            if (!changed) break;
        }
        return out;
    }
}
