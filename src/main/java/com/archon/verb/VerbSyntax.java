package com.archon.verb;

import com.archon.address.AddressSyntax;
import com.archon.model.BodyPart;

final class VerbSyntax {
    private VerbSyntax() {}

    static Check address(ValidationContext c, int index) {
        String raw = c.inv.arg(index);
        if (raw == null) {
            return Check.invalid(c.inv.verb() + " needs an address", null);
        }
        return address(raw);
    }

    static Check optionalAddress(ValidationContext c, int index) {
        return c.inv.arg(index) == null ? Check.ok() : address(c, index);
    }

    static Check item(ValidationContext c, int index) {
        if (c.pipedInput) {
            return Check.ok();
        }
        String raw = c.inv.arg(index);
        return raw == null || raw.isBlank()
                ? Check.invalid("an item is required", null)
                : address(itemAddress(raw));
    }

    static Check delivery(ValidationContext c) {
        Check target = address(c, c.pipedInput ? 0 : 1);
        return target.valid() ? item(c, 0) : target;
    }

    static Check aimedAddress(ValidationContext c, boolean optional) {
        String aim = c.inv.flag("aim");
        if (aim != null && BodyPart.parse(aim) == null) {
            return Check.invalid("unknown hit location \"" + aim + "\"",
                    "valid: head, torso, arm.l, arm.r, legs");
        }
        return optional ? optionalAddress(c, 0) : address(c, 0);
    }

    static String itemAddress(String raw) {
        return raw.startsWith("@") || raw.contains("/") ? raw : "/pack/" + raw;
    }

    private static Check address(String raw) {
        return AddressSyntax.problem(raw)
                .map(reason -> Check.invalid(reason, null))
                .orElseGet(Check::ok);
    }
}