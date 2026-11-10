package com.archon.model;

public final class Thrall extends Actor {
    public Thrall(Vec2 pos, int hp) {
        super("self", "Thrall", 'T', pos, new CreatureStats(hp, 0, 0, Balance.DEFAULT_STRENGTH));
        with(Tag.ORGANIC, Tag.FLESH);
        identify();
    }
}