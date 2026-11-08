package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.command.Ast;
import com.archon.model.*;
import com.archon.system.spatial.SpatialService;

import java.util.List;

public class ValidationContext {

    public final World world;
    public final Thrall thrall;
    public final Ast.Invocation inv;
    public final boolean pipedInput;

    public ValidationContext(World world, Ast.Invocation inv, boolean pipedInput) {
        this.world = world;
        this.thrall = world.thrall();
        this.inv = inv;
        this.pipedInput = pipedInput;
    }

    public Check checkAddress(int index) {
        String raw = inv.arg(index);
        if (raw == null) {
            return Check.invalid(inv.verb() + " needs an address");
        }
        return checkRawAddress(raw);
    }

    public Check checkOptionalAddress(int index) {
        return inv.arg(index) == null ? Check.ok() : checkAddress(index);
    }

    public Check checkAimedAddress(boolean optional) {
        String aim = inv.flag("aim");
        if (aim != null && BodyPart.parse(aim) == null) {
            return Check.invalid("unknown hit location \"" + aim + "\"", "valid: head, torso, arm.l, arm.r, legs");
        }
        return optional ? checkOptionalAddress(0) : checkAddress(0);
    }

    public Check checkItem(int index) {
        if (pipedInput) return Check.ok();
        String raw = inv.arg(index);
        return raw == null || raw.isBlank() ? Check.invalid("an item is required") : checkRawAddress(itemAddress(raw));
    }

    public Check checkDelivery() {
        Check target = checkAddress(pipedInput ? 0 : 1);
        return target.valid() ? checkItem(0) : target;
    }

    public static String itemAddress(String raw) {
        return raw.startsWith("@") || raw.contains("/") ? raw : "/pack/" + raw;
    }

    private Check checkRawAddress(String raw) {
        return resolve(raw) instanceof Resolved.Failure(String reason, boolean syntax) && syntax
                ? Check.invalid(reason)
                : Check.ok();
    }

    public Resolved resolve(String raw) {
        return Resolved.resolve(raw, world);
    }

    public boolean isCarried(ItemLocation location) {
        return switch (location) {
            case ItemLocation.Pack pack -> pack.owner() == thrall;
            case ItemLocation.Equipment equipment -> equipment.owner() == thrall;
            case ItemLocation.Contents ignored -> false;
            case ItemLocation.Ground ignored -> false;
        };
    }

    public String soleAdjacentHostile() {
        List<Entity> hostiles = SpatialService.hostilesAdjacentTo(world, thrall.pos());
        return hostiles.size() == 1 ? hostiles.getFirst().id() : null;
    }

    public BodyPart aimPart(Resolved resolved) {
        String aim = inv.flag("aim");
        if (aim != null) {
            return BodyPart.parse(aim);
        }
        return resolved instanceof Resolved.OnBody body ? body.part() : BodyPart.TORSO;
    }
}