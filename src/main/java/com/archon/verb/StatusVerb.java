package com.archon.verb;

import com.archon.model.BodyPart;
import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.Thrall;

public final class StatusVerb implements Verb {

    @Override
    public ExitCode execute(VerbContext c) {
        Thrall t = c.thrall;
        String channel = t.channeledPayload == null
                ? "none"
                : t.channeledPayload + " (" + t.channelTurns + "t)";
        String durations = InspectVerb.formatDurations(t);
        String crippled = crippledParts(t);
        c.say("HP " + t.hp() + "/" + t.maxHp()
                + "  Magic " + t.magic
                + "  Temp " + t.temp + "C"
                + "  ThermalMass " + t.thermalMass
                + "  Channel: " + channel
                + "\n  tags " + t.tags()
                + (durations.isEmpty() ? "" : "\n  durations: " + durations)
                + (crippled.isEmpty() ? "" : "\n  crippled: " + crippled)
                + "\n  hand/right: " + slot(t, EquipmentSlot.HAND_RIGHT)
                + "\n  hand/left : " + slot(t, EquipmentSlot.HAND_LEFT)
                + "\n  pack (" + t.inventory().pack().size() + "/" + Inventory.PACK_MAX + "): " + t.inventory().pack()
                + "\n  weaponState: " + (t.isNocked() ? "NOCKED" : "READY"));
        return ExitCode.SUCCESS;
    }

    private String crippledParts(Thrall t) {
        StringBuilder sb = new StringBuilder();
        BodyPart[] parts = BodyPart.values();
        for (int i = 0; i < parts.length; i++) {
            if (t.isCrippled(parts[i])) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(parts[i].path);
            }
        }
        return sb.toString();
    }

    private String slot(Thrall t, EquipmentSlot slot) {
        Item item = t.inventory().equipped(slot);
        return item == null ? "empty" : item.name();
    }
}