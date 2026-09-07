package com.archon.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Thrall extends Entity {
    public static final int PACK_MAX = 8;
    public static final List<String> SLOTS =
            List.of("head", "torso", "legs", "hand/left", "hand/right");

    public final Map<String, Item> slots = new LinkedHashMap<>();
    public final List<Item> pack = new ArrayList<>();
    public boolean nocked;
    public int strength = 3;

    public Thrall(Vec2 pos, int hp) {
        super("self", "Thrall", 'T', Kind.CREATURE, pos, hp);
        SLOTS.forEach(s -> slots.put(s, null));
        tags.add(Tag.ORGANIC); tags.add(Tag.FLESH); tags.add(Tag.FLAMMABLE);
        identified = true;
    }

    public Item mainHand() {
        Item r = slots.get("hand/right");
        return r != null ? r : slots.get("hand/left");
    }

    public boolean packFull() { return pack.size() >= PACK_MAX; }

    public Item findInPack(String idOrName) {
        return pack.stream()
                .filter(i -> i.id.equalsIgnoreCase(idOrName) || i.name.equalsIgnoreCase(idOrName))
                .findFirst().orElse(null);
    }
}