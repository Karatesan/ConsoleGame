package com.archon.address;

import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Vec2;
import com.archon.model.World;

public sealed interface Resolved {

    record OnEntity(Entity entity, BodyPart part) implements Resolved {}

    record OnItem(Item item, String container) implements Resolved {}

    record OnTile(Vec2 pos, String layer) implements Resolved {}

    /** Returns null when the address cannot currently be resolved. */
    static Resolved resolve(Address address, World world) {
        return switch (address) {
            case Address.TileAddr tile -> {
                Vec2 pos = Address.resolveTileSpec(tile.spec(), world);
                yield world.inBounds(pos) ? new OnTile(pos, tile.layer()) : null;
            }
            case Address.InventoryAddr inventory ->
                    resolveInventory(world.thrall, inventory.path(), null);
            case Address.EntityAddr entityAddress -> {
                Entity entity = world.get(entityAddress.id());
                if (entity == null || !entity.alive()) {
                    yield null;
                }
                if (EquipmentSlot.parse(path).isPresent())
                    yield new OnEntity(w.thrall, null, path);
                yield null;
            }
            case Address.EntityAddr e -> {
                Entity ent = w.get(e.id());
                if (ent == null || !ent.alive()) yield null;
                if (e.path() == null) yield new OnEntity(ent, null, null);
                if (ent instanceof Prop prop && e.path().equals("contents"))
                    yield new OnItem(prop.contents(), ent.id + "/contents");
                if (ent instanceof Actor actor) {
                    if (e.path().startsWith("pack")) {
                        String rest = e.path().length() > 4 ? e.path().substring(5) : "";
                        if (rest.isBlank()) yield new OnItem(null, ent.id + "/pack");
                        Item it = actor.inventory().findInPack(rest);
                        yield it == null ? null : new OnItem(it, ent.id + "/pack");
                    }
                    if (e.path().startsWith("hand/") || EquipmentSlot.parse(e.path()).isPresent())
                        yield new OnEntity(actor, null, e.path());
                }

                BodyPart part = BodyPart.parse(path);
                yield part == null ? null : new OnEntity(entity, part);
            }
        };
    }

    private static Resolved resolveInventory(Actor actor, String path, String ownerId) {
        if (actor == null || path == null) {
            return null;
        }

        if (path.equals("pack")) {
            return new OnItem(null, ownerId == null ? "pack" : ownerId + "/pack");
        }

        if (path.startsWith("pack/")) {
            Item item = actor.inventory().find(path.substring("pack/".length())).orElse(null);
            return item == null ? null : new OnItem(item, ownerId == null ? "pack" : ownerId + "/pack");
        }

        return EquipmentSlot.parse(path)
                .map(slot -> new OnItem(
                        actor.inventory().equipped(slot),
                        ownerId == null ? "self/" + slot.path : ownerId + "/" + slot.path
                ))
                .orElse(null);
    }
}