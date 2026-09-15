package com.archon.model;

public final class Thrall extends Actor {
    public Thrall(Vec2 pos, int hp) {
        super("self", "Thrall", 'T', pos, new CreatureStats(hp, 0, 0, 3));
        tags.add(Tag.ORGANIC);
        tags.add(Tag.FLESH);
        tags.add(Tag.FLAMMABLE);
        identified = true;
    }
}