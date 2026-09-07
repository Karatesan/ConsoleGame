package com.archon;

import com.archon.app.Repl;

public final class Main {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1234L;
        new Repl().run(seed);
    }
}