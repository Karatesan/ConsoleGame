package com.archon.verb;

import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.GameMap;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileDisplacement;
import com.archon.system.spatial.SpatialService;

import static com.archon.model.Balance.*;

public final class StepVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        String direction = c.inv.arg(0);
        if (direction == null) {
            return Check.invalid("step needs a direction", "n s e w");
        }
        Vec2 dir = Vec2.dir(direction);
        if (dir == null || (dir.x() != 0 && dir.y() != 0)) {
            return Check.invalid("step moves cardinally only (\"" + direction + "\")", "n s e w");
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (c.thrall.has(Tag.PRONE)) {
            c.thrall.removeTag(Tag.PRONE);
            c.say("Thrall scrambles back to their feet.");
            return ExitCode.SUCCESS;
        }

        Vec2 origin = c.thrall.pos();
        Vec2 destination = origin.plus(Vec2.dir(c.inv.arg(0)));
        if (!SpatialService.passable(c.world, destination)) {
            c.say("blocked at " + destination);
            return ExitCode.BLOCKED;
        }

        if (c.thrall.isCrippled(BodyPart.LEGS) && !c.world.dice().chance(CRIPPLED_LEGS_MOVE_CHANCE)) {
            c.say("Crippled legs give way — Thrall fails to advance.");
            return ExitCode.MISS;
        }

        boolean careful = c.inv.hasFlag("careful");
        GameMap.Tile originTile = c.world.tile(origin);
        boolean originSlippery = originTile != null
                && (originTile.has(Tag.ICE) || (originTile.has(Tag.OIL) && !originTile.has(Tag.BURNING)));

        boolean slipped = TileDisplacement.onEnterTile(c.world, c.thrall, destination, !careful, c.log);

        if (careful) {
            if (c.world.dice().chance(Balance.clampChance(CAREFUL_STEP_BASE_CHANCE + c.thrall.evasion()))) {
                c.say("Thrall steps carefully to " + destination + ".");
            } else {
                c.world.markThrallMoved();
                c.say("Thrall steps to " + destination + ", leaving an opening.");
            }
            c.flushLog();
            return ExitCode.SUCCESS;
        }

        c.world.markThrallMoved();
        if (!slipped && originSlippery && c.thrall.alive() && !c.thrall.has(Tag.PRONE)
                && c.world.dice().chance(Balance.SLIP_CHANCE)) {
            ThermalState.applyProne(c.thrall);
            slipped = true;
        }
        c.say(slipped
                ? "Thrall steps to " + destination + " and slips, falling prone!"
                : "Thrall advances to " + destination + ".");
        for (int i = 0; i < c.log.size(); i++) {
            String msg = c.log.get(i);
            if (!(slipped && msg.endsWith(" slips and falls prone!"))) {
                c.say(msg);
            }
        }
        c.log.clear();
        return ExitCode.SUCCESS;
    }
}