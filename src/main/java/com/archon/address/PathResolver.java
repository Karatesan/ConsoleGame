package com.archon.address;

import com.archon.model.*;

final class PathResolver {
    private PathResolver() {}

    static Resolved entity(Address.EntityAddr address, World world) {
        AddressSyntax.Path path = AddressSyntax.path(address.path(), true);
        if (path instanceof AddressSyntax.BadPath bad) {
            return new Resolved.Failure(Resolved.Problem.BAD_SYNTAX, bad.reason());
        }

        Entity entity = world.get(address.id());
        if (entity == null) {
            return new Resolved.Failure(Resolved.Problem.UNKNOWN_ENTITY, "unknown entity \"" + address.id() + "\"");
        }
        if (!entity.alive()) {
            return new Resolved.Failure(Resolved.Problem.DEAD_ENTITY, entity.id() + " is no longer alive");
        }

        return switch (path) {
            case AddressSyntax.RootPath ignored -> new Resolved.OnEntity(entity);
            case AddressSyntax.ContentsPath ignored -> entity instanceof Prop prop ? selection(prop.contents(),
                    new ItemLocation.Contents(prop)) : unsupported();
            case AddressSyntax.BodyPath body ->
                    entity instanceof Actor actor ? new Resolved.OnBody(actor, body.part()) : unsupported();
            default -> entity instanceof Actor actor ? inventory(actor, path) : unsupported();
        };
    }

    static Resolved inventory(Actor actor, String path) {
        return inventory(actor, AddressSyntax.path(path, false));
    }

    private static Resolved inventory(Actor actor, AddressSyntax.Path path) {
        return switch (path) {
            case AddressSyntax.PackPath pack -> pack.query() == null ? new Resolved.OnLocation(
                    new ItemLocation.Pack(actor)) : actor.inventory().find(pack.query()).<Resolved>map(
                    item -> new Resolved.OnItem(item, new ItemLocation.Pack(actor))).orElseGet(
                    () -> new Resolved.Failure(Resolved.Problem.ITEM_NOT_FOUND,
                            "no packed item \"" + pack.query() + "\" on " + actor.id()));
            case AddressSyntax.SlotPath slot ->
                    selection(actor.inventory().equipped(slot.slot()), new ItemLocation.Equipment(actor, slot.slot()));
            case AddressSyntax.BadPath bad -> new Resolved.Failure(Resolved.Problem.BAD_SYNTAX, bad.reason());
            default -> unsupported();
        };
    }

    private static Resolved selection(Item item, ItemLocation location) {
        return item == null ? new Resolved.OnLocation(location) : new Resolved.OnItem(item, location);
    }

    private static Resolved unsupported() {
        return new Resolved.Failure(Resolved.Problem.UNSUPPORTED_PATH, "target does not support that path");
    }
}