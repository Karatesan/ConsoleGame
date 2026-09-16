package com.archon.model;

public final class Thrall extends Actor {
    public Thrall(Vec2 pos, int hp) {
        super("self", "Thrall", 'T', pos, new CreatureStats(hp, 0, 0, 3));
        with(Tag.ORGANIC, Tag.FLESH, Tag.FLAMMABLE);
        identify();
    }
}