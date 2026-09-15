package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.World;

public final class InspectVerb extends FreeVerb {
    @Override public String name() { return "inspect"; }
    @Override public String help() { return "inspect <address> — tags, HP, hit locations. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        String a = c.inv.arg(0);
        if (a == null) {
            c.say("inspect what?");
            return ExitCode.INVALID;
        }

        Resolved r = VerbHelpers.resolve(c, a);
        if (r == null) {
            c.say("cannot perceive " + a);
            return ExitCode.INVALID;
        }

        switch (r) {
            case Resolved.OnEntity oe -> {
                var entity = oe.entity();
                StringBuilder sb = new StringBuilder(
                        entity.name() + " \"" + entity.id() + "\" — HP " + entity.hp() + "/" + entity.maxHp()
                                + ", Armor " + entity.armor());
                sb.append("\n  Tags: ").append(entity.tags());

                if (entity instanceof Actor actor && actor.mainHand() != null) {
                    sb.append("\n  Holding: ").append(actor.mainHand().name());
                }

                if (entity.readied() != null && !entity.isReadiedSpent()) {
                    sb.append("\n  READIED: ").append(entity.readied().description())
                            .append(" (").append(entity.readied().damage()).append(" dmg)");
                }

                for (BodyPart p : BodyPart.values()) {
                    sb.append(String.format(
                            "%n  %-6s %3d%%  x%.1f",
                            p.path,
                            Math.max(5, 70 + p.hitMod - entity.evasion()),
                            p.damageMult));
                }

                c.say(sb.toString());
            }
            case Resolved.OnItem oi -> {
                var item = oi.item();
                c.say(item == null
                        ? "nothing there"
                        : item.name() + " — tags " + item.tags()
                                + (item.substance() != null ? ", contains " + item.substance() : ""));
            }
            case Resolved.OnTile ot -> {
                World.Tile t = c.world.tile(ot.pos());
                c.say(ot.pos() + " " + (t.wall ? "WALL" : "floor") + " — tags " + t.tags
                        + (t.ground.isEmpty() ? "" : ", ground " + t.ground));
            }
        }

        return ExitCode.SUCCESS;
    }
}