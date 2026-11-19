package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.combat.CombatNarrative;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileDisplacement;
import com.archon.system.spatial.SpatialService;

/** Telekinetic push and pull verb (Tab 3A/3B/3C): entities, props, gases, liquids, and loose items. */
public final class PushVerb implements Verb {

    private final boolean pull;

    public PushVerb(boolean pull) {
        this.pull = pull;
    }

    @Override
    public Check validateStructural(ValidationContext c) {
        Check addr = c.checkAddress(0);
        if (!addr.valid()) return addr;

        String distArg = c.inv.arg(1);
        if (distArg != null) {
            int n;
            try {
                n = Integer.parseInt(distArg);
            } catch (NumberFormatException ex) {
                return Check.invalid("distance must be a positive integer");
            }
            if (n <= 0) {
                return Check.invalid("distance must be positive");
            }
        }
        return Check.ok();
    }

    @Override
    public ExitCode execute(VerbContext c) {
        boolean forceFocused = false;
        if (c.materialIn != null) {
            if (!(c.materialIn instanceof Material.OfSubstance(Tag sub) && sub == Tag.FORCE)) {
                return c.rejectBlocked((pull ? "pull" : "push") + " only accepts piped FORCE");
            }
            forceFocused = true;
        }

        int force = c.thrall.magic + (forceFocused ? Balance.FORCE_FOCUS_BONUS : 0);

        Resolved.Target target = c.resolveTarget(c.inv.arg(0));
        if (target == null) return ExitCode.BLOCKED;

        Vec2 targetPos = target.pos();
        if (targetPos.equals(c.thrall.pos())) {
            return c.rejectBlocked("cannot push or pull self");
        }

        boolean blind = c.thrall.has(Tag.BLIND);
        int maxRange = blind ? Balance.BLIND_MAX_RANGE : Balance.PUSH_RANGE;
        int dist = Math.abs(targetPos.x() - c.thrall.pos().x()) + Math.abs(targetPos.y() - c.thrall.pos().y());
        if (c.thrall.pos().x() != targetPos.x() && c.thrall.pos().y() != targetPos.y()) {
            return c.rejectBlocked((pull ? "pull" : "push") + " requires cardinal line of fire");
        }
        if (dist > maxRange) {
            return c.rejectBlocked(blind
                    ? "blinded — cannot " + (pull ? "pull" : "push") + " beyond " + maxRange + " tile"
                    : "target out of range (max " + maxRange + ")");
        }
        if (!SpatialService.lineOfFire(c.world, c.thrall.pos(), targetPos)) {
            return c.rejectBlocked("no clear cardinal line of fire");
        }

        Vec2 ray = new Vec2(Integer.compare(targetPos.x(), c.thrall.pos().x()),
                Integer.compare(targetPos.y(), c.thrall.pos().y()));
        Vec2 moveDir = pull ? new Vec2(-ray.x(), -ray.y()) : ray;
        int requestedN = c.inv.arg(1) != null ? Integer.parseInt(c.inv.arg(1)) : force;

        GameMap.Tile tile = c.world.tile(targetPos);
        String layer = target instanceof Resolved.OnTile ot ? ot.layer() : null;
        boolean explicitGas = "gas".equals(layer) || "steam".equals(layer) || "oil_mist".equals(layer) || "caustic".equals(layer);
        boolean explicitFloor = "floor".equals(layer) || "water".equals(layer) || "oil".equals(layer) || "acid".equals(layer);
        boolean isGasOrLiqSelector = explicitGas || explicitFloor;
        Entity occupant = SpatialService.entityAt(c.world, targetPos);

        // Case 1: Loose ground item
        if ((target instanceof Resolved.OnItem)
                || (target instanceof Resolved.OnTile && layer == null && tile != null && !tile.ground().isEmpty()
                        && occupant == null && !SpatialService.hasGas(tile)
                        && !tile.has(Tag.WATER) && !tile.has(Tag.OIL) && !tile.has(Tag.ACID))) {
            if (!pull) {
                return c.rejectBlocked("push cannot launch loose items");
            }
            Item item = target instanceof Resolved.OnItem(Item i, var ignored) ? i : tile.ground().getFirst();
            if (item == null) {
                return c.rejectBlocked("no loose item there to pull");
            }
            int maxReach = force - Balance.RES_LIGHT;
            if (dist > maxReach) {
                return c.rejectBlocked("item out of telekinetic pull reach");
            }
            if (!c.pipedOutput && c.thrall.inventory().isPackFull()) {
                return c.rejectBlocked("pack is full");
            }
            c.world.map().removeGroundItem(targetPos, item);
            if (!c.thrall.inventory().isPackFull()) {
                c.thrall.inventory().addToPack(item);
            }
            c.materialOut = new Material.OfItem(item);
            c.say("Thrall pulls " + item.name() + ".");
            return ExitCode.SUCCESS;
        }

        // Case 2: Gas or floor liquid
        if (target instanceof Resolved.OnTile && tile != null
                && (isGasOrLiqSelector || (occupant == null
                        && (SpatialService.hasGas(tile) || tile.has(Tag.WATER) || tile.has(Tag.OIL) || tile.has(Tag.ACID))))) {
            if (explicitGas && !matchesGasLayer(tile, layer)) {
                return c.rejectBlocked("no matching gas cloud there");
            }
            if (explicitFloor && !matchesFloorLayer(tile, layer)) {
                return c.rejectBlocked("no matching floor liquid there");
            }
            int steps = Math.min(requestedN, Math.max(0, force - Balance.RES_GAS));
            TileDisplacement.relocateGasOrLiquid(c.world, targetPos, moveDir, steps, pull ? c.thrall.pos() : null, layer, c.log);
            c.say("Telekinesis shifts the cloud/liquid.");
            c.flushLog();
            return ExitCode.SUCCESS;
        }

        // Case 3: Entity (Creature, Prop, or Door)
        Entity entity = layer == null ? (target.targetEntity() != null ? target.targetEntity() : occupant) : null;
        if (entity == null || !entity.alive()) {
            if (!pull && tile != null && !tile.ground().isEmpty()) {
                return c.rejectBlocked("push cannot launch loose items");
            }
            return c.rejectBlocked("nothing there to " + (pull ? "pull" : "push"));
        }

        int res;
        if (entity.kind() == Entity.Kind.DOOR || entity.has(Tag.STONE) || entity.has(Tag.METAL) || entity.has(Tag.HEAVY)) {
            res = Balance.RES_HEAVY;
        } else if (entity instanceof Actor actor) {
            res = actor.strength() / 2;
        } else {
            res = Balance.RES_MEDIUM;
        }

        int steps = Math.min(requestedN, Math.max(0, force - res));
        if (steps <= 0) {
            c.say(entity.name() + " resists telekinetic force.");
            return ExitCode.SUCCESS;
        }

        int moved = 0;
        boolean slammed = false;
        Entity slammedInto = null;
        for (int i = 0; i < steps; i++) {
            Vec2 next = entity.pos().plus(moveDir);
            if (pull && next.equals(c.thrall.pos())) {
                break;
            }
            GameMap.Tile nextTile = c.world.tile(next);
            Entity blocker = (nextTile != null && !nextTile.isWall()) ? SpatialService.entityAt(c.world, next) : null;
            if (nextTile == null || nextTile.isWall() || blocker != null) {
                if (moved >= Balance.SLAM_MIN_TRAVEL) {
                    slammed = true;
                    slammedInto = blocker;
                }
                break;
            }

            boolean slipped = TileDisplacement.onEnterTile(c.world, entity, next, true, c.log);
            moved++;
            if (!entity.alive() || slipped) {
                break;
            }
        }

        c.say(moved == 0
                ? entity.name() + " cannot be moved."
                : entity.name() + " is hurled " + moved + " tile" + (moved == 1 ? "" : "s") + ".");
        c.flushLog();
        if (slammed) {
            applyCollisionDamage(c, entity);
            if (slammedInto != null && slammedInto.alive()) {
                applyCollisionDamage(c, slammedInto);
            }
        }
        return ExitCode.SUCCESS;
    }

    private static void applyCollisionDamage(VerbContext c, Entity target) {
        if (target.kind() == Entity.Kind.PROP && ThermalState.isLightOrMedium(target)) {
            int dmg = Math.max(target.hp(), Balance.FORCE_COLLISION_DMG);
            int bypassedArmor = target.armor();
            ThermalState.damageEntity(c.world, target, dmg, false, c.log);
            CombatNarrative.narrateAutoHit(c, "Telekinetic slam", target.name(), dmg, bypassedArmor, true);
        } else if (target.kind() != Entity.Kind.DOOR && !target.has(Tag.STONE) && !target.has(Tag.METAL)) {
            int bypassedArmor = target.armor();
            ThermalState.damageEntity(c.world, target, Balance.FORCE_COLLISION_DMG, false, c.log);
            if (target.alive() && target.kind() == Entity.Kind.CREATURE) {
                ThermalState.applyProne(target);
                c.log.add(target.name() + " is knocked prone!");
            }
            CombatNarrative.narrateAutoHit(c, "Telekinetic slam", target.name(), Balance.FORCE_COLLISION_DMG, bypassedArmor, true);
        }
    }

    private static boolean matchesGasLayer(GameMap.Tile tile, String layer) {
        if (!SpatialService.hasGas(tile)) return false;
        return switch (layer) {
            case "gas" -> true;
            case "steam" -> tile.has(Tag.STEAM);
            case "oil_mist" -> tile.has(Tag.OIL_MIST);
            case "caustic" -> tile.has(Tag.CAUSTIC_GAS);
            default -> false;
        };
    }

    private static boolean matchesFloorLayer(GameMap.Tile tile, String layer) {
        return switch (layer) {
            case "floor" -> tile.has(Tag.WATER) || tile.has(Tag.OIL) || tile.has(Tag.ACID);
            case "water" -> tile.has(Tag.WATER);
            case "oil" -> tile.has(Tag.OIL);
            case "acid" -> tile.has(Tag.ACID);
            default -> false;
        };
    }
}
