package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.World;

public final class InspectVerb extends FreeVerb {
    @Override public String name() { return "inspect"; }
    @Override public String help() { return "inspect <address> — tags, HP, hit locations. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        String a = c.inv.arg(0);
        if (a == null) { c.say("inspect what?"); return ExitCode.INVALID; }
        Resolved r = VerbHelpers.resolve(c, a);
        if (r == null) { c.say("cannot perceive " + a); return ExitCode.INVALID; }
        switch (r) {
            case Resolved.OnEntity oe -> {
                Entity e = oe.entity();
                StringBuilder sb = new StringBuilder(
                        e.name + " \"" + e.id + "\" — HP " + e.hp + "/" + e.maxHp + ", Armor " + e.armor);
                sb.append("\n  Tags: ").append(e.tags);
                if (e.held != null) sb.append("\n  Holding: ").append(e.held.name);
                if (e.readied != null && !e.readiedSpent)
                    sb.append("\n  READIED: ").append(e.readied.description())
                            .append(" (").append(e.readied.damage()).append(" dmg)");
                for (BodyPart p : BodyPart.values())
                    sb.append(String.format("%n  %-6s %3d%%  x%.1f", p.path,
                            Math.max(5, 70 + p.hitMod - e.evasion), p.damageMult));
                c.say(sb.toString());
            }
            case Resolved.OnItem oi -> c.say(oi.item() == null ? "nothing there"
                    : oi.item().name + " — tags " + oi.item().tags
                    + (oi.item().substance != null ? ", contains " + oi.item().substance : ""));
            case Resolved.OnTile ot -> {
                World.Tile t = c.world.tile(ot.pos());
                c.say(ot.pos() + " " + (t.wall ? "WALL" : "floor") + " — tags " + t.tags
                        + (t.ground.isEmpty() ? "" : ", ground " + t.ground));
            }
        }
        return ExitCode.SUCCESS;
    }
}
