package com.archon.address;

import com.archon.model.*;

public sealed interface Resolved {

    record OnEntity(Entity entity, BodyPart part) implements Resolved {}
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

                if (path.equals("pack") || path.startsWith("pack/")) {
                    String rest = path.equals("pack") ? "" : path.substring("pack/".length());
                    if (rest.isBlank()) {
                        yield new OnItem(null, "pack");
                    }

                    Item item = w.thrall.inventory().find(rest);
                    yield item == null ? null : new OnItem(item, "pack");
                }

                var slot = EquipmentSlot.parse(path);
                if (slot.isPresent()) {
                    EquipmentSlot equipmentSlot = slot.get();
                    yield new OnItem(
                            w.thrall.inventory().equipped(equipmentSlot),
                            "self/" + equipmentSlot.path()
                    );
                }

                yield null;
            }
            case Address.EntityAddr e -> {
                Entity entity = w.get(e.id());
                if (entity == null || !entity.alive()) {
                    yield null;
                }

                String path = e.path();
                if (path == null) {
                    yield new OnEntity(entity, null);
                }

                if (entity instanceof Prop prop && path.equals("contents")) {
                    yield new OnItem(prop.contents(), entity.id() + "/contents");
                }

                if (entity instanceof Actor actor) {
                    if (path.equals("pack") || path.startsWith("pack/")) {
                        String rest = path.equals("pack") ? "" : path.substring("pack/".length());
                        if (rest.isBlank()) {
                            yield new OnItem(null, entity.id() + "/pack");
                        }

                        Item item = actor.inventory().find(rest);
                        yield item == null ? null : new OnItem(item, entity.id() + "/pack");
                    }

                    var slot = EquipmentSlot.parse(path);
                    if (slot.isPresent()) {
                        EquipmentSlot equipmentSlot = slot.get();
                        yield new OnItem(
                                actor.inventory().equipped(equipmentSlot),
                                entity.id() + "/" + equipmentSlot.path()
                        );
                    }
                }

                BodyPart part = BodyPart.parse(path);
                yield part == null ? null : new OnEntity(entity, part);
            }
        };
    }
}