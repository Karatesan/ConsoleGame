package com.archon.verb;

import com.archon.address.Address;
import com.archon.address.Resolved;
import com.archon.model.*;

import java.util.Optional;

final class TargetDescriptions {
    private TargetDescriptions() {}

    static String inspect(World world, Resolved.Target target) {
        return switch (target) {
            case Resolved.OnEntity entity -> entity(entity.entity(), null);
            case Resolved.OnBody body -> entity(body.actor(), body.part());
            case Resolved.OnItem selected -> item(selected.item());
            case Resolved.OnLocation location ->
                    location.location() instanceof ItemLocation.Pack pack ? pack.owner().id() + "/pack — " + pack.owner().inventory().pack() : "nothing there";
            case Resolved.OnTile tile -> tile(world, tile);
        };
    }

    static Optional<String> list(World world, Resolved.Target target) {
        String listing = switch (target) {
            case Resolved.OnLocation location when location.location() instanceof ItemLocation.Pack pack ->
                    pack.owner().inventory().isPackEmpty() ? "(empty)" : String.join("\n",
                            pack.owner().inventory().pack().stream().map(
                                    item -> "  " + item.id() + "  " + item.tags()).toList());
            case Resolved.OnLocation ignored -> "(empty)";
            case Resolved.OnItem item -> "  " + item.item().id() + "  " + item.item().tags();
            case Resolved.OnTile tile -> tile.layer() == Address.Layer.CEILING ? "ceiling — tags " + world.tile(
                    tile.pos()).ceiling() : world.tile(tile.pos()).ground().toString();
            default -> null;
        };
        return Optional.ofNullable(listing);
    }

    private static String entity(Entity entity, BodyPart focus) {
        StringBuilder output = new StringBuilder(
                entity.name() + " \"" + entity.id() + "\" — HP " + entity.hp() + "/" + entity.maxHp() + ", Armor " + entity.armor());
        output.append("\n  Tags: ").append(entity.tags());

        if (entity instanceof Actor actor) {
            if (actor.mainHand() != null) {
                output.append("\n  Holding: ").append(actor.mainHand().name());
            }
            if (actor.readied() != null && !actor.isReadiedSpent()) {
                output.append("\n  READIED: ").append(actor.readied().description()).append(" (").append(
                        actor.readied().damage()).append(" dmg)");
            }
            for (BodyPart part : BodyPart.values()) {
                if (focus != null && focus != part) continue;
                output.append(String.format("%n  %-6s aim %+d  melee x%.1f%s", part.path, part.hitMod, part.damageMult,
                        Boolean.TRUE.equals(actor.crippled().get(part)) ? " CRIPPLED" : ""));
            }
        }
        return output.toString();
    }

    private static String item(Item item) {
        return item.name() + " — tags " + item.tags() + (item.substance() == null ? "" : ", contains " + item.substance());
    }

    private static String tile(World world, Resolved.OnTile target) {
        GameMap.Tile tile = world.tile(target.pos());
        return target.layer() == Address.Layer.CEILING ? target.pos() + " ceiling — tags " + tile.ceiling() : target.pos() + " " + (tile.isWall() ? "WALL" : "floor") + " — tags " + tile.tags() + (tile.ground().isEmpty() ? "" : ", ground " + tile.ground());
    }
}