package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.*;

public final class InspectVerb implements Verb {

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved resolved = VerbHelpers.resolve(c, c.inv.arg(0));
        switch (resolved) {
            case Resolved.Failure failure -> {
                c.say(failure.reason());
                return ExitCode.INVALID;
            }
            case Resolved.OnEntity target -> inspectEntity(c, target.entity(), null);
            case Resolved.OnBody target -> inspectEntity(c, target.actor(), target.part());
            case Resolved.OnItem target -> inspectItem(c, target.item());
            case Resolved.OnLocation target -> c.say(
                    target.location() instanceof ItemLocation.Pack pack
                            ? pack.owner().id() + "/pack — " + pack.owner().inventory().pack()
                            : "nothing there"
            );
            case Resolved.OnTile target -> {
                GameMap.Tile tile = c.world.tile(target.pos());
                c.say(target.layer() == Address.Layer.CEILING
                        ? target.pos() + " ceiling — tags " + tile.ceiling()
                        : target.pos() + " " + (tile.isWall() ? "WALL" : "floor")
                        + " — tags " + tile.tags()
                        + (tile.ground().isEmpty() ? "" : ", ground " + tile.ground()));
            }
        }
        return ExitCode.SUCCESS;
    }

    private void inspectEntity(VerbContext c, Entity entity, BodyPart focus) {
        StringBuilder output = new StringBuilder(entity.name() + " \"" + entity.id()
                + "\" — HP " + entity.hp() + "/" + entity.maxHp()
                + ", Armor " + entity.armor());
        output.append("\n  Tags: ").append(entity.tags());

        if (entity instanceof Actor actor) {
            if (actor.mainHand() != null) {
                output.append("\n  Holding: ").append(actor.mainHand().name());
            }
            if (actor.readied() != null && !actor.isReadiedSpent()) {
                output.append("\n  READIED: ").append(actor.readied().description())
                        .append(" (").append(actor.readied().damage()).append(" dmg)");
            }
            for (BodyPart part : BodyPart.values()) {
                if (focus != null && focus != part) continue;
                output.append(String.format(
                        "%n  %-6s %3d%%  x%.1f%s",
                        part.path,
                        Math.max(5, 70 + part.hitMod - entity.evasion()),
                        part.damageMult,
                        Boolean.TRUE.equals(actor.crippled().get(part)) ? " CRIPPLED" : ""
                ));
            }
        }
        c.say(output.toString());
    }

    private void inspectItem(VerbContext c, Item item) {
        c.say(item.name() + " — tags " + item.tags()
                + (item.substance() == null ? "" : ", contains " + item.substance()));
    }
}