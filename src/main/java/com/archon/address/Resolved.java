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
    record OnEntity(Entity entity, BodyPart part) implements Resolved { }

    record OnItem(Item item, String container) implements Resolved { }

    record OnTile(Vec2 pos, String layer) implements Resolved { }

    static Resolved resolve(Address address, World world) {
        return switch (address) {
            case Address.TileAddr tile -> {
                Vec2 pos = Address.resolveTileSpec(tile.spec(), world);
                yield world.inBounds(pos) ? new OnTile(pos, tile.layer()) : null;
            }
            case Address.InventoryAddr inventory -> resolveInventory(world.thrall(), inventory.path(), "self");
            case Address.EntityAddr entityAddress -> resolveEntity(entityAddress, world);
        };
    }

    private static Resolved resolveEntity(Address.EntityAddr address, World world) {
        Entity entity = world.get(address.id());
        if (entity == null || !entity.alive()) {
            return null;
        }

        String path = address.path();
        if (path == null) {
            return new OnEntity(entity, null);
        }

        if (entity instanceof Prop prop && path.equals("contents")) {
            return new OnItem(prop.contents(), entity.id() + "/contents");
        }

        if (entity instanceof Actor actor) {
            Resolved inventory = resolveInventory(actor, path, entity.id());
            if (inventory != null) {
                return inventory;
            }
        }

        BodyPart part = BodyPart.parse(path);
        return part == null ? null : new OnEntity(entity, part);
    }

    private static Resolved resolveInventory(Actor actor, String path, String ownerId) {
        if (path == null) {
            return null;
        }

        if (path.equals("pack")) {
            return new OnItem(null, ownerId.equals("self") ? "pack" : ownerId + "/pack");
        }

        if (path.startsWith("pack/")) {
            Item item = actor.inventory().find(path.substring("pack/".length())).orElse(null);
            return item == null
                    ? null
                    : new OnItem(item, ownerId.equals("self") ? "pack" : ownerId + "/pack");
        }

        return EquipmentSlot.parse(path)
                .<Resolved>map(slot -> new OnItem(actor.inventory().equipped(slot), ownerId + "/" + slot.path))
                .orElse(null);
    }
}