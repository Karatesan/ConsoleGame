package com.archon.verb;

import com.archon.address.Resolution;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;

public final class InspectVerb extends FreeVerb {
    @Override
    public String name() {
        return "inspect";
    }

    @Override
    public String help() {
        return "inspect <address> — tags, HP, hit locations. 0 AP.";
    }

    @Override
    public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0);
        if (address == null) {
            c.say("inspect what?");
            return ExitCode.INVALID;
        }

        Resolution resolution = VerbHelpers.resolve(c, address);
        if (!(resolution instanceof Resolution.Resolved resolved)) {
            c.say("cannot perceive " + address);
            return ExitCode.INVALID;
        }

        switch (resolved) {
            case Resolution.Resolved.EntityTarget entityTarget -> inspectEntity(c, entityTarget.entity());
            case Resolution.Resolved.BodyTarget bodyTarget -> inspectEntity(c, bodyTarget.entity());
            case Resolution.Resolved.PackedItem packedItem -> inspectItem(c, packedItem.item());
            case Resolution.Resolved.EquippedItem equippedItem -> inspectItem(c, equippedItem.item());
            case Resolution.Resolved.PropContents propContents -> inspectItem(c, propContents.item());
            case Resolution.Resolved.PackRoot packRoot -> c.say("pack root");
            case Resolution.Resolved.EmptyEquipmentSlot emptyEquipmentSlot -> c.say("nothing there");
            case Resolution.Resolved.EmptyPropContents emptyPropContents -> c.say("nothing there");
            case Resolution.Resolved.TileTarget tileTarget -> {
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