package com.archon.address;

import com.archon.model.*;

public sealed interface Resolved {

    final record OnEntity(Entity entity, BodyPart part) implements Resolved {}
    final record OnItem(Item item, String container) implements Resolved {}
    final record OnTile(Vec2 pos, String layer) implements Resolved {}

    /** Returns null when the address cannot currently be resolved. */
    static Resolved resolve(Address address, World world) {
        return switch (address) {
            case Address.TileAddr tile -> {
                Vec2 pos = Address.resolveTileSpec(tile.spec(), world);
                yield world.inBounds(pos) ? new OnTile(pos, tile.layer()) : null;
            }
            case Address.InventoryAddr inventory -> resolveInventory(world.thrall, inventory.path(), null);
            case Address.EntityAddr entityAddress -> {
                Entity entity = world.get(entityAddress.id());
                if (entity == null || !entity.alive()) {
                    yield null;
                }

                String path = entityAddress.path();
                if (path == null) {
                    yield new OnEntity(entity, null);
                }

                if (entity instanceof Prop prop && path.equals("contents")) {
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

        if (path.equals("pack") || path.startsWith("pack/")) {
            String itemPath = path.equals("pack") ? "" : path.substring("pack/".length());
            String container = ownerId == null ? "pack" : ownerId + "/pack";

            if (itemPath.isBlank()) {
                return new OnItem(null, container);
            }

            Item item = actor.inventory().find(itemPath);
            return item == null ? null : new OnItem(item, container);
        }

        var slot = EquipmentSlot.parse(path);
        if (slot.isEmpty()) {
            return null;
        }

        String container = ownerId == null ? path : ownerId + "/" + path;
        return new OnItem(actor.inventory().equipped(slot.get()), container);
    }
}