package com.archon.verb;

import com.archon.model.Balance;
import com.archon.model.Tag;
import com.archon.system.physics.ThermalState;

/** Tab 4B/4C Channel focus buffer (thermal store/release, -f force focus, -b Blood Surge). */
public final class ChannelVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        if (c.inv.arg(0) != null) {
            return Check.invalid("channel takes no target operand");
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        if (c.inv.hasFlag("blood")) {
            if (c.thrall.bloodSurgedThisRound) {
                return c.rejectBlocked("blood surge already used this round");
            }
            if (!c.payBloodCost("Blood Surge", Balance.HP_COST_CHANNEL_BLOOD)) {
                return ExitCode.SUCCESS;
            }
            c.thrall.bloodSurgedThisRound = true;
            if (c.round != null) {
                c.round.gain(Balance.BLOOD_SURGE_AP);
                c.round.resetLinesUsed();
            }
            c.say("Thrall surges with forbidden blood (+" + Balance.BLOOD_SURGE_AP + " AP, tax reset).");
            c.flushLog();
            return ExitCode.SUCCESS;
        }

        if (c.inv.hasFlag("force")) {
            if (c.pipedOutput) {
                c.materialOut = new Material.OfSubstance(Tag.FORCE);
                c.say("Thrall focuses telekinetic force.");
            } else {
                c.thrall.channeledPayload = Tag.FORCE;
                c.thrall.channelTurns = Balance.CHANNEL_MAX_TURNS;
                c.say("Thrall stores telekinetic force in the focus buffer.");
            }
            return ExitCode.SUCCESS;
        }

        if (c.pipedInput) {
            if (!(c.materialIn instanceof Material.OfSubstance(Tag sub) && (sub == Tag.FIRE || sub == Tag.INFERNO))) {
                return c.rejectBlocked("channel can only store thermal payloads (heat or inferno)");
            }
            c.thrall.channeledPayload = sub;
            c.thrall.channelTurns = Balance.CHANNEL_MAX_TURNS;
            c.say("Thrall channels " + ThermalState.payloadLabel(sub) + ".");
            return ExitCode.SUCCESS;
        }

        if (c.thrall.channeledPayload == null) {
            return c.rejectBlocked("nothing channeled in focus buffer");
        }
        Tag released = c.thrall.channeledPayload;
        c.thrall.channeledPayload = null;
        c.thrall.channelTurns = 0;
        c.materialOut = new Material.OfSubstance(released);
        c.say("Thrall releases channeled " + ThermalState.payloadLabel(released) + ".");
        return ExitCode.SUCCESS;
    }
}
