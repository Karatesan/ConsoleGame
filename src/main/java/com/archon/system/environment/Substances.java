package com.archon.system.environment;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileReactions;
import com.archon.system.spatial.SpatialService;

import java.util.List;
import java.util.Objects;

/** Immediate finite extraction, container consumption, and substance application. */
public final class Substances {

    private Substances() {}

    /**
     * Extracts the existing portion from an authoritative target, or returns null when empty.
     * Entity sources use an actor's main hand or a prop's contents.
     */
    public static Tag extract(World world, Resolved.Target source) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(source, "source");

        Item item = sourceItem(source);
        if (item != null) {
            return item.consumeSubstance();
        }

        if (source instanceof Resolved.OnTile) {
            GameMap.Tile tile = requireDestination(world, source.pos());
            Tag substance = tile.has(Tag.OIL) ? Tag.OIL
                    : tile.has(Tag.ACID) ? Tag.ACID
                    : tile.has(Tag.WATER) ? Tag.WATER
                    : ThermalState.gasTag(tile);
            if (substance != null) {
                if (substance == Tag.STEAM || substance == Tag.SUPERHEATED_STEAM || substance == Tag.OIL_MIST) {
                    ThermalState.clearGas(world, source.pos());
                } else {
                    if (!world.map().removeTag(source.pos(), substance)) {
                        throw new IllegalStateException("selected substance disappeared during extraction");
                    }
                    if (substance == Tag.OIL) {
                        world.map().removeTag(source.pos(), Tag.FLAMMABLE);
                        tile.oilTurns = 0;
                    } else if (substance == Tag.WATER) {
                        world.map().removeTag(source.pos(), Tag.CONDUCTIVE);
                    }
                    if (!tile.has(Tag.WATER) && !tile.has(Tag.OIL) && !tile.has(Tag.ACID)) {
                        world.map().removeTag(source.pos(), Tag.LIQUID);
                    }
                }
            }
            return substance;
        }

        return null;
    }

    /** Keeps the container and returns the consumed substance, or null when empty. */
    public static Tag pour(World world, Vec2 at, Item container) {
        return pour(world, at, container, null);
    }

    public static Tag pour(World world, Vec2 at, Item container, List<String> log) {
        return pour(world, at, container, false, log);
    }

    public static Tag pour(World world, Vec2 at, Item container, boolean floorOnly, List<String> log) {
        Objects.requireNonNull(container, "container");
        requireDestination(world, at);

        Tag substance = container.consumeSubstance();
        if (substance != null) {
            TileReactions.spillSubstance(world, at, substance, floorOnly, log);
        }
        return substance;
    }

    public static void spill(World world, Vec2 at, Tag substance) {
        spill(world, at, substance, false, null);
    }

    public static void spill(World world, Vec2 at, Tag substance, List<String> log) {
        spill(world, at, substance, false, log);
    }

    public static void spill(World world, Vec2 at, Tag substance, boolean floorOnly, List<String> log) {
        Objects.requireNonNull(substance, "substance");
        requireDestination(world, at);
        TileReactions.spillSubstance(world, at, substance, floorOnly, log);
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
        return switch (source) {
            case Resolved.OnItem(Item item, var ignored) -> item;
            case Resolved.OnEntity(Actor actor) -> actor.mainHand();
            case Resolved.OnEntity(Prop prop) -> prop.contents();
            default -> null;
        };
    }
}