package com.archon.verb;

import com.archon.address.Resolved;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.Vec2;
import com.archon.system.combat.CombatNarrative;
import com.archon.system.physics.EntityReactions;
import com.archon.system.physics.ThermalState;
import com.archon.system.physics.TileReactions;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 5-tile cardinal cross AoE spell: Kinetic vertical crush, Elemental blast, or Crimson Nova (-b). */
public final class BlastVerb implements Verb {

    private static final int[][] CROSS_OFFSETS = {{0, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, 0}};

    @Override
    public Check validateStructural(ValidationContext c) {
        return c.checkAddress(0);
    }

    @Override
    public ExitCode execute(VerbContext c) {
        Resolved.Target resolved = c.resolveTarget(c.inv.arg(0));
        if (resolved == null) return ExitCode.BLOCKED;

        boolean blood = c.inv.hasFlag("blood");
        Vec2 epicenter = resolved.pos();
        if (!blood && epicenter.equals(c.thrall.pos())) {
            return c.rejectBlocked("blast cannot target self");
        }

        boolean blind = c.thrall.has(Tag.BLIND);
        int maxRange = blind ? Balance.BLIND_MAX_RANGE : Balance.BLAST_RANGE;
        if (c.thrall.pos().chebyshev(epicenter) > maxRange) {
            return c.rejectBlocked(blind
                    ? "blinded — cannot blast beyond " + maxRange + " tile"
                    : "epicenter out of blast range (max " + maxRange + ")");
        }
        if (blood) {
            if (!SpatialService.lineOfSight(c.world, c.thrall.pos(), epicenter, true)) {
                return c.rejectBlocked("no line of sight for crimson nova");
            }
        } else if (!SpatialService.lineOfFire(c.world, c.thrall.pos(), epicenter)) {
            return c.rejectBlocked("no clear cardinal line of fire to epicenter");
        }

        Tag payload;
        if (blood) {
            payload = Tag.BLOOD;
            if (!c.payBloodCost("Crimson Nova", Balance.HP_COST_BLAST_BLOOD)) {
                return ExitCode.SUCCESS;
            }
        } else if (c.materialIn instanceof Material.OfSubstance(Tag sub)) {
            if (sub == Tag.FORCE) {
                return c.rejectBlocked("cannot pipe force focus into blast");
            }
            payload = sub;
            Vec2 gasPos = SpatialService.firstGasOnLine(c.world, c.thrall.pos(), epicenter);
            if (gasPos != null) {
                epicenter = gasPos;
            }
        } else if (c.materialIn != null) {
            return c.rejectBlocked("blast cannot use piped items");
        } else {
            payload = Tag.FORCE;
        }

        List<Vec2> cross = new ArrayList<>(5);
        for (int i = 0; i < CROSS_OFFSETS.length; i++) {
            Vec2 p = new Vec2(epicenter.x() + CROSS_OFFSETS[i][0], epicenter.y() + CROSS_OFFSETS[i][1]);
            GameMap.Tile t = c.world.tile(p);
            if (t != null && !t.isWall() && !p.equals(c.thrall.pos())) {
                cross.add(p);
            }
        }

        Set<Entity> burstHitEntities = new HashSet<>();

        if (payload == Tag.BLOOD) {
            c.say("Crimson Nova erupts across " + epicenter + "!");
            c.flushLog();
            int kills = 0;
            boolean anyHit = false;
            for (int i = 0; i < cross.size(); i++) {
                Entity occ = SpatialService.entityAt(c.world, cross.get(i));
                if (occ != null && occ.alive() && occ.kind() == Entity.Kind.CREATURE) {
                    anyHit = true;
                    EntityReactions.Hit hit = EntityReactions.applyPayloadToEntity(c.world, c.thrall, occ, Tag.BLOOD,
                            false, false, 1.0, true, burstHitEntities, Balance.DMG_BLOOD_BLAST, c.log);
                    if (!occ.alive()) {
                        kills++;
                    }
                    CombatNarrative.narrateAutoHit(c, "Crimson Nova", occ, hit);
                }
            }
            if (kills > 0 && c.thrall.alive()) {
                int heal = kills * Balance.BLOOD_NOVA_KILL_HEAL;
                c.thrall.heal(heal);
                c.say("Thrall absorbs spilled blood (+" + heal + " HP).");
            }
            if (!anyHit) c.say("No living targets in range.");
            return ExitCode.SUCCESS;
        }

        if (payload == Tag.FORCE) {
            c.say("Kinetic blast crushes " + epicenter + "!");
            int rawDmg = Balance.DMG_KIN + ThermalState.magicBonus(c.thrall);
            boolean anyEffect = false;
            for (int i = 0; i < cross.size(); i++) {
                Vec2 pos = cross.get(i);
                TileReactions.applyPayloadToTile(c.world, c.thrall, pos, Tag.FORCE, true, true, burstHitEntities, c.log);

                List<Item> ground = c.world.tile(pos).ground();
                List<Item> fragile = null;
                for (int g = 0; g < ground.size(); g++) {
                    Item item = ground.get(g);
                    if (item.substance() != null || item.has(Tag.GLASS) || item.has(Tag.BREAKABLE)) {
                        if (fragile == null) fragile = new ArrayList<>();
                        fragile.add(item);
                    }
                }
                if (fragile != null) {
                    for (int f = 0; f < fragile.size(); f++) {
                        Item flask = fragile.get(f);
                        c.world.map().removeGroundItem(pos, flask);
                        Tag sub = flask.consumeSubstance();
                        if (sub != null) {
                            c.log.add(pos + " " + flask.name() + " shatters, spilling " + sub.name().toLowerCase(Locale.ROOT) + "!");
                            TileReactions.spillSubstance(c.world, pos, sub, c.log);
                        } else {
                            c.log.add(pos + " " + flask.name() + " shatters.");
                        }
                    }
                }
                if (!c.log.isEmpty()) {
                    c.flushLog();
                    anyEffect = true;
                }

                Entity occ = SpatialService.entityAt(c.world, pos);
                if (occ != null && occ.alive()) {
                    anyEffect = true;
                    int proneChance = pos.equals(epicenter) ? 100 : Balance.BLAST_ARM_PRONE_CHANCE;
                    EntityReactions.Hit hit = EntityReactions.applyKineticHit(
                            c.world, c.thrall, occ, rawDmg, null, true, proneChance, 1.0, false, c.log);
                    if (hit.damage() > 0 || !c.log.isEmpty() || !occ.alive()) {
                        CombatNarrative.narrateAutoHit(c, "Kinetic blast", occ, hit);
                    } else {
                        c.say(occ.name() + " withstands the kinetic crush.");
                    }
                }
            }
            if (!anyEffect) c.say("No targets affected.");
            return ExitCode.SUCCESS;
        }

        String blastSource = blastSource(payload);
        c.say(blastSource + " saturates " + epicenter + "!");
        boolean[] hadGas = new boolean[cross.size()];
        List<Vec2> initialCaustic = new ArrayList<>();
        for (int i = 0; i < cross.size(); i++) {
            Vec2 pos = cross.get(i);
            hadGas[i] = SpatialService.hasGas(c.world.tile(pos));
            if (payload == Tag.ACID && c.world.tile(pos).has(Tag.CAUSTIC_GAS)) {
                initialCaustic.add(pos);
            }
        }

        boolean anyEffect = false;
        for (int i = 0; i < cross.size(); i++) {
            Vec2 pos = cross.get(i);
            TileReactions.applyPayloadToTile(c.world, c.thrall, pos, payload, true, false, burstHitEntities, c.log);
            if (!c.log.isEmpty()) {
                c.flushLog();
                anyEffect = true;
            }
            Entity occ = SpatialService.entityAt(c.world, pos);
            if (!hadGas[i] && occ != null && occ.alive()) {
                anyEffect = true;
                EntityReactions.Hit hit = EntityReactions.applyPayloadToEntity(c.world, c.thrall, occ, payload, true, false,
                        1.0, false, burstHitEntities, c.log);
                if (hit.damage() > 0 || !c.log.isEmpty() || !occ.alive()) {
                    CombatNarrative.narrateAutoHit(c, blastSource, occ, hit);
                }
            }
        }

        if (payload == Tag.ACID) {
            for (int i = 0; i < initialCaustic.size(); i++) {
                TileReactions.expandCausticGasOutward(c.world, initialCaustic.get(i));
                c.say("Caustic gas expands outward from " + initialCaustic.get(i) + "!");
                anyEffect = true;
            }
        }
        if (!anyEffect) c.say("No targets affected.");
        return ExitCode.SUCCESS;
    }

    private static String blastSource(Tag payload) {
        return switch (payload) {
            case FIRE -> "Heat blast";
            case INFERNO -> "Inferno blast";
            default -> Character.toUpperCase(payload.name().charAt(0))
                    + payload.name().substring(1).toLowerCase(Locale.ROOT) + " blast";
        };
    }
}
