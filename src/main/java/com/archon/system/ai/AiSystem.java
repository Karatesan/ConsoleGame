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
import com.archon.system.combat.CombatNarrative;
import com.archon.system.item.ItemMoves;
import com.archon.system.physics.TileDisplacement;
import com.archon.system.spatial.SpatialService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.archon.model.Balance.*;

/** Capability-driven enemy decision loop executed during the round-end phase. */
public final class AiSystem {

    private static final Vec2[] DIRS = {
            new Vec2(0, -1), new Vec2(0, 1), new Vec2(1, 0), new Vec2(-1, 0)
    };

    private AiSystem() {}

    public static void tick(World world, EventBus bus) {
        Thrall thrall = world.thrall();
        if (!thrall.alive()) {
            return;
        }

        List<Entity> entities = world.entities();
        for (int i = 0; i < entities.size(); i++) {
            if (!thrall.alive()) {
                break;
            }
            Entity entity = entities.get(i);
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

    private static int stepCost(Actor actor) {
        int cost = 1;
        if (actor.has(Tag.CHILLED)) cost += CHILLED_AP_STEP;
        if (actor.has(Tag.CRIPPLED)) cost += CRIPPLED_AP_STEP;
        return cost;
    }

    private static boolean canAct(Actor actor) {
        return actor.alive() && !actor.has(Tag.FROZEN) && !actor.has(Tag.STUNNED);
    }

    private static boolean isHazard(GameMap.Tile tile) {
        return tile.has(Tag.BURNING) || tile.has(Tag.CAUSTIC_GAS) || tile.has(Tag.OIL_MIST) || tile.has(Tag.ACID);
    }

    private static void takeTurn(World world, Actor actor, Thrall thrall, List<String> log) {
        if (!canAct(actor)) {
            log.add(actor.name() + " is unable to act.");
            return;
        }

        int actions = AI_ACTIONS_PER_TURN;

        if (actor.has(Tag.PRONE) && !actor.isCrippled(BodyPart.LEGS) && actions >= AP_STAND_UP) {
            actor.removeTag(Tag.PRONE);
            actions -= AP_STAND_UP;
            log.add(actor.name() + " stands up.");
        }

        int moveCost = stepCost(actor);

        // 1. Escape hazard tile if possible
        if (actions >= moveCost && isHazard(world.tile(actor.pos()))
                && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 escape = bestStep(world, actor.pos(), thrall.pos(), false, false);
            if (escape != null) {
                stepActor(world, actor, escape, "steps out of danger", log);
                actions -= moveCost;
                if (!canAct(actor)) return;
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

        if (actions <= 0 || !canAct(actor)) {
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
        int dist = actor.pos().manhattan(thrall.pos());
        int moveCost = stepCost(actor);

        // Kite away if Thrall closed into melee range
        if (dist <= 1 && actions >= moveCost && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 retreat = bestStep(world, actor.pos(), thrall.pos(), true, false);
            if (retreat != null) {
                stepActor(world, actor, retreat, "retreats", log);
                actions -= moveCost;
                if (!canAct(actor)) return;
            }
        }

        if (actions <= 0 || actor.has(Tag.PRONE) || !canAct(actor)) {
            return;
        }

        boolean blind = actor.has(Tag.BLIND);
        if (blind) {
            if (SpatialService.hasGas(world.tile(actor.pos())) && actions >= moveCost
                    && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
                Vec2 clearStep = bestStepOutOfGas(world, actor.pos(), thrall.pos());
                if (clearStep != null) {
                    stepActor(world, actor, clearStep, "steps clear of the cloud", log);
                    actions -= moveCost;
                    if (!canAct(actor)) return;
                }
            }
            int curDist = actor.pos().manhattan(thrall.pos());
            if (actions > 0 && !actor.has(Tag.PRONE)
                    && curDist <= BLIND_MAX_RANGE && SpatialService.lineOfFire(world, actor.pos(), thrall.pos())) {
                fireRangedShot(world, actor, thrall, actions, false, log);
            }
            return;
        }

        boolean hasLof = actor.pos().manhattan(thrall.pos()) > 1
                && SpatialService.lineOfFire(world, actor.pos(), thrall.pos());
        while (actions >= moveCost && !hasLof && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 vantage = bfsStep(world, actor.pos(), thrall.pos(), true, false);
            if (vantage == null) {
                break;
            }
            stepActor(world, actor, vantage, "repositions", log);
            actions -= moveCost;
            if (!canAct(actor)) return;
            hasLof = actor.pos().manhattan(thrall.pos()) > 1
                    && SpatialService.lineOfFire(world, actor.pos(), thrall.pos());
            // Stop moving with 1 action left if we need to nock/ready
            if (!hasLof && !actor.isNocked() && actions == 1) {
                break;
            }
        }

        if (actions <= 0 || actor.has(Tag.PRONE) || !canAct(actor)) {
            return;
        }

        if (hasLof) {
            fireRangedShot(world, actor, thrall, actions, true, log);
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

    private static void fireRangedShot(
            World world, Actor actor, Thrall thrall, int actions, boolean allowReNock, List<String> log) {
        if (!actor.isNocked()) {
            actor.nock();
            actions--;
            log.add(actor.name() + " nocks an arrow.");
        }
        if (actions > 0 && actor.isNocked()) {
            actor.fireNocked();
            int idx = log.size();
            var shot = CombatEngine.resolveRanged(world.dice(), world, actor, thrall, BodyPart.TORSO, false, log);
            actions--;
            CombatNarrative.insertHit(log, idx, actor.name() + "'s arrow", thrall, shot);
            if (allowReNock && actions > 0 && thrall.alive()) {
                actor.nock();
                log.add(actor.name() + " nocks an arrow.");
            }
        }
    }

    private static void actMelee(World world, Actor actor, Thrall thrall, int actions, List<String> log) {
        int moveCost = stepCost(actor);
        while (actions >= moveCost && actor.pos().manhattan(thrall.pos()) > 1
                && !actor.isCrippled(BodyPart.LEGS) && !actor.has(Tag.PRONE)) {
            Vec2 step = bfsStep(world, actor.pos(), thrall.pos(), false, false);
            if (step == null) {
                step = bfsStep(world, actor.pos(), thrall.pos(), false, true);
            }
            if (step == null) {
                break;
            }
            stepActor(world, actor, step, "advances", log);
            actions -= moveCost;
            if (!canAct(actor)) return;
        }

        if (actions <= 0 || actor.has(Tag.PRONE) || !canAct(actor)) {
            return;
        }

        int dist = actor.pos().manhattan(thrall.pos());
        boolean canHitBlind = !actor.has(Tag.BLIND) || dist <= BLIND_MAX_RANGE;
        if (dist <= 1 && canHitBlind) {
            BodyPart targetPart = (!thrall.isCrippled(BodyPart.LEGS) && world.dice().chance(AI_AIM_LEGS_CHANCE))
                    ? BodyPart.LEGS : BodyPart.TORSO;
            int idx = log.size();
            var strike = CombatEngine.resolveMelee(world.dice(), world, actor, thrall, targetPart, true, "normal", false, log);
            actions--;
            String weaponName = strike.weapon() == null ? "fists" : strike.weapon().name();
            CombatNarrative.insertHit(log, idx, actor.name() + "'s " + weaponName, thrall, strike);

            if (actions > 0 && thrall.alive() && !actor.isCrippled(BodyPart.HEAD)) {
                actor.setGuarded(true);
                log.add(actor.name() + " raises guard.");
            }
        } else if (dist == 2 && !actor.has(Tag.BLIND) && !actor.isCrippled(BodyPart.HEAD) && !actor.isCrippled(BodyPart.ARM_R)) {
            int dmg = CombatEngine.baseMeleeDamage(actor);
            actor.ready(Actor.Trigger.ON_ADJACENCY, "strikes approaching target", dmg);
            log.add(actor.name() + " readies a strike against approach.");
        } else if (!actor.isCrippled(BodyPart.HEAD)) {
            actor.setGuarded(true);
            log.add(actor.name() + " raises guard.");
        }
    }

    private static void stepActor(World world, Actor actor, Vec2 dest, String verb, List<String> log) {
        int idx = log.size();
        boolean slipped = TileDisplacement.onEnterTile(world, actor, dest, true, log);
        if (slipped) {
            for (int i = log.size() - 1; i >= idx; i--) {
                if (log.get(i).endsWith(" slips and falls prone!")) {
                    log.remove(i);
                }
            }
            log.add(idx, actor.name() + " " + verb + " to " + dest + " and slips, falling prone!");
        } else {
            log.add(idx, actor.name() + " " + verb + " to " + dest + ".");
        }
    }

    private static Item findGroundWeapon(World world, Vec2 pos) {
        List<Item> ground = world.tile(pos).ground();
        for (int i = 0; i < ground.size(); i++) {
            Item item = ground.get(i);
            if (item.maxDamage() > 0 && !item.has(Tag.STONE)) {
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
                if (!allowHazard && isHazard(world.tile(p))) {
                    continue;
                }
                boolean goal = forLof
                        ? (p.manhattan(target) > 1 && SpatialService.lineOfFire(world, p, target))
                        : (p.manhattan(target) == 1);
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
            for (int i = 0; i < DIRS.length; i++) {
                Vec2 dir = DIRS[i];
                int nx = cx + dir.x();
                int ny = cy + dir.y();
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int nIdx = ny * w + nx;
                if (dist[nIdx] != Integer.MAX_VALUE) continue;
                Vec2 np = new Vec2(nx, ny);
                if (!np.equals(from) && !SpatialService.passable(world, np)) continue;
                if (!allowHazard && isHazard(world.tile(np))) continue;
                dist[nIdx] = nextDist;
                queue[tail++] = nIdx;
            }
        }

        int fromDist = dist[from.y() * w + from.x()];
        Vec2 best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < DIRS.length; i++) {
            Vec2 cand = from.plus(DIRS[i]);
            if (!SpatialService.passable(world, cand)) continue;
            GameMap.Tile tile = world.tile(cand);
            boolean hazard = isHazard(tile);
            if (hazard && !allowHazard) continue;
            int d = dist[cand.y() * w + cand.x()];
            if (d == Integer.MAX_VALUE || d >= fromDist) continue;
            int score = scoreStep(fromDist - d, hazard, tile);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best != null ? best : bestStep(world, from, target, false, allowHazard);
    }

    private static Vec2 bestStepOutOfGas(World world, Vec2 from, Vec2 target) {
        Vec2 best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < DIRS.length; i++) {
            Vec2 cand = from.plus(DIRS[i]);
            if (!SpatialService.passable(world, cand)) continue;
            GameMap.Tile tile = world.tile(cand);
            if (isHazard(tile) || SpatialService.hasGas(tile)) continue;
            int score = scoreStep(cand.manhattan(target) - from.manhattan(target), false, tile);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }

    private static int scoreStep(int distDelta, boolean hazard, GameMap.Tile tile) {
        return distDelta * AI_STEP_DIST_WEIGHT
                - (hazard ? AI_STEP_BURN_PENALTY : 0)
                - (SpatialService.hasGas(tile) ? AI_STEP_OIL_PENALTY * 2 : 0)
                - (tile.has(Tag.OIL) ? AI_STEP_OIL_PENALTY : 0);
    }

    private static Vec2 bestStep(World world, Vec2 from, Vec2 target, boolean retreat, boolean allowHazard) {
        int currentDist = from.manhattan(target);
        boolean fromHazard = isHazard(world.tile(from));
        Vec2 best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < DIRS.length; i++) {
            Vec2 cand = from.plus(DIRS[i]);
            if (!SpatialService.passable(world, cand)) {
                continue;
            }
            GameMap.Tile tile = world.tile(cand);
            boolean hazard = isHazard(tile);
            if (hazard && !allowHazard) {
                continue;
            }

            int dist = cand.manhattan(target);
            int delta = retreat ? (dist - currentDist) : (currentDist - dist);
            if (delta <= 0 && !(fromHazard && !hazard)) {
                continue;
            }

            int score = scoreStep(delta, hazard, tile);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }
}
