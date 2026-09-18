package com.archon.address;

import com.archon.model.*;

final class PathResolver {
    private PathResolver() { }

    static Resolved entity(Address.EntityAddr address, World world) {
        Entity entity = world.get(address.id());
        if (entity == null) {
            return new Resolved.Failure(
                    Resolved.Problem.UNKNOWN_ENTITY,
                    "unknown entity \"" + address.id() + "\""
            );
        }
        if (!entity.alive()) {
            return new Resolved.Failure(
                    Resolved.Problem.DEAD_ENTITY,
                    entity.id() + " is no longer alive"
            );
        }

        String path = address.path();
        if (path.isEmpty()) {
            return new Resolved.OnEntity(entity);
        }

        if (entity instanceof Prop prop && path.equals("contents")) {
            return selection(prop.contents(), new ItemLocation.Contents(prop));
        }

        if (entity instanceof Actor actor) {
            if (path.startsWith("body/")) {
                BodyPart part = BodyPart.parse(path.substring("body/".length()));
                return part == null
                        ? invalidPath(path)
                        : new Resolved.OnBody(actor, part);
            }
            return inventory(actor, path);
        }

        return invalidPath(path);
    }

    static Resolved inventory(Actor actor, String path) {
        if (path.equals("pack")) {
            return new Resolved.OnLocation(new ItemLocation.Pack(actor));
        }

        if (path.startsWith("pack/")) {
            String query = path.substring("pack/".length());
            if (query.isBlank()) {
                return invalidPath(path);
            }

            return actor.inventory().find(query)
                    .<Resolved>map(item ->
                            new Resolved.OnItem(item, new ItemLocation.Pack(actor)))
                    .orElseGet(() -> new Resolved.Failure(
                            Resolved.Problem.ITEM_NOT_FOUND,
                            "no packed item \"" + query + "\" on " + actor.id()
                    ));
        }

        EquipmentSlot slot = EquipmentSlot.parse(path).orElse(null);
        if (slot == null) {
            return invalidPath(path);
        }

        return selection(
                actor.inventory().equipped(slot),
                new ItemLocation.Equipment(actor, slot)
        );
    }

    private static Resolved selection(Item item, ItemLocation location) {
        return item == null
                ? new Resolved.OnLocation(location)
                : new Resolved.OnItem(item, location);
    }

    private static Resolved invalidPath(String path) {
        return new Resolved.Failure(
                Resolved.Problem.INVALID_PATH,
                "unknown target path \"" + path + "\""
        );
    }
}