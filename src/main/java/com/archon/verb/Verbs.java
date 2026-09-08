package com.archon.verb;

import java.util.*;

/**
 * Verb registry.
 * Maps command verbs to their respective Verb implementations.
 */
public final class Verbs {

    private static final Map<String, Verb> REGISTRY = new LinkedHashMap<>();

    private static void reg(Verb v) {
        REGISTRY.put(v.name(), v);
    }

    static {
        // Free & query
        reg(new ScanVerb());
        reg(new InspectVerb());
        reg(new StatusVerb());
        reg(new LsVerb());
        reg(new HelpVerb());
        reg(new MapVerb());
        reg(new PassVerb());

        // Movement
        reg(new StepVerb());

        // Melee
        reg(new StrikeVerb());
        reg(new GuardVerb());

        // Ranged
        reg(new NockVerb());
        reg(new ShootVerb());

        // Item manipulation & pipeline
        reg(new TakeVerb());
        reg(new DropVerb());
        reg(new WieldVerb());
        reg(new ThrowVerb());

        // Environment & elemental
        reg(new PourVerb());
        reg(new SiphonVerb());
        reg(new IgniteVerb());
    }

    public static Verb get(String name) { return REGISTRY.get(name); }
    public static Collection<Verb> all() { return Collections.unmodifiableCollection(REGISTRY.values()); }
    public static Set<String> names() { return Collections.unmodifiableSet(REGISTRY.keySet()); }

    private Verbs() {}
}