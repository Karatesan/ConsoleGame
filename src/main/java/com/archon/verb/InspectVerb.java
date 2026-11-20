// src/main/java/com/archon/verb/InspectVerb.java
package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.*;

public final class InspectVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAddress(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target target = c.resolveTarget(c.inv.arg(0));
        if (target == null) return ExitCode.BLOCKED;

        c.say(formatInspect(c.world, target));
        return ExitCode.SUCCESS;
    }

    private static String formatInspect(World world, Resolved.Target target) {
        return switch (target) {
            case Resolved.OnEntity(Entity entity) -> formatEntity(entity, null);
            case Resolved.OnBody(Actor actor, BodyPart part) -> formatEntity(actor, part);
            case Resolved.OnItem(Item item, var ignored) ->
                    item.name() + " — tags " + item.tags()
                            + (item.maxDamage() > 0 ? ", dmg " + item.minDamage() + "-" + item.maxDamage() : "")
                            + (item.substance() == null ? "" : ", contains " + item.substance());
            case Resolved.OnLocation(ItemLocation.Pack(var owner)) ->
                    owner.id() + "/pack — " + owner.inventory().pack();
            case Resolved.OnLocation ignored -> "nothing there";
            case Resolved.OnTile tile -> formatTile(world, tile);
        };
    }

    private static String formatEntity(Entity entity, BodyPart focus) {
        StringBuilder out = new StringBuilder(entity.name() + " \"" + entity.id()
                + "\" — HP " + entity.hp() + "/" + entity.maxHp() + ", Armor " + entity.armor()
                + ", Temp " + entity.temp + "C, ThermalMass " + entity.thermalMass);
        if (entity instanceof Actor actor) {
            out.append(", Magic ").append(actor.magic).append(", Str ").append(actor.strength());
        }
        out.append("\n  Tags: ").append(entity.tags());
        String durations = formatDurations(entity);
        if (!durations.isEmpty()) {
            out.append("\n  Durations: ").append(durations);
        }

        if (entity instanceof Actor actor) {
            if (actor.channeledPayload != null) {
                out.append("\n  Channel: ").append(actor.channeledPayload)
                        .append(" (").append(actor.channelTurns).append(" turns)");
            }
            if (actor.mainHand() != null) {
                out.append("\n  Holding: ").append(actor.mainHand().name());
            }
            if (actor.isGuarded()) {
                out.append(String.format("\n  GUARDED: +%d%% armor, -%d%% melee hit chance (bypass with -f)",
                        Balance.GUARD_ARMOR_BONUS_PCT, Balance.GUARD_MELEE_HIT_PENALTY));
            }
            if (actor.readied() != null && !actor.isReadiedSpent()) {
                out.append("\n  READIED: ").append(actor.readied().description())
                        .append(" (").append(actor.readied().damage()).append(" dmg)");
            }
            for (BodyPart part : BodyPart.values()) {
                if (focus != null && focus != part) continue;
                out.append(String.format("%n  %-6s aim %+d  melee x%.1f  crit %+d%%%s",
                        part.path, part.hitMod, part.damageMult, part.critBonus,
                        actor.isCrippled(part) ? " CRIPPLED" : ""));
            }
        }
        return out.toString();
    }

    static String formatDurations(Entity e) {
        StringBuilder sb = new StringBuilder();
        appendDur(sb, "wet", e.wetTurns);
        appendDur(sb, "oil", e.oilTurns);
        appendDur(sb, "propBurn", e.propBurnTurns);
        appendDur(sb, "bleed", e.bleedTurns);
        appendDur(sb, "dazed", e.dazedTurns);
        if (e.numbArmTurns > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("numbArm(").append(e.numbArmPart != null ? e.numbArmPart.path : "?")
                    .append(")=").append(e.numbArmTurns).append('t');
        }
        appendDur(sb, "crippled", e.crippledTurns);
        appendDur(sb, "stun", e.stunTurns);
        appendDur(sb, "blindLinger", e.blindLingerTurns);
        return sb.toString();
    }

    private static void appendDur(StringBuilder sb, String name, int turns) {
        if (turns > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(name).append('=').append(turns).append('t');
        }
    }

    private static String formatTile(World world, Resolved.OnTile target) {
        GameMap.Tile tile = world.tile(target.pos());
        boolean hasGas = com.archon.system.spatial.SpatialService.hasGas(tile)
                || tile.gasTemp != Balance.AMBIENT_TEMP;
        return target.pos() + " " + (tile.isWall() ? "WALL" : "floor")
                + (target.layer() != null ? " [" + target.layer() + "]" : "")
                + " — tags " + tile.tags()
                + ", temp " + tile.temp + "C"
                + (hasGas ? ", gasTemp " + tile.gasTemp + "C" : "")
                + (tile.oilTurns > 0 ? ", oilTurns " + tile.oilTurns : "")
                + (tile.burnTurns > 0 ? ", burnTurns " + tile.burnTurns : "")
                + (tile.ground().isEmpty() ? "" : ", ground " + tile.ground());
    }
}