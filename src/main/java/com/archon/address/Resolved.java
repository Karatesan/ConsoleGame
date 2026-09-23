package com.archon.address;

import com.archon.model.*;

import java.util.Objects;

public sealed interface Resolved {
    enum Problem {
        BAD_SYNTAX, UNKNOWN_ENTITY, DEAD_ENTITY, UNSUPPORTED_PATH, ITEM_NOT_FOUND, INVALID_TILE, OUT_OF_BOUNDS
    }

    sealed interface Target extends Resolved {
        Vec2 pos();
    }

    sealed interface ItemTarget extends Target {
        ItemLocation location();

        default Vec2 pos() {return location().pos();}
    }

    record OnEntity(Entity entity) implements Target {
        public OnEntity {Objects.requireNonNull(entity, "entity");}
        public Vec2 pos() {return entity.pos();}
    }

    record OnBody(Actor actor, BodyPart part) implements Target {
        public OnBody {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(part, "part");
        }
        public Vec2 pos() {return actor.pos();}
    }

    record OnItem(Item item, ItemLocation location) implements ItemTarget {
        public OnItem {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(location, "location");
        }
    }

    record OnLocation(ItemLocation location) implements ItemTarget {
        public OnLocation {Objects.requireNonNull(location, "location");}
    }

    record OnTile(Vec2 pos, Address.Layer layer) implements Target {
        public OnTile {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(layer, "layer");
        }
    }

    record Failure(Problem problem, String reason) implements Resolved {
        public Failure {
            Objects.requireNonNull(problem, "problem");
            Objects.requireNonNull(reason, "reason");
        }
    }

    static Resolved resolve(Address address, World world) {
        Objects.requireNonNull(world.thrall(), "install a thrall before resolving addresses");
        return switch (Objects.requireNonNull(address, "address")) {
            case Address.Invalid invalid -> new Failure(Problem.BAD_SYNTAX, invalid.reason());
            case Address.EntityAddr entity -> PathResolver.entity(entity, world);
            case Address.InventoryAddr inventory -> PathResolver.inventory(world.thrall(), inventory.path());
            case Address.TileAddr tile -> TileResolver.resolve(tile, world);
        };
    }
}