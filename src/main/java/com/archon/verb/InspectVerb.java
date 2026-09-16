package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.address.Resolved.BodyTarget;
import com.archon.address.Resolved.EmptyEquipmentSlot;
import com.archon.address.Resolved.EmptyPropContents;
import com.archon.address.Resolved.EntityTarget;
import com.archon.address.Resolved.EquippedItem;
import com.archon.address.Resolved.PackRoot;
import com.archon.address.Resolved.PackedItem;
import com.archon.address.Resolved.PropContents;
import com.archon.address.Resolved.TileTarget;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;

public final class InspectVerb extends FreeVerb {
    @Override public String name() { return "inspect"; }
    @Override public String help() { return "inspect <address> — tags, HP, hit locations. 0 AP."; }

    @Override public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0);
        if (address == null) {
            c.say("inspect what?");
            return ExitCode.INVALID;
        }

        Resolution resolution = VerbHelpers.resolve(c, address);
        if (resolution.resolved() == null) {
            c.say("cannot perceive " + address);
            return ExitCode.INVALID;
        }

        switch (resolution.resolved()) {
            case EntityTarget entityTarget -> inspectEntity(c, entityTarget.entity());
            case BodyTarget bodyTarget -> inspectEntity(c, bodyTarget.entity());
            case PackedItem packedItem -> inspectItem(c, packedItem.item());
            case EquippedItem equippedItem -> inspectItem(c, equippedItem.item());
            case PropContents propContents -> inspectItem(c, propContents.item());
            case PackRoot packRoot -> c.say("pack root");
            case EmptyEquipmentSlot emptyEquipmentSlot -> c.say("nothing there");
            case EmptyPropContents emptyPropContents -> c.say("nothing there");
            case TileTarget tileTarget -> {
                GameMap.Tile tile = c.world.tile(tileTarget.pos());
                c.say(tileTarget.pos() + " " + (tile.isWall() ? "WALL" : "floor") + " — tags " + tile.tags()
                        + (tile.ground().isEmpty() ? "" : ", ground " + tile.ground()));
            }
        }

        return ExitCode.SUCCESS;
    }

    private void inspectEntity(VerbContext c, Entity entity) {
        StringBuilder output = new StringBuilder(entity.name() + " \"" + entity.id() + "\" — HP "
                + entity.hp() + "/" + entity.maxHp() + ", Armor " + entity.armor());

        output.append("\n  Tags: ").append(entity.tags());

        if (entity instanceof Actor actor) {
            if (actor.mainHand() != null) {
                output.append("\n  Holding: ").append(actor.mainHand().name());
            }

            if (actor.readied() != null && !actor.isReadiedSpent()) {
                output.append("\n  READIED: ")
                        .append(actor.readied().description())
                        .append(" (")
                        .append(actor.readied().damage())
                        .append(" dmg)");
            }
        }

        for (BodyPart part : BodyPart.values()) {
            output.append(String.format(
                    "%n  %-6s %3d%%  x%.1f",
                    part.path,
                    Math.max(5, 70 + part.hitMod - entity.evasion()),
                    part.damageMult
            ));
        }

        c.say(output.toString());
    }

    private void inspectItem(VerbContext c, Item item) {
        c.say(item.name() + " — tags " + item.tags()
                + (item.substance() == null ? "" : ", contains " + item.substance()));
    }
}
