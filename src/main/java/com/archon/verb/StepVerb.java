package com.archon.verb;

import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.spatial.SpatialService;

import static com.archon.model.Balance.*;

public final class StepVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        String direction = c.inv.arg(0);
        if (direction == null) {
            return Check.invalid("step needs a direction", "n s e w ne nw se sw");
        }
        if (Vec2.dir(direction) == null) {
            return Check.invalid("unknown direction \"" + direction + "\"", "n s e w ne nw se sw");
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

        boolean oiled = c.world.tile(origin).has(Tag.OIL) || c.world.tile(destination).has(Tag.OIL);
        c.thrall.moveTo(destination);

        if (c.world.tile(destination).has(Tag.WATER)) {
            c.thrall.extinguish();
            c.thrall.applyTag(Tag.WET);
        }

        if (c.inv.hasFlag("careful")) {
            if (c.world.dice().chance(Balance.clampChance(CAREFUL_STEP_BASE_CHANCE + c.thrall.evasion()))) {
                c.say("Thrall steps carefully to " + destination + ".");
                return ExitCode.SUCCESS;
            }
            c.world.markThrallMoved();
            c.say("Thrall steps to " + destination + ", leaving an opening.");
            return ExitCode.SUCCESS;
        }

        c.world.markThrallMoved();
        if (oiled && c.world.dice().chance(OIL_SLIP_CHANCE)) {
            c.thrall.applyTag(Tag.PRONE);
            c.thrall.setGuarded(false);
            c.thrall.clearReadied();
            c.say("Thrall steps to " + destination + " and slips on the oil, falling prone!");
            return ExitCode.SUCCESS;
        }

        c.say("Thrall advances to " + destination + ".");
        return ExitCode.SUCCESS;
    }
}