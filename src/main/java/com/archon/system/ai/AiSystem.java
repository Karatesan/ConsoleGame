package com.archon.system.ai;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.ItemLocation;
import com.archon.model.Tag;
import com.archon.model.Thrall;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.system.combat.CombatEngine;
import com.archon.system.item.ItemMoves;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.archon.model.Balance.*;

/** Capability-driven enemy decision loop executed during the round-end phase. */
public final class AiSystem {

    private static final Vec2[] DIRS = {
            new Vec2(0, -1), new Vec2(0, 1), new Vec2(1, 0), new Vec2(-1, 0),
            new Vec2(1, -1), new Vec2(-1, -1), new Vec2(1, 1), new Vec2(-1, 1)
    };

    private AiSystem() {}

    public static void tick(World world, EventBus bus) {
        Thrall thrall = world.thrall();
        if (!thrall.alive()) {
            return;
        }

        for (Entity entity : world.entities()) {
            if (!thrall.alive()) {
                break;
            }
            if (!(entity instanceof Actor actor) || !actor.alive()) {
                continue;
            }

            actor.resetRoundState();
            actor.clearReadied();
            Vec2 from = actor.pos();
            List<String> log = new ArrayList<>();
            takeTurn(world, actor, thrall, log);
            if (!log.isEmpty() || !from.equals(actor.pos())) {
                bus.post(new GameEvent.ActorBeat(actor.id(), actor.name(), from, actor.pos(), log));
            }
        }
    }

    private static void takeTurn(World world, Actor actor, Thrall thrall, List<String> log) {
        if (actor.has(Tag.STUNNED) || actor.has(Tag.FROZEN)) {
            log.add(actor.name() + " is unable to act.");
            return;
        }

        int actions = AI_ACTIONS_PER_TURN;

        if (actor.has(Tag.PRONE) && !actor.isCrippled(BodyPart.LEGS)) {
            actor.removeTag(Tag.PRONE);
            actions--;
            log.add(actor.name() + " stands up.");
        }

        // 1. Escape burning tile if possible
        if (actions > 0 && world.tile(actor.pos()).has(Tag.BURNING)
                && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 escape = bestStep(world, actor.pos(), thrall.pos(), false, false);
            if (escape != null) {
                stepActor(world, actor, escape, "steps out of the flames", log);
                actions--;
            }
        }

        // 2. Recover dropped weapon on current tile if unarmed
        if (actions > 0 && actor.mainHand() == null && !actor.isCrippled(BodyPart.ARM_R) && !actor.has(Tag.PRONE)) {
            Item groundWeapon = findGroundWeapon(world, actor.pos());
            if (groundWeapon != null && pickUpAndEquip(world, actor, groundWeapon)) {
                actions--;
                log.add(actor.name() + " recovers " + groundWeapon.name() + ".");
            }
        }

        if (actions <= 0) {
            return;
        }

        Item weapon = actor.mainHand();
        if (weapon != null && weapon.has(Tag.RANGED)) {
            actRanged(world, actor, thrall, weapon, actions, log);
        } else {
            actMelee(world, actor, thrall, actions, log);
        }
    }

    private static void actRanged(World world, Actor actor, Thrall thrall, Item bow, int actions, List<String> log) {
        int dist = actor.pos().chebyshev(thrall.pos());

        // Kite away if Thrall closed into melee range
        if (dist <= 1 && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 retreat = bestStep(world, actor.pos(), thrall.pos(), true, false);
            if (retreat != null) {
                stepActor(world, actor, retreat, "retreats", log);
                actions--;
            }
        }

        if (actions <= 0 || actor.has(Tag.PRONE) || actor.has(Tag.BLIND)) {
            return;
        }

        boolean hasLof = actor.pos().chebyshev(thrall.pos()) > 1
                && SpatialService.lineOfFire(world, actor.pos(), thrall.pos());
        while (actions > 0 && !hasLof && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 vantage = bfsStep(world, actor.pos(), thrall.pos(), true, false);
            if (vantage == null) {
                break;
            }
            stepActor(world, actor, vantage, "repositions", log);
            actions--;
            hasLof = actor.pos().chebyshev(thrall.pos()) > 1
                    && SpatialService.lineOfFire(world, actor.pos(), thrall.pos());
            // Stop moving with 1 action left if we need to nock/ready
            if (!hasLof && !actor.isNocked() && actions == 1) {
                break;
            }
        }

        if (actions <= 0 || actor.has(Tag.PRONE)) {
            return;
        }

        if (hasLof) {
            if (!actor.isNocked()) {
                actor.nock();
                actions--;
                log.add(actor.name() + " nocks an arrow.");
            }
            if (actions > 0 && actor.isNocked()) {
                actor.fireNocked();
                var shot = CombatEngine.resolveRanged(world.dice(), world, actor, thrall, BodyPart.TORSO, false);
                actions--;
                if (shot.hit()) {
                    String crit = shot.crit() ? "CRITICAL! " : "";
                    log.add(crit + actor.name() + " shoots Thrall's " + shot.part().path + " for " + shot.damage() + " dmg.");
                    if (shot.crippledPart()) {
                        log.add("Thrall's " + shot.part().path + " is crippled!");
                    }
                    if (shot.blindedTarget()) {
                        log.add("Thrall is blinded!");
                    }
                } else {
                    log.add(actor.name() + "'s arrow misses Thrall.");
                }
                if (actions > 0 && thrall.alive()) {
                    actor.nock();
                    log.add(actor.name() + " nocks an arrow.");
                }
            }
        } else {
            if (!actor.isNocked()) {
                actor.nock();
                actions--;
                log.add(actor.name() + " nocks an arrow.");
            }
            if (actions > 0 && actor.isNocked() && thrall.alive()
                    && !actor.isCrippled(BodyPart.ARM_R) && !actor.isCrippled(BodyPart.ARM_L)
                    && !actor.isCrippled(BodyPart.HEAD)) {
                int dmg = Math.max(MIN_DAMAGE, (bow.minDamage() + bow.maxDamage()) / 2);
                actor.ready(Actor.Trigger.ON_MOVEMENT_IN_LOS, "fires on movement in line of sight", dmg);
                log.add(actor.name() + " readies a shot.");
            }
        }
    }

    private static void actMelee(World world, Actor actor, Thrall thrall, int actions, List<String> log) {
        while (actions > 0 && actor.pos().chebyshev(thrall.pos()) > 1
                && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 step = bfsStep(world, actor.pos(), thrall.pos(), false, false);
            if (step == null) {
                step = bfsStep(world, actor.pos(), thrall.pos(), false, true);
            }
            if (step == null) {
                break;
            }
            stepActor(world, actor, step, "advances", log);
            actions--;
        }

        if (actions <= 0 || actor.has(Tag.PRONE)) {
            return;
        }

        int dist = actor.pos().chebyshev(thrall.pos());
        if (dist <= 1) {
            BodyPart targetPart = (!thrall.isCrippled(BodyPart.LEGS) && world.dice().chance(AI_AIM_LEGS_CHANCE))
                    ? BodyPart.LEGS : BodyPart.TORSO;
            var strike = CombatEngine.resolveMelee(world.dice(), world, actor, thrall, targetPart, true, "normal", false);
            actions--;
            String weaponName = strike.weapon() == null ? "fists" : strike.weapon().name();
            if (strike.hit()) {
                String crit = strike.crit() ? "CRITICAL! " : "";
                log.add(crit + actor.name() + " strikes Thrall's " + strike.part().path + " with " + weaponName
                        + " for " + strike.damage() + " dmg.");
                if (strike.crippledPart()) {
                    log.add("Thrall's " + strike.part().path + " is crippled!");
                }
                if (strike.blindedTarget()) {
                    log.add("Thrall is blinded!");
                }
            } else {
                log.add(actor.name() + " swings " + weaponName + " at Thrall — MISS.");
            }

            if (actions > 0 && thrall.alive() && !actor.isCrippled(BodyPart.HEAD)) {
                actor.setGuarded(true);
                log.add(actor.name() + " raises guard.");
            }
        } else if (dist == 2 && !actor.isCrippled(BodyPart.HEAD) && !actor.isCrippled(BodyPart.ARM_R)) {
            int dmg = CombatEngine.baseMeleeDamage(actor);
            actor.ready(Actor.Trigger.ON_ADJACENCY, "strikes approaching target", dmg);
            log.add(actor.name() + " readies a strike against approach.");
        } else if (!actor.isCrippled(BodyPart.HEAD)) {
            actor.setGuarded(true);
            log.add(actor.name() + " raises guard.");
        }
    }

    private static void stepActor(World world, Actor actor, Vec2 dest, String verb, List<String> log) {
        boolean oiled = world.tile(actor.pos()).has(Tag.OIL) || world.tile(dest).has(Tag.OIL);
        actor.moveTo(dest);
        if (world.tile(dest).has(Tag.WATER)) {
            actor.extinguish();
            actor.applyTag(Tag.WET);
        }
        if (oiled && world.dice().chance(OIL_SLIP_CHANCE)) {
            actor.applyTag(Tag.PRONE);
            actor.setGuarded(false);
            actor.clearReadied();
            log.add(actor.name() + " " + verb + " to " + dest + " and slips on the oil, falling prone!");
        } else {
            log.add(actor.name() + " " + verb + " to " + dest + ".");
        }
    }

    private static Item findGroundWeapon(World world, Vec2 pos) {
        for (Item item : world.tile(pos).ground()) {
            if (item.maxDamage() > 0) {
                return item;
            }
        }
        return null;
    }

    private static boolean pickUpAndEquip(World world, Actor actor, Item weapon) {
        ItemLocation ground = new ItemLocation.Ground(actor.pos());
        if (ItemMoves.toPack(world.map(), weapon, ground, actor) != ItemMoves.Result.SUCCESS) {
            return false;
        }
        EquipmentSlot slot = actor.inventory().equipped(EquipmentSlot.HAND_RIGHT) == null
                ? EquipmentSlot.HAND_RIGHT
                : EquipmentSlot.HAND_LEFT;
        return ItemMoves.equip(weapon, new ItemLocation.Pack(actor), slot) == ItemMoves.Result.SUCCESS;
    }

    private static Vec2 bfsStep(World world, Vec2 from, Vec2 target, boolean forLof, boolean allowHazard) {
        int w = world.width();
        int h = world.height();
        int[] dist = new int[w * h];
        Arrays.fill(dist, Integer.MAX_VALUE);
        int[] queue = new int[w * h];
        int head = 0, tail = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Vec2 p = new Vec2(x, y);
                if (!p.equals(from) && !SpatialService.passable(world, p)) {
                    continue;
                }
                if (!allowHazard && world.tile(p).has(Tag.BURNING)) {
                    continue;
                }
                boolean goal = forLof
                        ? (p.chebyshev(target) > 1 && SpatialService.lineOfFire(world, p, target))
                        : (p.chebyshev(target) <= 1);
                if (goal) {
                    int idx = y * w + x;
                    dist[idx] = 0;
                    queue[tail++] = idx;
                }
            }
        }

        while (head < tail) {
            int cur = queue[head++];
            int cx = cur % w;
            int cy = cur / w;
            int nextDist = dist[cur] + 1;
            for (Vec2 dir : DIRS) {
                int nx = cx + dir.x();
                int ny = cy + dir.y();
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int nIdx = ny * w + nx;
                if (dist[nIdx] != Integer.MAX_VALUE) continue;
                Vec2 np = new Vec2(nx, ny);
                if (!np.equals(from) && !SpatialService.passable(world, np)) continue;
                if (!allowHazard && world.tile(np).has(Tag.BURNING)) continue;
                dist[nIdx] = nextDist;
                queue[tail++] = nIdx;
            }
        }

        int fromDist = dist[from.y() * w + from.x()];
        Vec2 best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Vec2 dir : DIRS) {
            Vec2 cand = from.plus(dir);
            if (!SpatialService.passable(world, cand)) continue;
            GameMap.Tile tile = world.tile(cand);
            if (tile.has(Tag.BURNING) && !allowHazard) continue;
            int d = dist[cand.y() * w + cand.x()];
            if (d == Integer.MAX_VALUE || d >= fromDist) continue;
            int score = (fromDist - d) * AI_STEP_DIST_WEIGHT
                    - (tile.has(Tag.BURNING) ? AI_STEP_BURN_PENALTY : 0)
                    - (tile.has(Tag.OIL) ? AI_STEP_OIL_PENALTY : 0);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best != null ? best : bestStep(world, from, target, false, allowHazard);
    }

    private static Vec2 bestStep(World world, Vec2 from, Vec2 target, boolean retreat, boolean allowHazard) {
        int currentDist = from.chebyshev(target);
        boolean fromBurning = world.tile(from).has(Tag.BURNING);
        Vec2 best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Vec2 dir : DIRS) {
            Vec2 cand = from.plus(dir);
            if (!SpatialService.passable(world, cand)) {
                continue;
            }
            GameMap.Tile tile = world.tile(cand);
            boolean burning = tile.has(Tag.BURNING);
            if (burning && !allowHazard) {
                continue;
            }

            int dist = cand.chebyshev(target);
            int delta = retreat ? (dist - currentDist) : (currentDist - dist);
            if (delta <= 0 && !(fromBurning && !burning)) {
                continue;
            }

            int score = delta * AI_STEP_DIST_WEIGHT - (burning ? AI_STEP_BURN_PENALTY : 0)
                    - (tile.has(Tag.OIL) ? AI_STEP_OIL_PENALTY : 0);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }
}
