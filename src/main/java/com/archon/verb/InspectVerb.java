package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.World;

public final class InspectVerb extends FreeVerb {
    @Override public String name() { return "inspect"; }
    @Override public String help() { return "inspect <address> — tags, HP, hit locations. 0 AP."; }

    @Override public ExitCode execute(VerbContext c) {
        String address = c.inv.arg(0);
        if (address == null) {
            c.say("inspect what?");
            return ExitCode.INVALID;
        }

        Resolved resolved = VerbHelpers.resolve(c, address);
        if (resolved == null) {
            c.say("cannot perceive " + address);
            return ExitCode.INVALID;
        }

        switch (resolved) {
            case Resolved.OnEntity onEntity -> inspectEntity(c, onEntity.entity());
            case Resolved.OnItem onItem -> inspectItem(c, onItem.item());
            case Resolved.OnTile onTile -> {
                World.Tile tile = c.world.tile(onTile.pos());
                c.say(onTile.pos() + " " + (tile.wall ? "WALL" : "floor") + " — tags " + tile.tags
                        + (tile.ground.isEmpty() ? "" : ", ground " + tile.ground));
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
        c.say(item == null
                ? "nothing there"
                : item.name() + " — tags " + item.tags()
                        + (item.substance() == null ? "" : ", contains " + item.substance()));
    }
}