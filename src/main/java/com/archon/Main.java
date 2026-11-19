package com.archon;

import com.archon.app.Repl;

public final class Main {
    public static void main(String[] args) {
        long seed = System.currentTimeMillis();
        boolean magicArena = true;
        for (String arg : args) {
            if ("classic".equalsIgnoreCase(arg) || "--classic".equalsIgnoreCase(arg)) {
                magicArena = false;
            } else {
                seed = Long.parseLong(arg);
            }
        }
        new Repl().run(seed, magicArena);
    }
}