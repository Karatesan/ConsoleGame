package com.archon.command;

import java.util.LinkedHashMap;
import java.util.Map;

public record FlagSpec(String name, String shortName, boolean takesValue, String bareDefault) {

    public static final Map<String, FlagSpec> BY_NAME = new LinkedHashMap<>();
    public static final Map<String, FlagSpec> BY_SHORT = new LinkedHashMap<>();

    private static void def(String n, String s, boolean v, String bare) {
        FlagSpec f = new FlagSpec(n, s, v, bare);
        BY_NAME.put(n, f);
        if (s != null) BY_SHORT.put(s, f);
    }

    static {
        def("aim",     "a", true,  null);
        def("power",   "p", true,  "heavy");
        def("force",   "f", false, "");
        def("careful", "c", false, "");
        def("quiet",   "q", false, "");
        def("dry-run", "n", false, "");
        def("verbose", "v", false, "");
        def("all",     null, false, "");
        def("layer",   null, true,  null);
        def("range",   null, true,  null);
    }
}