package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.ItemLocation;
import com.archon.system.item.ItemMoves;

sealed interface ItemInput {
    record Selected(Resolved.OnItem target) implements ItemInput {}

    record Rejected(Check check) implements ItemInput {}

    static ItemInput select(VerbContext c, int argIndex) {
        if (c.materialIn instanceof Material.OfItem material) {
            ItemLocation location = ItemMoves.carriedLocation(c.thrall, material.item());
            return location == null ? new Rejected(
                    Check.blocked("piped item is no longer carried", null)) : new Selected(
                    new Resolved.OnItem(material.item(), location));
        }

        if (c.pipedInput) {
            throw new IllegalStateException("item selection received non-item material");
        }

        String raw = c.inv.arg(argIndex);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("item operand was not validated");
        }

        Resolved resolved = VerbHelpers.resolve(c, VerbSyntax.itemAddress(raw));
        Check resolution = VerbTargets.resolution(resolved);
        if (!resolution.valid()) {
            return new Rejected(resolution);
        }
        if (resolved instanceof Resolved.OnLocation) {
            return new Rejected(Check.blocked("address does not select an item", null));
        }
        if (!(resolved instanceof Resolved.OnItem item)) {
            return new Rejected(Check.blocked("expected an item address", null));
        }
        if (!VerbTargets.ownedBy(item.location(), c.thrall)) {
            return new Rejected(Check.blocked("not carrying that item", "take it first"));
        }
        if (ItemMoves.checkSource(c.world.map(), item.item(), item.location()) != ItemMoves.Result.SUCCESS) {
            return new Rejected(Check.blocked("item no longer at its source", null));
        }

        return new Selected(item);
    }
}