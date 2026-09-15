package com.archon.address;

import com.archon.model.*;

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
            case Address.InventoryAddr inventory -> resolveInventory(world.thrall(), inventory.path(), null);
            case Address.EntityAddr entityAddress -> {
                Entity entity = world.get(entityAddress.id());
                if (entity == null || !entity.alive()) {
                    yield null;
                }

                String path = entityAddress.path();
                if (path == null) {
                    yield new OnEntity(entity, null);
                }

                if (entity instanceof Prop prop && path.equals("/contents")) {
                    yield new OnItem(prop.contents(), entity.id() + "/contents");
                }

                if (entity instanceof Actor actor) {
                    Resolved inventoryResolution = resolveInventory(actor, path, entity.id());
                    if (inventoryResolution != null) {
                        yield inventoryResolution;
                    }
                }

                BodyPart part = BodyPart.parse(path);
                yield part == null ? null : new OnEntity(entity, part);
            }
        };
    }

    private static Resolved resolveInventory(Actor actor, String path, String ownerId) {
        if (path == null) {
            return null;
        }

        if (path.equals("/pack") || path.startsWith("/pack/")) {
            String container = ownerId == null ? "pack" : ownerId + "/pack";
            if (path.equals("/pack")) {
                return new OnItem(null, container);
            }

            Item item = actor.inventory().find(path.substring("/pack/".length()));
            return item == null ? null : new OnItem(item, container);
        }

        return EquipmentSlot.parse(path)
                .map(slot -> new OnItem(
                        actor.inventory().equipped(slot),
                        ownerId == null ? slot.path() : ownerId + "/" + slot.path()
                ))
                .orElse(null);
    }
}