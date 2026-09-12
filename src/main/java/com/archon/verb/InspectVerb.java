package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Prop;
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
            case Resolved.OnActor oa -> {
                Actor actor = oa.actor();
                StringBuilder sb = new StringBuilder(
                        actor.getName() + " \"" + actor.getId() + "\" — HP " + actor.getHp() + "/" + actor.getMaxHp() + ", Armor " + actor.getArmor());
                sb.append("\n  Tags: ").append(actor.getTags());
                if (actor.getHeld() != null) sb.append("\n  Holding: ").append(actor.getHeld().getName());
                if (actor.getReadied() != null && !actor.isReadiedSpent())
                    sb.append("\n  READIED: ").append(actor.getReadied().description())
                            .append(" (").append(actor.getReadied().damage()).append(" dmg)");
                for (BodyPart p : BodyPart.values())
                    sb.append(String.format("%n  %-6s %3d%%  x%.1f", p.path,
                            Math.max(5, 70 + p.hitMod - actor.getEvasion()), p.damageMult));
                c.say(sb.toString());
            }
            case Resolved.OnProp op -> {
                Prop prop = op.prop();
                StringBuilder sb = new StringBuilder(
                        prop.getName() + " \"" + prop.getId() + "\" — HP " + prop.getHp() + "/" + prop.getMaxHp() + ", Armor " + prop.getArmor());
                sb.append("\n  Tags: ").append(prop.getTags());
                c.say(sb.toString());
            }
            case Resolved.OnItem oi -> c.say(oi.item() == null ? "nothing there"
                    : oi.item().getName() + " — tags " + oi.item().getTags()
                    + (oi.item().getSubstance() != null ? ", contains " + oi.item().getSubstance() : ""));
            case Resolved.OnTile ot -> {
                World.Tile t = c.world.tile(ot.pos());
                c.say(ot.pos() + " " + (t.wall ? "WALL" : "floor") + " — tags " + t.tags
                        + (t.ground.isEmpty() ? "" : ", ground " + t.ground));
            }
        }
        return ExitCode.SUCCESS;
    }
}