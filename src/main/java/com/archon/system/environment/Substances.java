package com.archon.system.environment;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.item.ItemMoves;
import com.archon.system.spatial.SpatialService;

import java.util.Objects;

/** Immediate finite extraction, container consumption, and substance application. */
public final class Substances {

    public sealed interface Extraction permits Portion, Unavailable {}

    public record Portion(Tag substance) implements Extraction {
        public Portion {
            Objects.requireNonNull(substance, "substance");
        }
    }

    public enum Unavailable implements Extraction {
        EMPTY, STALE_SOURCE
    }

    private Substances() {}

    /**
     * Extracts the existing portion from an authoritative target.
     * Entity sources use an actor's main hand or a prop's contents.
     */
    public static Extraction extract(World world, Resolved.Target source) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(source, "source");

        if (source instanceof Resolved.OnItem selected && ItemMoves.checkSource(world.map(), selected.item(),
                selected.location()) != ItemMoves.Result.SUCCESS) {
            return Unavailable.STALE_SOURCE;
        }

        Item item = sourceItem(source);
        if (item != null) {
            Tag substance = item.consumeSubstance();
            return substance == null ? Unavailable.EMPTY : new Portion(substance);
        }

        if (source instanceof Resolved.OnTile target && target.layer() != Address.Layer.CEILING) {
            GameMap.Tile tile = requireDestination(world, target.pos());
            Tag substance = tile.has(Tag.OIL) ? Tag.OIL : tile.has(Tag.WATER) ? Tag.WATER : null;
            if (substance == null) {
                return Unavailable.EMPTY;
            }
            if (!world.map().removeTag(target.pos(), substance)) {
                throw new IllegalStateException("selected substance disappeared during extraction");
            }
            return new Portion(substance);
        }

        return Unavailable.EMPTY;
    }

    /** Keeps the container and returns the consumed substance, or null when empty. */
    public static Tag pour(World world, Vec2 at, Item container) {
        Objects.requireNonNull(container, "container");
        requireDestination(world, at);

        Tag substance = container.consumeSubstance();
        if (substance != null) {
            apply(world, at, substance);
        }
        return substance;
    }

    public static void spill(World world, Vec2 at, Tag substance) {
        Objects.requireNonNull(substance, "substance");
        requireDestination(world, at);
        apply(world, at, substance);
    }

    /** Invalid coordinates are programmer errors and must be detected before consumption. */
    public static GameMap.Tile requireDestination(World world, Vec2 at) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(at, "position");

        GameMap.Tile tile = world.map().tile(at);
        if (tile == null) {
            throw new IllegalArgumentException("Position is outside the map: " + at);
        }
        return tile;
    }

    private static Item sourceItem(Resolved.Target source) {
        if (source instanceof Resolved.OnItem target) {
            return target.item();
        }
        if (source instanceof Resolved.OnEntity target) {
            if (target.entity() instanceof Actor actor) {
                return actor.mainHand();
            }
            if (target.entity() instanceof Prop prop) {
                return prop.contents();
            }
        }
        return null;
    }

    private static void apply(World world, Vec2 at, Tag substance) {
        GameMap map = world.map();
        map.addTag(at, Tag.LIQUID);
        map.addTag(at, substance);

        if (substance == Tag.OIL) {
            map.addTag(at, Tag.FLAMMABLE);
        }
        if (substance == Tag.WATER) {
            map.addTag(at, Tag.CONDUCTIVE);
        }

        Entity occupant = SpatialService.entityAt(world, at);
        if (occupant != null && substance == Tag.OIL) {
            occupant.applyTag(Tag.FLAMMABLE);
        }
    }
}