package com.archon.address;

import com.archon.model.*;

public sealed interface Resolved {

    sealed interface Target extends Resolved {
        Vec2 pos();
    }

    record OnEntity(Entity entity) implements Target {
        public Vec2 pos() { return entity.pos(); }
    }

    record OnBody(Actor actor, BodyPart part) implements Target {
        public Vec2 pos() { return actor.pos(); }
    }

    record OnItem(Item item, ItemLocation location) implements Target {
        public Vec2 pos() { return location.pos(); }
    }

    record OnLocation(ItemLocation location) implements Target {
        public Vec2 pos() { return location.pos(); }
    }

    record OnTile(Vec2 pos, Address.Layer layer) implements Target {}

    record Failure(String reason) implements Resolved {}

    default Entity targetEntity() {
        return switch (this) {
            case OnEntity(Entity entity) -> entity;
            case OnBody(Actor actor, var ignored) -> actor;
            default -> null;
        };
    }

    default boolean isFloorTile() {
        return this instanceof OnTile(var ignored, var layer) && layer != Address.Layer.CEILING;
    }

    default boolean isCeilingTile() {
        return this instanceof OnTile(var ignored, var layer) && layer == Address.Layer.CEILING;
    }

    static Resolved resolve(String raw, World world) {
        return resolve(Address.parse(raw), world);
    }

    static Resolved resolve(Address address, World world) {
        return switch (address) {
            case Address.Invalid(String reason) -> new Failure(reason);


            case Address.EntityAddr entity -> resolveEntity(entity, world);
            case Address.InventoryAddr inventory -> resolveInventory(world.thrall(), inventory.path());
            case Address.TileAddr tile -> resolveTile(tile, world);
        };
    }

    private static Resolved resolveEntity(Address.EntityAddr address, World world) {
        Entity entity = world.get(address.id());
        if (entity == null) {
            return new Failure("unknown entity \"" + address.id() + "\"");
        }
        if (!entity.alive()) {
            return new Failure(entity.id() + " is no longer alive");
        }

        return switch (address.path()) {
            case Address.RootPath ignored -> new OnEntity(entity);
            case Address.ContentsPath ignored when entity instanceof Prop prop ->
                    selection(prop.contents(), new ItemLocation.Contents(prop));
            case Address.BodyPath(BodyPart part) when entity instanceof Actor actor ->
                    new OnBody(actor, part);
            case Address.Path path when entity instanceof Actor actor ->
                    resolveInventory(actor, path);
            default -> new Failure("target does not support that path");
        };
    }

    private static Resolved resolveInventory(Actor actor, Address.Path path) {
        return switch (path) {
            case Address.PackPath(String query) -> query == null
                    ? new OnLocation(new ItemLocation.Pack(actor))
                    : actor.inventory().find(query)
                    .<Resolved>map(item -> new OnItem(item, new ItemLocation.Pack(actor)))
                    .orElseGet(() -> new Failure("no packed item \"" + query + "\" on " + actor.id()));
            case Address.SlotPath(EquipmentSlot slot) ->
                    selection(actor.inventory().equipped(slot), new ItemLocation.Equipment(actor, slot));
            default -> new Failure("target does not support that path");
        };
    }

    private static Resolved resolveTile(Address.TileAddr address, World world) {
        return switch (address.spec()) {
            case Address.AbsoluteTile(int x, int y) -> bounded(x, y, address.layer(), world);
            case Address.RelativeTile(Vec2 dir, int dist) -> bounded(
                    world.thrall().pos().x() + (long) dir.x() * dist,
                    world.thrall().pos().y() + (long) dir.y() * dist,
                    address.layer(), world);
            case Address.AnchorTile(String id) -> {
                Entity anchor = id.equalsIgnoreCase("self") ? world.thrall() : world.get(id);
                if (anchor == null) {
                    yield new Failure("unknown tile reference \"" + id + "\"; use @x,y, @self, @e2, or @entityId");
                }
                if (!anchor.alive()) {
                    yield new Failure(anchor.id() + " is no longer alive");
                }
                yield bounded(anchor.pos().x(), anchor.pos().y(), address.layer(), world);
            }
        };
    }

    private static Resolved bounded(long x, long y, Address.Layer layer, World world) {
        if (x < 0 || y < 0 || x >= world.width() || y >= world.height()) {
            return new Failure("tile (" + x + "," + y + ") is outside the map");
        }
        return new OnTile(new Vec2((int) x, (int) y), layer);
    }

    private static Resolved selection(Item item, ItemLocation location) {
        return item == null ? new OnLocation(location) : new OnItem(item, location);
    }
}