package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.EquipmentSlot;
import com.archon.model.Inventory;
import com.archon.model.Item;
import com.archon.model.ItemLocation;

import java.util.Objects;

sealed interface ItemInput {
    record Selected(Resolved.OnItem target) implements ItemInput { }
    record Rejected(Check check) implements ItemInput { }

    static ItemInput select(VerbContext c, int argIndex) {
        if (c.materialIn instanceof Material.OfItem material) {
            Item item = Objects.requireNonNull(material.item(), "piped item");
            Inventory inventory = c.thrall.inventory();

            if (inventory.pack().stream().anyMatch(candidate -> candidate == item)) {
                return new Selected(new Resolved.OnItem(
                        item, new ItemLocation.Pack(c.thrall)
                ));
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (inventory.equipped(slot) == item) {
                    return new Selected(new Resolved.OnItem(
                            item, new ItemLocation.Equipment(c.thrall, slot)
                    ));
                }
            }
            return new Rejected(Check.blocked("piped item is no longer carried", null));
        }

        if (c.acceptsPipedMaterial()) {
            return new Rejected(Check.invalid("this action requires item material", null));
        }

        String raw = c.inv.arg(argIndex);
        if (raw == null || raw.isBlank()) {
            return new Rejected(Check.invalid("an item is required", null));
        }

        String address = raw.startsWith("@") || raw.contains("/")
                ? raw
                : "/pack/" + raw;
        Resolved resolved = VerbHelpers.resolve(c, address);
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) {
            return new Rejected(resolution);
        }
        if (resolved instanceof Resolved.OnLocation) {
            return new Rejected(Check.blocked("address does not select an item", null));
        }
        if (!(resolved instanceof Resolved.OnItem item)) {
            return new Rejected(Check.invalid("expected an item address", null));
        }
        if (!VerbTargets.ownedBy(item.location(), c.thrall)) {
            return new Rejected(Check.blocked("not carrying that item", "take it first"));
        }

        return new Selected(item);
    }
}