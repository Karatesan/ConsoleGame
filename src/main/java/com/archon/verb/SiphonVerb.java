package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.CombatNarrative;
import com.archon.system.environment.Substances;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileReactions;
import com.archon.system.spatial.SpatialService;

import java.util.List;

/** Tab 4A/4B/4C world element extraction, thermal siphon (-t), and Blood Drain (-b). */
public final class SiphonVerb implements Verb {

    @Override
    public Check validateStructural(ValidationContext c) {
        if (c.inv.hasFlag("blood") && c.inv.hasFlag("thermal")) {
            return Check.invalid("siphon cannot combine -b and -t");
        }
        return c.checkAddress(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target target = c.resolveTarget(c.inv.arg(0));
        if (target == null) return ExitCode.BLOCKED;

        Vec2 pos = target.pos();
        if (c.thrall.has(Tag.BLIND) && c.thrall.pos().chebyshev(pos) > Balance.BLIND_MAX_RANGE) {
            return c.rejectBlocked("blinded — cannot siphon beyond " + Balance.BLIND_MAX_RANGE + " tile");
        }
        if (!SpatialService.siphonLineOfFire(c.world, c.thrall.pos(), pos)) {
            return c.rejectBlocked("target out of siphon reach or line of fire");
        }

        if (c.inv.hasFlag("blood")) {
            return executeBloodDrain(c, target, pos);
        }

        boolean promoteIn = c.materialIn instanceof Material.OfSubstance(Tag inSub)
                && (inSub == Tag.FIRE || inSub == Tag.INFERNO);
        if (c.materialIn != null && !promoteIn) {
            return c.rejectBlocked("channel buffer must hold heat to promote with siphon");
        }

        boolean thermalFlag = c.inv.hasFlag("thermal");
        Tag emitted;

        if (target instanceof Resolved.OnItem(Item item, var ignored)) {
            if (item.has(Tag.LIT)) {
                if (thermalFlag) return c.rejectBlocked("-t is only valid on water puddles and gases");
                item.tags().remove(Tag.LIT);
                emitted = Tag.INFERNO;
            } else if (item.substance() != null) {
                if (thermalFlag) return c.rejectBlocked("-t is only valid on water puddles and gases");
                if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                emitted = item.consumeSubstance();
            } else {
                return c.rejectBlocked("nothing to siphon");
            }
        } else if (target.targetEntity() != null) {
            Entity e = target.targetEntity();
            if (e.has(Tag.BURNING) || e.has(Tag.LIT)) {
                if (thermalFlag) return c.rejectBlocked("-t is only valid on water puddles and gases");
                extinguishEntityFire(c.world, e, c.log);
                emitted = Tag.INFERNO;
            } else if (e == c.thrall) {
                if (thermalFlag) return c.rejectBlocked("-t cannot be used on self");
                if (!c.thrall.has(Tag.OIL)) return c.rejectBlocked("self has no oil coat to siphon");
                if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                c.thrall.removeTag(Tag.OIL);
                c.thrall.oilTurns = 0;
                emitted = Tag.OIL;
            } else if (e.kind() == Entity.Kind.CREATURE) {
                if (thermalFlag) return c.rejectBlocked("-t is only valid on water puddles and gases");
                emitted = siphonCreatureHeat(c, e);
                if (emitted == null) return ExitCode.BLOCKED;
            } else if (e instanceof Prop prop && prop.contents() != null && prop.contents().substance() != null) {
                if (thermalFlag) return c.rejectBlocked("-t is only valid on floor water puddles and gases");
                if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                emitted = prop.contents().consumeSubstance();
            } else {
                return c.rejectBlocked("cannot siphon from props or doors");
            }
        } else {
            GameMap.Tile tile = c.world.tile(pos);
            String layer = target instanceof Resolved.OnTile ot ? ot.layer() : null;
            boolean explicitGas = "gas".equals(layer) || "steam".equals(layer)
                    || "superheated_steam".equals(layer) || "oil_mist".equals(layer);
            boolean explicitWater = "water".equals(layer);
            boolean explicitOil = "oil".equals(layer);
            boolean explicitAcid = "acid".equals(layer);
            boolean explicitFloor = "floor".equals(layer);
            boolean explicitTileSelector = explicitGas || explicitWater || explicitOil || explicitAcid || explicitFloor;
            Entity occ = SpatialService.entityAt(c.world, pos);

            if (explicitGas && !matchesGasLayer(tile, layer)) {
                return c.rejectBlocked("no matching gas cloud there");
            }

            if (!explicitGas && !explicitWater && !explicitAcid && !explicitOil
                    && (tile.has(Tag.BURNING)
                            || (!explicitFloor && occ != null && (occ.has(Tag.BURNING) || occ.has(Tag.LIT)))
                            || (!explicitFloor && hasLitGroundItem(tile)))) {
                if (thermalFlag) return c.rejectBlocked("-t is only valid on water puddles and gases");
                extinguishTileFire(c.world, pos, tile, explicitFloor ? null : occ, c.log);
                emitted = Tag.INFERNO;
            } else if ("self".equalsIgnoreCase(c.inv.arg(0).replaceFirst("^@", ""))
                    && occ == c.thrall && c.thrall.has(Tag.OIL) && !explicitTileSelector
                    && !SpatialService.hasGas(tile) && !tile.has(Tag.WATER) && !tile.has(Tag.OIL) && !tile.has(Tag.ACID)) {
                if (thermalFlag) return c.rejectBlocked("-t cannot be used on self");
                if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                c.thrall.removeTag(Tag.OIL);
                c.thrall.oilTurns = 0;
                emitted = Tag.OIL;
            } else if (SpatialService.hasGas(tile) && !explicitWater && !explicitOil && !explicitAcid && !explicitFloor) {
                Tag gas = ThermalState.gasTag(tile);
                if (promoteIn && !thermalFlag) {
                    return c.rejectBlocked("thermal promotion on gas requires -t");
                }
                ThermalState.clearGas(c.world, pos);
                if (thermalFlag) {
                    if (gas == Tag.STEAM || gas == Tag.SUPERHEATED_STEAM) ThermalState.setWaterPuddle(c.world, pos);
                    else if (gas == Tag.OIL_MIST) ThermalState.setOilSlick(c.world, pos);
                    emitted = Tag.FIRE;
                } else {
                    emitted = gas == Tag.OIL_MIST ? Tag.OIL : Tag.WATER;
                }
            } else if (explicitWater || (!explicitOil && !explicitAcid && tile.has(Tag.WATER))) {
                if (tile.has(Tag.WATER)) {
                    if (promoteIn && !thermalFlag) {
                        return c.rejectBlocked("thermal promotion on water requires -t");
                    }
                    if (thermalFlag) {
                        tile.temp += Balance.SIPHON_COOL_DELTA;
                        if (tile.temp <= Balance.FREEZE_TEMP) {
                            c.world.map().removeTag(pos, Tag.WATER);
                            c.world.map().removeTag(pos, Tag.LIQUID);
                            c.world.map().removeTag(pos, Tag.CONDUCTIVE);
                            c.world.map().addTag(pos, Tag.ICE);
                            c.log.add(pos + " water freezes into ice!");
                        }
                        emitted = Tag.FIRE;
                    } else {
                        c.world.map().removeTag(pos, Tag.WATER);
                        c.world.map().removeTag(pos, Tag.LIQUID);
                        c.world.map().removeTag(pos, Tag.CONDUCTIVE);
                        emitted = Tag.WATER;
                    }
                } else {
                    if (thermalFlag) return c.rejectBlocked("-t requires a floor water puddle");
                    if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                    Tag water = drainContainerSubstanceAt(tile, occ, Tag.WATER);
                    if (water == null) return c.rejectBlocked("nothing to siphon");
                    emitted = water;
                }
            } else if (explicitOil) {
                if (thermalFlag) return c.rejectBlocked("-t cannot be used on oil or acid pools");
                if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                if (!tile.has(Tag.OIL)) return c.rejectBlocked("no oil slick there");
                c.world.map().removeTag(pos, Tag.OIL);
                c.world.map().removeTag(pos, Tag.FLAMMABLE);
                tile.oilTurns = 0;
                if (!tile.has(Tag.WATER) && !tile.has(Tag.ACID)) c.world.map().removeTag(pos, Tag.LIQUID);
                emitted = Tag.OIL;
            } else if (explicitAcid || tile.has(Tag.ACID) || tile.has(Tag.OIL)) {
                if (thermalFlag) return c.rejectBlocked("-t cannot be used on oil or acid pools");
                if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                if (explicitAcid && !tile.has(Tag.ACID)) return c.rejectBlocked("no acid pool there");
                if (tile.has(Tag.ACID)) {
                    c.world.map().removeTag(pos, Tag.ACID);
                    if (!tile.has(Tag.OIL) && !tile.has(Tag.WATER)) c.world.map().removeTag(pos, Tag.LIQUID);
                    emitted = Tag.ACID;
                } else {
                    emitted = Substances.extract(c.world, new Resolved.OnTile(pos));
                    if (emitted == null) return c.rejectBlocked("nothing to siphon");
                }
            } else if (!explicitTileSelector && occ != null && occ.kind() == Entity.Kind.CREATURE && occ != c.thrall) {
                if (thermalFlag) return c.rejectBlocked("-t is only valid on water puddles and gases");
                emitted = siphonCreatureHeat(c, occ);
                if (emitted == null) return ExitCode.BLOCKED;
            } else {
                Tag contained = !explicitTileSelector ? drainContainerSubstanceAt(tile, occ, null) : null;
                if (contained == null) {
                    return c.rejectBlocked("nothing to siphon");
                }
                if (thermalFlag) return c.rejectBlocked("-t is only valid on water puddles and gases");
                if (promoteIn) return c.rejectBlocked("thermal promotion requires a heat source");
                emitted = contained;
            }
        }

        c.flushLog();
        if (promoteIn) {
            c.materialOut = new Material.OfSubstance(Tag.INFERNO);
            if (!c.pipedOutput) {
                c.thrall.channeledPayload = Tag.INFERNO;
                c.thrall.channelTurns = Balance.CHANNEL_MAX_TURNS;
            }
            c.say("Thrall merges heat into channeled inferno.");
        } else if (!c.pipedOutput) {
            c.materialOut = null;
            String label = ThermalState.payloadLabel(emitted);
            c.say("Thrall draws " + label + ".");
            if (emitted == Tag.FIRE || emitted == Tag.INFERNO) {
                ThermalState.detonateUnstableSiphon(c.world, c.thrall, emitted, c.log);
            } else {
                c.say("Uncontained " + label + " spills over " + c.thrall.name() + "!");
                TileReactions.spillSubstance(c.world, c.thrall.pos(), emitted, c.log);
            }
            c.flushLog();
        } else {
            c.materialOut = new Material.OfSubstance(emitted);
            c.say("Thrall draws " + ThermalState.payloadLabel(emitted) + ".");
        }
        return ExitCode.SUCCESS;
    }

    private static boolean matchesGasLayer(GameMap.Tile tile, String layer) {
        if (tile == null || !SpatialService.hasGas(tile)) return false;
        return switch (layer) {
            case "gas" -> true;
            case "steam" -> tile.has(Tag.STEAM);
            case "superheated_steam" -> tile.has(Tag.SUPERHEATED_STEAM);
            case "oil_mist" -> tile.has(Tag.OIL_MIST);
            default -> false;
        };
    }

    private ExitCode executeBloodDrain(VerbContext c, Resolved.Target target, Vec2 pos) {
        Entity entity = target.targetEntity() != null ? target.targetEntity() : SpatialService.entityAt(c.world, pos);
        if (entity == null || entity.kind() != Entity.Kind.CREATURE || !entity.alive()) {
            return c.rejectBlocked("siphon -b requires a living creature");
        }
        if (entity == c.thrall) {
            return c.rejectBlocked("siphon -b cannot target self");
        }

        if (!c.payBloodCost("Blood Drain", Balance.HP_COST_SIPHON_BLOOD)) {
            return ExitCode.SUCCESS;
        }

        boolean wasBleeding = entity.has(Tag.BLEEDING);
        int turns = wasBleeding ? entity.bleedTurns : 0;
        int bypassedArmor = entity.armor();
        if (wasBleeding) {
            ThermalState.clearBleeding(entity);
        }
        int dmg = Balance.BLOOD_BOIL_BASE + ThermalState.magicBonus(c.thrall)
                + turns * Balance.BLOOD_SIPHON_BLEED_MULT;
        ThermalState.damageEntity(c.world, entity, dmg, false, c.log);

        if (wasBleeding && entity.alive()) {
            c.log.add("BLEEDING consumed (+" + (turns * Balance.BLOOD_SIPHON_BLEED_MULT) + " dmg)!");
        }
        int heal = Math.max(1, dmg / 2);
        if (c.thrall.alive()) {
            c.thrall.heal(heal);
            c.log.add("Thrall absorbs blood (+" + heal + " HP).");
        }

        CombatNarrative.narrateAutoHit(c, "Blood Drain", entity.name(), dmg, bypassedArmor, true);
        return ExitCode.SUCCESS;
    }

    private static Tag siphonCreatureHeat(VerbContext c, Entity target) {
        if (target.has(Tag.FROZEN) || target.temp <= Balance.ABS_ZERO) {
            c.say(target.name() + " has no extractable heat");
            return null;
        }
        int bypassedArmor = target.armor();
        c.log.add(CombatNarrative.formatAutoHit("Siphon", target.name(), Balance.FROSTBITE_DMG, bypassedArmor, true));
        ThermalState.damageEntity(c.world, target, Balance.FROSTBITE_DMG, false, c.log);
        if (target.alive()) {
            ThermalState.applyCreatureTempDelta(c.world, target, Balance.SIPHON_COOL_DELTA, false, c.log);
            if (target.alive()) {
                c.log.add("cooled to " + target.temp + "C");
            }
        }
        return Tag.FIRE;
    }

    private static boolean hasLitGroundItem(GameMap.Tile tile) {
        List<Item> ground = tile.ground();
        for (int i = 0; i < ground.size(); i++) {
            if (ground.get(i).has(Tag.LIT)) return true;
        }
        return false;
    }

    private static void extinguishEntityFire(World world, Entity e, List<String> log) {
        e.removeTag(Tag.LIT);
        e.extinguish();
        e.removeTag(Tag.OIL);
        e.oilTurns = 0;
        e.propBurnTurns = 0;
        e.temp = e.kind() == Entity.Kind.CREATURE ? Balance.BODY_TEMP : Balance.AMBIENT_TEMP;
        if (e.kind() == Entity.Kind.CREATURE) {
            ThermalState.syncThermalState(world, e, log);
        }
    }

    private static void extinguishTileFire(World world, Vec2 pos, GameMap.Tile tile, Entity occ, List<String> log) {
        if (tile.has(Tag.BURNING)) {
            world.map().removeTag(pos, Tag.BURNING);
            world.map().removeTag(pos, Tag.OIL);
            world.map().removeTag(pos, Tag.FLAMMABLE);
            if (!tile.has(Tag.ACID) && !tile.has(Tag.WATER)) {
                world.map().removeTag(pos, Tag.LIQUID);
            }
            tile.oilTurns = 0;
            tile.burnTurns = 0;
            tile.temp = Balance.AMBIENT_TEMP;
        }
        if (occ != null && (occ.has(Tag.BURNING) || occ.has(Tag.LIT))) {
            extinguishEntityFire(world, occ, log);
        }
        List<Item> ground = tile.ground();
        for (int i = 0; i < ground.size(); i++) {
            ground.get(i).tags().remove(Tag.LIT);
        }
    }

    private static Tag drainContainerSubstanceAt(GameMap.Tile tile, Entity occ, Tag required) {
        List<Item> ground = tile.ground();
        for (int i = 0; i < ground.size(); i++) {
            Item item = ground.get(i);
            if (item.substance() != null && (required == null || item.substance() == required)) {
                return item.consumeSubstance();
            }
        }
        if (occ instanceof Prop prop && prop.contents() != null && prop.contents().substance() != null
                && (required == null || prop.contents().substance() == required)) {
            return prop.contents().consumeSubstance();
        }
        return null;
    }
}
