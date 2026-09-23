package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.ItemLocation;

final class VerbTargets {
    private VerbTargets() {}

    static Check resolution(Resolved resolved) {
        if (!(resolved instanceof Resolved.Failure failure)) {
            return Check.ok();
        }

        return switch (failure.problem()) {
            case BAD_SYNTAX, INVALID_TILE ->
                    Check.invalid(failure.reason(), null);
            case UNKNOWN_ENTITY, DEAD_ENTITY, ITEM_NOT_FOUND, UNSUPPORTED_PATH, OUT_OF_BOUNDS ->
                    Check.blocked(failure.reason(), null);
        };
    }

    static Check reachable(ValidationContext c, Resolved.Target target) {
        return target.pos().chebyshev(c.thrall.pos()) <= 1
                ? Check.ok()
                : Check.blocked(
                "target at " + target.pos() + " out of reach",
                "step closer first"
        );
    }

    static boolean ownedBy(ItemLocation location, Actor actor) {
        return switch (location) {
            case ItemLocation.Pack pack -> pack.owner() == actor;
            case ItemLocation.Equipment equipment -> equipment.owner() == actor;
            case ItemLocation.Contents ignored -> false;
            case ItemLocation.Ground ignored -> false;
        };
    }

    static ExitCode reject(VerbContext c, Check check) {
        c.say(check.reason());
        return check.code();
    }
}