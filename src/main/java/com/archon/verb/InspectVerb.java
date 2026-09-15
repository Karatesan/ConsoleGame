package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.Vec2;
import com.archon.model.World;

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

        Resolved resolved = VerbHelpers.resolve(c, address);
        if (resolved == null) {
            c.say("cannot perceive " + address);
            return ExitCode.INVALID;
        }

        switch (resolved) {
            case Resolved.OnEntity onEntity -> inspectEntity(c, onEntity.entity());
            case Resolved.OnItem onItem -> inspectItem(c, onItem.item());
            case Resolved.OnTile onTile -> inspectTile(c, onTile.pos());
        }

        return ExitCode.SUCCESS;
    }

    private static void inspectItem(VerbContext c, Item item) {
        if (item == null) {
            c.say("nothing there");
            return;
        }

        c.say(item.name() + " — tags " + item.tags()
                + (item.substance() != null ? ", contains " + item.substance() : ""));
    }

    private static void inspectTile(VerbContext c, Vec2 pos) {
        World.Tile tile = c.world.tile(pos);
        c.say(pos + " " + (tile.wall ? "WALL" : "floor") + " — tags " + tile.tags
                + (tile.ground.isEmpty() ? "" : ", ground " + tile.ground));
    }

    private static void inspectEntity(VerbContext c, Entity entity) {
        StringBuilder output = new StringBuilder(
                entity.name() + " \"" + entity.id() + "\" — HP " + entity.hp() + "/" + entity.maxHp()
                        + ", Armor " + entity.armor());

        output.append("\n  Tags: ").append(entity.tags());

        if (entity instanceof Actor actor && actor.mainHand() != null) {
            output.append("\n  Holding: ").append(actor.mainHand().name());
        }

        if (entity.readied() != null && !entity.isReadiedSpent()) {
            output.append("\n  READIED: ").append(entity.readied().description())
                    .append(" (").append(entity.readied().damage()).append(" dmg)");
        }

        for (BodyPart part : BodyPart.values()) {
            output.append(String.format(
                    "%n  %-6s %3d%%  x%.1f",
                    part.path(),
                    Math.max(5, 70 + part.hitMod() - entity.evasion()),
                    part.damageMult()));
        }

        c.say(output.toString());
    }
}