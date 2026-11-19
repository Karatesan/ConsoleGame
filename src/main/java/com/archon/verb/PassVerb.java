package com.archon.verb;

import com.archon.model.Balance;
import com.archon.model.Tag;
import com.archon.system.physics.ThermalState;

public final class PassVerb implements Verb {

    @Override
    public ExitCode execute(VerbContext c) {
        if (c.inv.hasFlag("roll")) {
            if (c.world.tile(c.thrall.pos()).has(Tag.BURNING)) {
                return c.rejectBlocked("cannot smother flames on a burning tile");
            }
            boolean wasBurning = c.thrall.has(Tag.BURNING);
            ThermalState.applyProne(c.thrall);
            if (wasBurning) {
                c.thrall.extinguish();
                c.thrall.removeTag(Tag.OIL);
                c.thrall.oilTurns = 0;
                c.thrall.temp = Balance.BODY_TEMP;
                ThermalState.syncThermalState(c.world, c.thrall, c.log);
                c.say("Thrall drops and rolls to smother the flames!");
                c.flushLog();
            } else {
                c.say("Thrall drops prone.");
            }
            return ExitCode.SUCCESS;
        }
        c.say("Thrall falls still.");
        return ExitCode.SUCCESS;
    }
}