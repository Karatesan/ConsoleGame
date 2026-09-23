package com.archon.verb;

import com.archon.model.Vec2;
import com.archon.system.spatial.SpatialService;

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
        Vec2 destination = c.thrall.pos().plus(Vec2.dir(c.inv.arg(0)));
        if (!SpatialService.passable(c.world, destination)) {
            c.say("blocked at " + destination);
            return ExitCode.BLOCKED;
        }

        c.thrall.moveTo(destination);
        c.world.markThrallMoved();
        c.say("Thrall advances to " + destination + ".");
        return ExitCode.SUCCESS;
    }
}