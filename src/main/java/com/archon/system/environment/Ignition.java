package com.archon.system.environment;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileReactions;
import com.archon.system.spatial.SpatialService;

import java.util.List;
import java.util.Objects;

/** Eligibility and effects of direct ignition, separate from periodic fire spread. */
public final class Ignition {

    private Ignition() {}

    public static boolean hasFlame(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        Item left = actor.inventory().equipped(EquipmentSlot.HAND_LEFT);
        Item right = actor.inventory().equipped(EquipmentSlot.HAND_RIGHT);
        return (left != null && left.has(Tag.LIT)) || (right != null && right.has(Tag.LIT));
    }

    public static boolean entity(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entity.has(Tag.WET)) {
            entity.removeTag(Tag.WET);
            entity.wetTurns = 0;
            return false;
        }
        if (entity.has(Tag.FROZEN)) {
            ThermalState.exitFrozen(entity);
            return false;
        }
        if (!entity.has(Tag.FLAMMABLE) && !entity.has(Tag.OIL)) {
            return false;
        }
        ThermalState.igniteEntity(entity);
        return true;
    }

    public static boolean tile(World world, Vec2 at) {
        return tile(world, at, null);
    }

    public static boolean tile(World world, Vec2 at, List<String> log) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(at, "position");

        GameMap.Tile tile = world.map().tile(at);
        if (tile == null) {
            throw new IllegalArgumentException("Position is outside the map: " + at);
        }
        if (tile.has(Tag.WATER)) {
            world.map().removeTag(at, Tag.WATER);
            world.map().removeTag(at, Tag.LIQUID);
            world.map().removeTag(at, Tag.CONDUCTIVE);
            return false;
        }
        if (tile.has(Tag.OIL_MIST)) {
            TileReactions.detonateOilMist(world, null, at, false, null, log);
            return true;
        }
        boolean litItems = TileReactions.igniteGroundItems(tile, log);
        if (!tile.has(Tag.OIL) && !tile.has(Tag.TAR) && !tile.has(Tag.FLAMMABLE)) {
            return litItems;
        }
        if (tile.has(Tag.ICE)) {
            world.map().removeTag(at, Tag.ICE);
            ThermalState.spawnGas(world, at, Tag.STEAM);
        }

        ThermalState.igniteTile(world, at);
        Entity occupant = SpatialService.entityAt(world, at);
        if (occupant != null) {
            entity(occupant);
        }
        return true;
    }
}