package com.archon.system.environment;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.CombatEngine;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Handles cleanup, drops, and environmental effects when an entity is destroyed or killed.
 */
public final class Destruction {

    public record Result(
            List<String> messages,
            Tag spilledSubstance,
            boolean droppedItem,
            boolean ignitedTile
    ) {
        public static final Result EMPTY = new Result(List.of(), null, false, false);
    }

    private Destruction() {}

    public static Result onKilled(World world, Entity entity) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(entity, "entity");

        Vec2 pos = entity.pos();
        List<String> messages = new ArrayList<>();
        Tag spilledSubstance = null;
        boolean droppedItem = false;
        boolean ignitedTile = false;

        // 1. Spills and drops from container props
        if (entity instanceof Prop prop && prop.contents() != null) {
            Item item = prop.removeContents();
            if (item.substance() != null) {
                spilledSubstance = item.consumeSubstance();
                Substances.spill(world, pos, spilledSubstance);
                String sub = spilledSubstance.name().toLowerCase(Locale.ROOT);
                messages.add(Character.toUpperCase(sub.charAt(0)) + sub.substring(1) + " spills across the floor.");
            } else {
                world.map().placeGroundItem(pos, item);
                droppedItem = true;
                messages.add("Something drops onto the floor.");
            }
        }

        // 2. Fire and thermal reactions (braziers, burning props, or torches)
        if (entity.has(Tag.LIT) || entity.has(Tag.BURNING)) {
            GameMap.Tile tile = world.map().tile(pos);
            if (tile != null) {
                if (tile.has(Tag.WATER)) {
                    world.map().removeTag(pos, Tag.WATER);
                    messages.add("The embers are doused by water.");
                } else {
                    world.map().addTag(pos, Tag.BURNING);
                    ignitedTile = true;
                    messages.add("Fire takes hold of the floor.");
                    Entity occupant = SpatialService.entityAt(world, pos);
                    if (occupant != null && occupant.alive()) {
                        Ignition.entity(occupant);
                    }
                }
            }
        }

        // 3. Creature-specific drops (equipped weapon)
        if (entity instanceof Actor actor) {
            CombatEngine.dropMainHand(world, actor);
        }

        return new Result(Collections.unmodifiableList(messages), spilledSubstance, droppedItem, ignitedTile);
    }
}
