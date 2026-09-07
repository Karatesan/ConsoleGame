package com.archon.model;

public record Vec2(int x, int y) {
    public Vec2 plus(Vec2 o) { return new Vec2(x + o.x, y + o.y); }
    public int chebyshev(Vec2 o) { return Math.max(Math.abs(x - o.x), Math.abs(y - o.y)); }

    /** Returns null if not a known direction token. */
    public static Vec2 dir(String d) {
        return switch (d.toLowerCase()) {
            case "n"  -> new Vec2(0, -1);
            case "s"  -> new Vec2(0, 1);
            case "e"  -> new Vec2(1, 0);
            case "w"  -> new Vec2(-1, 0);
            case "ne" -> new Vec2(1, -1);
            case "nw" -> new Vec2(-1, -1);
            case "se" -> new Vec2(1, 1);
            case "sw" -> new Vec2(-1, 1);
            default   -> null;
        };
    }

    @Override public String toString() { return "(" + x + "," + y + ")"; }
}