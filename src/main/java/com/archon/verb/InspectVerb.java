package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolution;
import com.archon.address.Resolution.Resolved;
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
        Resolved target = VerbHelpers.found(resolution);
        if (target == null) {
            c.say("cannot perceive " + address);
            return ExitCode.INVALID;
        }

        switch (target) {
            case Resolved.EntityTarget entityTarget -> inspectEntity(c, entityTarget.entity());
            case Resolved.BodyTarget bodyTarget -> inspectEntity(c, bodyTarget.entity());
            case Resolved.PackedItem packedItem -> inspectItemTarget(c, packedItem.item());
            case Resolved.EquippedItem equippedItem -> inspectItemTarget(c, equippedItem.item());
            case Resolved.PropContents propContents -> inspectItemTarget(c, propContents.item());
            case Resolved.PackRoot ignored -> c.say("pack root");
            case Resolved.EmptyEquipmentSlot ignored -> c.say("nothing there");
            case Resolved.EmptyPropContents ignored -> c.say("nothing there");
            case Resolved.TileTarget tileTarget -> inspectTile(c, tileTarget);
        }

        return ExitCode.SUCCESS;
    }

    private void inspectTile(VerbContext c, Resolved.TileTarget tileTarget) {
        GameMap.Tile tile = c.world.tile(tileTarget.pos());

        if (tileTarget.layer() == Address.Layer.CEILING) {
            c.say(tileTarget.pos() + " ceiling — tags " + tile.ceiling());
            return;
        }

        StringBuilder output = new StringBuilder(tileTarget.pos() + " " + (tile.isWall() ? "WALL" : "floor")
                + " — tags " + tile.tags());

        if (!tile.ground().isEmpty()) {
            output.append(", ground ").append(tile.ground());
        }

        c.say(output.toString());
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

    private void inspectItemTarget(VerbContext c, Item item) {
        if (item == null) {
            c.say("nothing there");
            return;
        }

        inspectItem(c, item);
    }

    private void inspectItem(VerbContext c, Item item) {
        c.say(item.name() + " — tags " + item.tags()
                + (item.substance() == null ? "" : ", contains " + item.substance()));
    }
}