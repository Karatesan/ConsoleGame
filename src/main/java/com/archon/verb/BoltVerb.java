package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Balance;
import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.combat.CombatNarrative;
import com.archon.system.combat.SpellCombat;
import com.archon.system.item.ItemMoves;
import com.archon.system.physics.EntityReactions;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileReactions;
import com.archon.system.spatial.SpatialService;

import java.util.Locale;

/** Single-target spell bolt: Pure Kinetic, Piped Item, Elemental, and Bloodlance (-b). */
public final class BoltVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        String aim = c.inv.flag("aim");
        if (aim != null) {
            BodyPart part = BodyPart.parse(aim);
            if (part == null || part == BodyPart.TORSO) {
                return Check.invalid("invalid bolt aim zone \"" + aim + "\"", "valid: head, arm.l, arm.r, legs");
            }
        }
        return c.checkAddress(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target resolved = c.resolveTarget(c.inv.arg(0));
        if (resolved == null) return dropUnpackedItem(c, ExitCode.BLOCKED);

        boolean aimed = c.inv.hasFlag("aim") || resolved instanceof Resolved.OnBody;
        BodyPart zone = aimed ? c.aimPart(resolved) : BodyPart.TORSO;
        if (aimed && (zone == null || zone == BodyPart.TORSO)) {
            return reject(c, "torso is not an aim option for bolt");
        }

        Vec2 targetPos = resolved.pos();
        if (targetPos.equals(c.thrall.pos())) {
            return reject(c, "bolt cannot target self");
        }

        boolean blind = c.thrall.has(Tag.BLIND);
        int maxRange = blind ? Balance.BLIND_MAX_RANGE : Balance.BOLT_RANGE;
        if (c.thrall.pos().chebyshev(targetPos) > maxRange) {
            return reject(c, blind
                    ? "blinded — cannot bolt beyond " + maxRange + " tile"
                    : "target out of bolt range (max " + maxRange + ")");
        }

        if (c.inv.hasFlag("blood")) {
            if (aimed) {
                return reject(c, "bolt -b cannot be aimed");
            }
            if (!SpatialService.lineOfSight(c.world, c.thrall.pos(), targetPos, true)) {
                return reject(c, "no line of sight for bloodlance");
            }
            if (!c.payBloodCost("Bloodlance", Balance.HP_COST_BOLT_BLOOD)) {
                return ExitCode.SUCCESS;
            }

            Entity occ = resolved.targetEntity() != null ? resolved.targetEntity() : SpatialService.entityAt(c.world, targetPos);
            if (occ != null && occ.alive() && occ.kind() == Entity.Kind.CREATURE) {
                int baseDmg = rollBoltBase(c.world.dice(), Tag.BLOOD);
                EntityReactions.Hit hit = EntityReactions.applyPayloadToEntity(c.world, c.thrall, occ, Tag.BLOOD,
                        false, false, 1.0, true, null, baseDmg, c.log);
                if (!occ.alive() && c.thrall.alive()) {
                    c.thrall.heal(Balance.BLOOD_KILL_HEAL);
                    c.log.add("Thrall absorbs spilled blood (+" + Balance.BLOOD_KILL_HEAL + " HP).");
                }
                CombatNarrative.narrateAutoHit(c, "Bloodlance", occ, hit);
                return ExitCode.SUCCESS;
            }
            c.say("Bloodlance dissipates harmlessly at " + targetPos + ".");
            c.flushLog();
            return ExitCode.SUCCESS;
        }

        if (!SpatialService.lineOfFire(c.world, c.thrall.pos(), targetPos)) {
            return reject(c, "no clear cardinal line of fire");
        }

        if (c.materialIn instanceof Material.OfItem(Item item)) {
            return executePipedItem(c, targetPos, item, zone, aimed);
        }

        Tag payload = c.materialIn instanceof Material.OfSubstance(Tag sub) ? sub : Tag.FORCE;
        boolean forceFocused = c.materialIn instanceof Material.OfSubstance(Tag sub) && sub == Tag.FORCE;
        String source = payloadSource(payload);

        if (payload != Tag.FORCE) {
            if (aimed) {
                return reject(c, "elemental bolt cannot be aimed");
            }
            Vec2 gasPos = SpatialService.firstGasOnLine(c.world, c.thrall.pos(), targetPos);
            if (gasPos != null) {
                TileReactions.applyPayloadToTile(c.world, c.thrall, gasPos, payload, false, false, null, c.log);
                c.say(source + " intercepts gas at " + gasPos + ".");
                c.flushLog();
                return ExitCode.SUCCESS;
            }
        }

        Entity occ = SpatialService.entityAt(c.world, targetPos);
        if (occ != null && occ.alive()) {
            if (occ.kind() == Entity.Kind.CREATURE) {
                SpellCombat.RollResult roll = SpellCombat.roll(c.world.dice(), c.thrall, occ, zone, payload);
                if (!roll.hit()) {
                    CombatNarrative.narrateSpellMiss(c, source, occ, aimed ? zone : null, roll);
                    return ExitCode.MISS;
                }
                int baseDmg = rollBoltBase(c.world.dice(), payload);
                EntityReactions.Hit hit = payload == Tag.FORCE
                        ? EntityReactions.applyKineticHit(c.world, c.thrall, occ,
                                baseDmg + ThermalState.magicBonus(c.thrall) + (forceFocused ? Balance.FORCE_FOCUS_BONUS : 0),
                                null, false, roll.critMult(), roll.ignoreArmor(), c.log)
                        : EntityReactions.applyPayloadToEntity(c.world, c.thrall, occ, payload, false,
                                roll.crit(), roll.critMult(), roll.ignoreArmor(), null, baseDmg, c.log);
                int extraDmg = SpellCombat.applyBoltStatusesAndBonuses(c.world.dice(), c.world, occ, zone, aimed,
                        payload, null, roll, hit.reactionConsumed(), c.log);
                CombatNarrative.narrateSpellHit(c, source, occ, zone, aimed, roll, hit.damage() + extraDmg, hit);
                return ExitCode.SUCCESS;
            }
            int baseDmg = rollBoltBase(c.world.dice(), payload);
            EntityReactions.Hit hit = payload == Tag.FORCE
                    ? EntityReactions.applyKineticHit(c.world, c.thrall, occ,
                            baseDmg + ThermalState.magicBonus(c.thrall) + (forceFocused ? Balance.FORCE_FOCUS_BONUS : 0),
                            null, false, 1.0, false, c.log)
                    : EntityReactions.applyPayloadToEntity(c.world, c.thrall, occ, payload, false, false, 1.0, false, null, baseDmg, c.log);
            CombatNarrative.narrateAutoHit(c, source, occ, hit);
            return ExitCode.SUCCESS;
        }

        TileReactions.applyPayloadToTile(c.world, c.thrall, targetPos, payload, false, true, null, c.log);
        c.say(source + " strikes " + targetPos + ".");
        c.flushLog();
        return ExitCode.SUCCESS;
    }

    private ExitCode executePipedItem(VerbContext c, Vec2 targetPos, Item item, BodyPart zone, boolean aimed) {
        if (item.substance() != null || item.has(Tag.LIT)) {
            if (aimed) {
                return reject(c, item.substance() != null ? "piped flask cannot be aimed" : "lit torch bolt cannot be aimed");
            }
        }

        ItemLocation loc = ItemMoves.carriedLocation(c.thrall, item);
        if (loc != null) {
            ItemMoves.remove(c.world.map(), item, loc).requireSuccess();
        }

        if (item.substance() != null) {
            Vec2 gasPos = SpatialService.firstGasOnLine(c.world, c.thrall.pos(), targetPos);
            Tag sub = item.consumeSubstance();
            if (gasPos != null) {
                TileReactions.applyPayloadToTile(c.world, c.thrall, gasPos, sub, false, false, null, c.log);
                c.say(item.name() + " shatters in gas at " + gasPos + ".");
                c.flushLog();
                return ExitCode.SUCCESS;
            }
            Entity occ = SpatialService.entityAt(c.world, targetPos);
            if (occ != null && occ.alive() && occ.kind() == Entity.Kind.CREATURE) {
                SpellCombat.RollResult roll = SpellCombat.roll(c.world.dice(), c.thrall, occ, BodyPart.TORSO, sub);
                if (roll.hit()) {
                    EntityReactions.Hit hit = EntityReactions.applyPayloadToEntity(c.world, c.thrall, occ, sub, false,
                            roll.crit(), roll.critMult(), roll.ignoreArmor(), null, c.log);
                    int extraDmg = SpellCombat.applyBoltStatusesAndBonuses(c.world.dice(), c.world, occ, BodyPart.TORSO, false,
                            sub, item, roll, hit.reactionConsumed(), c.log);
                    CombatNarrative.narrateSpellHit(c, item.name(), occ, null, false, roll, hit.damage() + extraDmg, hit);
                    return ExitCode.SUCCESS;
                }
                CombatNarrative.narrateSpellMiss(c, item.name(), occ, null, roll);
                TileReactions.applyPayloadToTile(c.world, c.thrall, targetPos, sub, false, false, null, c.log);
                c.say(item.name() + " shatters on the floor at " + targetPos + ".");
                c.flushLog();
                return ExitCode.MISS;
            }
            if (occ != null && occ.alive()) {
                EntityReactions.Hit hit = EntityReactions.applyPayloadToEntity(c.world, c.thrall, occ, sub, false, false, 1.0, false, null, c.log);
                CombatNarrative.narrateAutoHit(c, item.name(), occ, hit);
            } else {
                TileReactions.applyPayloadToTile(c.world, c.thrall, targetPos, sub, false, false, null, c.log);
                c.say(item.name() + " shatters at " + targetPos + ".");
                c.flushLog();
            }
            return ExitCode.SUCCESS;
        }

        if (item.has(Tag.LIT)) {
            Vec2 gasPos = SpatialService.firstGasOnLine(c.world, c.thrall.pos(), targetPos);
            if (gasPos != null) {
                TileReactions.applySparkToTile(c.world, c.thrall, item, gasPos, null, c.log);
                c.world.map().placeGroundItem(gasPos, item);
                c.say(item.name() + " intercepts gas at " + gasPos + ".");
                c.flushLog();
                return ExitCode.SUCCESS;
            }
            int rawDmg = rollWeaponDamage(c, item) + ThermalState.magicBonus(c.thrall);
            Entity occ = SpatialService.entityAt(c.world, targetPos);
            if (occ != null && occ.alive()) {
                if (occ.kind() == Entity.Kind.CREATURE) {
                    SpellCombat.RollResult roll = SpellCombat.roll(c.world.dice(), c.thrall, occ, BodyPart.TORSO, Tag.ITEM);
                    if (c.world.tile(targetPos).has(Tag.WATER)) {
                        item.tags().remove(Tag.LIT);
                    }
                    if (roll.hit()) {
                        EntityReactions.Hit hit = EntityReactions.applyKineticHit(c.world, c.thrall, occ, rawDmg, item, false,
                                roll.critMult(), roll.ignoreArmor(), c.log);
                        if (occ.alive() && item.has(Tag.LIT)) {
                            EntityReactions.applySparkToEntity(c.world, c.thrall, item, occ, c.log);
                        }
                        c.world.map().placeGroundItem(targetPos, item);
                        CombatNarrative.narrateSpellHit(c, item.name(), occ, null, false, roll, hit.damage(), hit);
                        return ExitCode.SUCCESS;
                    }
                    c.world.map().placeGroundItem(targetPos, item);
                    CombatNarrative.narrateSpellMiss(c, item.name(), occ, null, roll);
                    c.say(item.name() + " falls at " + targetPos + ".");
                    return ExitCode.MISS;
                }
                EntityReactions.Hit hit = EntityReactions.applyKineticHit(c.world, c.thrall, occ, rawDmg, item, false, 1.0, false, c.log);
                if (occ.alive() && item.has(Tag.LIT)) {
                    EntityReactions.applySparkToEntity(c.world, c.thrall, item, occ, c.log);
                }
                c.world.map().placeGroundItem(targetPos, item);
                CombatNarrative.narrateAutoHit(c, item.name(), occ, hit);
                return ExitCode.SUCCESS;
            }
            TileReactions.applySparkToTile(c.world, c.thrall, item, targetPos, null, c.log);
            c.world.map().placeGroundItem(targetPos, item);
            c.say(item.name() + " lands at " + targetPos + ".");
            c.flushLog();
            return ExitCode.SUCCESS;
        }

        boolean isStone = item.has(Tag.STONE) || "stone".equalsIgnoreCase(item.id());
        int baseDmg = isStone ? rollBoltBase(c.world.dice(), Tag.FORCE) : rollWeaponDamage(c, item);
        int rawDmg = baseDmg + ThermalState.magicBonus(c.thrall);
        boolean fracturesIce = isStone || item.has(Tag.HEAVY);
        Item wearTarget = isStone ? null : item;

        Entity occ = SpatialService.entityAt(c.world, targetPos);
        if (occ != null && occ.alive()) {
            if (occ.kind() == Entity.Kind.CREATURE) {
                SpellCombat.RollResult roll = SpellCombat.roll(c.world.dice(), c.thrall, occ, zone, Tag.FORCE);
                if (roll.hit()) {
                    EntityReactions.Hit hit = EntityReactions.applyKineticHit(c.world, c.thrall, occ, rawDmg, wearTarget, false,
                            roll.critMult(), roll.ignoreArmor(), c.log);
                    int extraDmg = SpellCombat.applyBoltStatusesAndBonuses(c.world.dice(), c.world, occ, zone, aimed,
                            Tag.ITEM, item, roll, hit.reactionConsumed(), c.log);
                    c.world.map().placeGroundItem(targetPos, item);
                    CombatNarrative.narrateSpellHit(c, item.name(), occ, zone, aimed, roll, hit.damage() + extraDmg, hit);
                    return ExitCode.SUCCESS;
                }
                c.world.map().placeGroundItem(targetPos, item);
                CombatNarrative.narrateSpellMiss(c, item.name(), occ, aimed ? zone : null, roll);
                c.say(item.name() + " falls at " + targetPos + ".");
                return ExitCode.MISS;
            }
            EntityReactions.Hit hit = EntityReactions.applyKineticHit(c.world, c.thrall, occ, rawDmg, wearTarget, false, 1.0, false, c.log);
            c.world.map().placeGroundItem(targetPos, item);
            CombatNarrative.narrateAutoHit(c, item.name(), occ, hit);
            return ExitCode.SUCCESS;
        }

        TileReactions.applyPayloadToTile(c.world, c.thrall, targetPos, Tag.FORCE, false, fracturesIce, null, c.log);
        c.world.map().placeGroundItem(targetPos, item);
        c.say(item.name() + " lands at " + targetPos + ".");
        c.flushLog();
        return ExitCode.SUCCESS;
    }

    private static String payloadSource(Tag payload) {
        return switch (payload) {
            case FORCE -> "Kinetic bolt";
            case FIRE -> "Heat bolt";
            case INFERNO -> "Inferno bolt";
            default -> Character.toUpperCase(payload.name().charAt(0))
                    + payload.name().substring(1).toLowerCase(Locale.ROOT) + " bolt";
        };
    }

    private static int rollBoltBase(Dice dice, Tag payload) {
        return switch (payload) {
            case FORCE -> dice.between(Balance.DMG_KIN_MIN, Balance.DMG_KIN_MAX);
            case FIRE -> dice.between(Math.max(1, Balance.DMG_HEAT - Balance.BOLT_DMG_VAR), Balance.DMG_HEAT + Balance.BOLT_DMG_VAR);
            case INFERNO -> dice.between(Math.max(1, Balance.DMG_INFERNO - Balance.BOLT_DMG_VAR), Balance.DMG_INFERNO + Balance.BOLT_DMG_VAR);
            case ACID -> dice.between(Balance.DMG_ACID - Balance.BOLT_DMG_VAR, Balance.DMG_ACID + Balance.BOLT_DMG_VAR);
            case BLOOD -> dice.between(Balance.DMG_BLOOD - Balance.BLOOD_BOLT_VAR, Balance.DMG_BLOOD + Balance.BLOOD_BOLT_VAR);
            default -> 0;
        };
    }

    private static int rollWeaponDamage(VerbContext c, Item item) {
        if (item.maxDamage() <= 0) return rollBoltBase(c.world.dice(), Tag.FORCE);
        int lo = Math.min(item.minDamage(), item.maxDamage());
        return c.world.dice().between(lo, item.maxDamage());
    }

    private static ExitCode reject(VerbContext c, String reason) {
        return dropUnpackedItem(c, c.rejectBlocked(reason));
    }

    private static ExitCode dropUnpackedItem(VerbContext c, ExitCode code) {
        if (c.materialIn instanceof Material.OfItem(Item item)
                && ItemMoves.carriedLocation(c.thrall, item) == null) {
            c.world.map().placeGroundItem(c.thrall.pos(), item);
        }
        return code;
    }
}
