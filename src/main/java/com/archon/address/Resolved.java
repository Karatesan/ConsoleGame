package com.archon.address;

import com.archon.model.*;

public sealed interface Resolved {

    record OnEntity(Entity entity, BodyPart part, String slot) implements Resolved {}
    record OnItem(Item item, String container) implements Resolved {}
    record OnTile(Vec2 pos, String layer) implements Resolved {}

    /** Returns null when the address cannot currently be resolved. */
    static Resolved resolve(Address a, World w) {
        return switch (a) {
            case Address.TileAddr t -> {
                Vec2 p = Address.resolveTileSpec(t.spec(), w);
                yield w.inBounds(p) ? new OnTile(p, t.layer()) : null;
            }
            case Address.InventoryAddr inv -> {
                String path = inv.path();
                if (path.startsWith("pack")) {
                    String rest = path.length() > 4 ? path.substring(5) : "";
                    if (rest.isBlank()) yield new OnItem(null, "pack");
                    Item it = w.thrall.findInPack(rest);
                    yield it == null ? null : new OnItem(it, "pack");
                }
                if (w.thrall.slots().containsKey(path))
                    yield new OnEntity(w.thrall, null, path);
                yield null;
            }
            case Address.EntityAddr e -> {
                Entity ent = w.get(e.id());
                if (ent == null || !ent.alive()) yield null;
                if (e.path() == null) yield new OnEntity(ent, null, null);
                if (e.path().equals("contents")) yield new OnItem(ent.held, ent.id + "/contents");
                if (e.path().startsWith("hand/")) {
                    if (ent == w.thrall) yield new OnEntity(ent, null, e.path());
                    yield ent.held == null ? null : new OnItem(ent.held, ent.id + "/" + e.path());
                }
                BodyPart bp = BodyPart.parse(e.path());
                yield bp == null ? null : new OnEntity(ent, bp, null);
            }
        };
    }
}