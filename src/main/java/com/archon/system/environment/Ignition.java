package com.archon.system.environment;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.spatial.SpatialService;

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
            return false;
        }
        if (!entity.has(Tag.FLAMMABLE)) {
            return false;
        }
        entity.ignite();
        return true;
    }

    public static boolean tile(World world, Vec2 at) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(at, "position");

        GameMap.Tile tile = world.map().tile(at);
        if (tile == null) {
            throw new IllegalArgumentException("Position is outside the map: " + at);
        }
        if (tile.has(Tag.WATER)) {
            world.map().removeTag(at, Tag.WATER);
            return false;
        }
        if (!tile.has(Tag.OIL) && !tile.has(Tag.FLAMMABLE)) {
            return false;
        }

        world.map().addTag(at, Tag.BURNING);
        Entity occupant = SpatialService.entityAt(world, at);
        if (occupant != null) {
            entity(occupant);
        }
        return true;
    }
}