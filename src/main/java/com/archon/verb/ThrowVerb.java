// src/main/java/com/archon/verb/ThrowVerb.java
package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.environment.Substances;
import com.archon.system.item.ItemMoves;
import com.archon.system.physics.EntityReactions;
import com.archon.system.physics.TileReactions;
import com.archon.system.spatial.SpatialService;

public final class ThrowVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkDelivery();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target target = c.selectDeliveryTarget(true);
        if (target == null) return ExitCode.BLOCKED;
        Vec2 at = target.pos();
        boolean floorOnly = VerbContext.isFloorDelivery(target);

        Resolved.OnItem selected = c.selectCarriedItem(0);
        if (selected == null) return ExitCode.BLOCKED;

        Item item = selected.item();
        var result = ItemMoves.remove(c.world.map(), item, selected.location());
        if (result != ItemMoves.Result.SUCCESS) {
            return c.rejectBlocked(result.reason());
        }

        if (item.substance() != null || item.has(Tag.LIT)) {
            Vec2 gasPos = SpatialService.firstGasOnLine(c.world, c.thrall.pos(), at);
            if (gasPos != null) {
                at = gasPos;
                floorOnly = true;
            }
        }

        if (item.substance() != null) {
            c.say(item.name() + " arcs toward " + at + " and shatters.");
            Tag substance = Substances.pour(c.world, at, item, floorOnly, c.log);
            if (substance != null) {
                if (c.log.isEmpty()) c.describeSpill(at, substance);
                else c.flushLog();
            }
        } else if (item.has(Tag.LIT)) {
            c.say(item.name() + " arcs toward " + at + ".");
            TileReactions.applySparkToTile(c.world, c.thrall, item, at, null, c.log);
            Entity occ = SpatialService.entityAt(c.world, at);
            if (occ != null && item.has(Tag.LIT)) {
                EntityReactions.applySparkToEntity(c.world, c.thrall, item, occ, c.log);
            }
            c.flushLog();
            c.world.map().placeGroundItem(at, item);
        } else {
            c.say(item.name() + " arcs toward " + at + " and lands on the floor.");
            c.world.map().placeGroundItem(at, item);
        }
        return ExitCode.SUCCESS;
    }
}