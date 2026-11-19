package com.archon.system.physics;

import com.archon.model.Actor;
import com.archon.model.Balance;
import com.archon.model.Entity;
import com.archon.model.GameMap;
import com.archon.model.Item;
import com.archon.model.Tag;
import com.archon.model.World;

import java.util.List;
import java.util.Set;

/** Tab 6 creature (cols B-I) and prop/door (cols Q-R) payload reactions plus LIT spark hits. */
public final class EntityReactions {

    /** Value telemetry returned by payload and kinetic impacts. */
    public record Hit(int damage, int armorBlocked, boolean armorBypassed, boolean reactionConsumed) {
        public static final Hit NONE = new Hit(0, 0, false, false);
    }

    private EntityReactions() {}

    public static Hit applyKineticHit(
            World world, Actor caster, Entity target, int rawDamage, Item pipedWeapon,
            boolean isBlast, double critMult, boolean ignoreArmorOnCrit, List<String> log) {
        return applyKineticHit(world, caster, target, rawDamage, pipedWeapon,
                isBlast, isBlast ? 100 : 0, critMult, ignoreArmorOnCrit, log);
    }

    public static Hit applyKineticHit(
            World world, Actor caster, Entity target, int rawDamage, Item pipedWeapon,
            boolean isBlast, int blastProneChance, double critMult, boolean ignoreArmorOnCrit, List<String> log) {
        if (!target.alive()) return Hit.NONE;
        if (target.kind() == Entity.Kind.DOOR) {
            if (pipedWeapon != null && (target.has(Tag.STONE) || target.has(Tag.METAL))) pipedWeapon.wear(Balance.WEAPON_CLATTER_WEAR);
            return Hit.NONE;
        }
        if (target.kind() == Entity.Kind.PROP) {
            if (target.has(Tag.STONE) || target.has(Tag.METAL)) {
                if (pipedWeapon != null) pipedWeapon.wear(Balance.WEAPON_CLATTER_WEAR);
                return Hit.NONE;
            }
            int dmg = Math.max(1, scaleBlast(rawDamage, critMult, isBlast));
            ThermalState.damageEntity(world, target, dmg, false, log);
            return new Hit(dmg, 0, false, false);
        }
        if (target.has(Tag.FROZEN)) {
            ThermalState.shredArmor(target, Balance.SHATTER_ARMOR_SHRED);
            int rawArmor = target.armor();
            int shatterRaw = Balance.SHATTER_BASE_DMG + ThermalState.magicBonus(caster);
            if (isBlast) shatterRaw = (int) Math.floor(shatterRaw * Balance.BLAST_DMG_MULT);
            int dmg = ignoreArmorOnCrit ? Math.max(1, shatterRaw) : Math.max(1, shatterRaw - rawArmor);
            ThermalState.addLog(log, "Mechanical shatter! " + target.name() + " (-" + Balance.SHATTER_ARMOR_SHRED + " armor -> " + rawArmor + ")");
            ThermalState.damageEntity(world, target, dmg, false, log);
            ThermalState.exitFrozen(target);
            maybeKnockBlastProne(world, target, blastProneChance, log);
            return new Hit(dmg, rawArmor, ignoreArmorOnCrit, true);
        }
        int rawArmor = target.armor();
        int scaled = scaleBlast(rawDamage, critMult, isBlast);
        int dmg = ignoreArmorOnCrit ? Math.max(1, scaled) : Math.max(1, scaled - rawArmor);
        ThermalState.damageEntity(world, target, dmg, false, log);
        maybeKnockBlastProne(world, target, blastProneChance, log);
        return new Hit(dmg, rawArmor, ignoreArmorOnCrit, false);
    }

    private static void maybeKnockBlastProne(World world, Entity target, int blastProneChance, List<String> log) {
        if (blastProneChance <= 0 || !target.alive() || !ThermalState.isLightOrMedium(target)) return;
        if (blastProneChance >= 100 || world.dice().chance(blastProneChance)) {
            ThermalState.applyProne(target);
            ThermalState.addLog(log, target.name() + " is knocked prone!");
        }
    }

    public static Hit applyPayloadToEntity(World world, Actor caster, Entity target, Tag payload, List<String> log) {
        return applyPayloadToEntity(world, caster, target, payload, false, false, 1.0, false, null, log);
    }

    public static Hit applyPayloadToEntity(
            World world, Actor caster, Entity target, Tag payload, boolean isBlast, boolean isCrit,
            double critMult, boolean ignoreArmorOnCrit, Set<Entity> burstHitEntities, List<String> log) {
        return applyPayloadToEntity(world, caster, target, payload, isBlast, isCrit, critMult,
                ignoreArmorOnCrit, burstHitEntities, 0, log);
    }

    public static Hit applyPayloadToEntity(
            World world, Actor caster, Entity target, Tag payload, boolean isBlast, boolean isCrit,
            double critMult, boolean ignoreArmorOnCrit, Set<Entity> burstHitEntities, int baseDmg, List<String> log) {
        if (!target.alive()) return Hit.NONE;
        return switch (payload) {
            case FORCE -> {
                int dmg = baseDmg > 0 ? baseDmg : Balance.DMG_KIN;
                yield applyKineticHit(world, caster, target, dmg + ThermalState.magicBonus(caster),
                        null, isBlast, critMult, ignoreArmorOnCrit, log);
            }
            case FIRE, INFERNO -> applyFire(world, caster, target, payload == Tag.INFERNO, isBlast, isCrit, critMult, baseDmg, log);
            case WATER -> applyWaterToEntity(world, target, burstHitEntities, true, log);
            case OIL -> { applyOilToEntity(world, target, log); yield Hit.NONE; }
            case ACID -> applyAcid(world, caster, target, isBlast, critMult, ignoreArmorOnCrit, baseDmg, log);
            case BLOOD -> applyBlood(world, caster, target, critMult, baseDmg, log);
            default -> Hit.NONE;
        };
    }

    private static Hit applyFire(
            World world, Actor caster, Entity target, boolean inferno,
            boolean isBlast, boolean isCrit, double critMult, int baseDmg, List<String> log) {
        int raw = baseDmg > 0 ? baseDmg : (inferno ? Balance.DMG_INFERNO : Balance.DMG_HEAT);
        int directDmg = Math.max(1, scaleBlast(raw + ThermalState.magicBonus(caster), critMult, isBlast));
        int baseDelta = inferno ? Balance.TD_INFERNO : Balance.TD_HEAT;
        int tempDelta = isCrit ? (int) Math.floor(baseDelta * Balance.CRIT_HEAT_TEMP_MULT) : baseDelta;

        if (target.kind() != Entity.Kind.CREATURE) {
            if (target.has(Tag.STONE)) {
                ThermalState.damageEntity(world, target, Balance.STONE_STRESS_DMG, false, log);
                return new Hit(Balance.STONE_STRESS_DMG, target.armor(), true, false);
            }
            if (target.has(Tag.METAL)) {
                target.temp += tempDelta;
                if (target.has(Tag.FLAMMABLE) || target.has(Tag.OIL)) {
                    ThermalState.igniteEntity(target);
                    ThermalState.addLog(log, target.name() + " catches fire!");
                } else {
                    ThermalState.addLog(log, target.name() + " heats to " + target.temp + "C.");
                }
                return Hit.NONE;
            }
            ThermalState.damageEntity(world, target, directDmg, false, log);
            if (target.alive()) {
                target.temp += tempDelta;
                if (target.has(Tag.OIL) || inferno || target.temp >= Balance.IGNITE_TEMP || (target.kind() != Entity.Kind.DOOR && target.has(Tag.FLAMMABLE))) {
                    ThermalState.igniteEntity(target);
                    ThermalState.addLog(log, target.name() + " bursts into flames!");
                }
            }
            return new Hit(directDmg, target.armor(), true, false);
        }

        int rawArmor = target.armor();
        boolean cauterize = target.has(Tag.BLEEDING);
        int cauterizeBonus = cauterize ? Balance.CAUTERIZE_SCALD_DMG : 0;

        if (target.has(Tag.WET)) {
            if (cauterize) ThermalState.clearBleeding(target);
            target.removeTag(Tag.WET);
            target.wetTurns = 0;
            target.temp = Math.min(target.temp + (int) Math.floor(tempDelta / target.thermalMass), Balance.BOIL_TEMP);
            ThermalState.spawnGas(world, target.pos(), Tag.STEAM);
            int total = directDmg + Balance.SCALD_FLASH_DMG + cauterizeBonus;
            ThermalState.addLog(log, "Steam flash on " + target.name() + (cauterize ? " (cauterized)!" : "!"));
            ThermalState.syncThermalState(world, target, log);
            ThermalState.damageEntity(world, target, total, false, log);
            return new Hit(total, rawArmor, true, true);
        }
        if (target.has(Tag.FROZEN)) {
            if (cauterize) ThermalState.clearBleeding(target);
            ThermalState.shredArmor(target, Balance.THERMAL_SHOCK_ARMOR_SHRED);
            boolean wasOiled = target.has(Tag.OIL);
            ThermalState.exitFrozen(target);
            ThermalState.spawnGas(world, target.pos(), Tag.STEAM);
            int total = Balance.THERMAL_SHOCK_DMG + cauterizeBonus;
            ThermalState.addLog(log, "Thermal shock! " + target.name() + " (-" + Balance.THERMAL_SHOCK_ARMOR_SHRED + " armor -> " + target.armor() + ")");
            if (wasOiled) {
                ThermalState.igniteEntity(target);
                ThermalState.addLog(log, target.name() + " ignites in grease fire!");
            }
            ThermalState.damageEntity(world, target, total, false, log);
            return new Hit(total, target.armor(), true, true);
        }

        if (cauterize) {
            ThermalState.clearBleeding(target);
            ThermalState.addLog(log, target.name() + "'s wounds are cauterized (+" + cauterizeBonus + " scald dmg)!");
        }
        int totalDmg = directDmg + cauterizeBonus;
        if (target.has(Tag.OIL) && !target.has(Tag.BURNING)) {
            ThermalState.damageEntity(world, target, totalDmg, false, log);
            if (target.alive()) {
                ThermalState.applyCreatureTempDelta(world, target, tempDelta, false, log);
                if (target.alive() && !target.has(Tag.BURNING)) {
                    ThermalState.igniteEntity(target);
                    ThermalState.addLog(log, target.name() + " ignites in grease fire!");
                }
            }
        } else if (target.has(Tag.BURNING)) {
            ThermalState.damageEntity(world, target, totalDmg, false, log);
            if (target.alive()) {
                if (inferno) ThermalState.applyCreatureTempDelta(world, target, tempDelta, false, log);
                target.temp = Math.max(target.temp, Balance.BURN_CAP);
            }
        } else {
            ThermalState.damageEntity(world, target, totalDmg, false, log);
            if (target.alive()) ThermalState.applyCreatureTempDelta(world, target, tempDelta, false, log);
        }
        return new Hit(totalDmg, rawArmor, true, false);
    }

    public static Hit applyWaterToEntity(World world, Entity target, Set<Entity> burstHitEntities, List<String> log) {
        return applyWaterToEntity(world, target, burstHitEntities, true, log);
    }

    public static Hit applyWaterToEntity(
            World world, Entity target, Set<Entity> burstHitEntities, boolean coolTemp, List<String> log) {
        if (target.kind() != Entity.Kind.CREATURE) {
            if (target.has(Tag.BURNING) || target.has(Tag.LIT)) {
                target.removeTag(Tag.LIT);
                target.extinguish();
                target.propBurnTurns = 0;
                target.temp = Balance.AMBIENT_TEMP;
                ThermalState.spawnGas(world, target.pos(), Tag.STEAM);
                ThermalState.addLog(log, target.name() + " is doused in a hiss of steam!");
            } else if (target.temp >= Balance.BOIL_TEMP) {
                target.temp = Balance.AMBIENT_TEMP;
                ThermalState.spawnGas(world, target.pos(), Tag.STEAM);
                ThermalState.addLog(log, target.name() + " cools rapidly, spawning steam!");
            } else if (coolTemp) {
                coolTowardWaterTemp(target);
            }
            target.removeTag(Tag.OIL);
            target.oilTurns = 0;
            return Hit.NONE;
        }

        target.removeTag(Tag.BLOOD);
        if (target.has(Tag.FROZEN)) {
            return Hit.NONE;
        }
        if (target.has(Tag.BURNING)) {
            boolean grease = target.has(Tag.OIL);
            target.extinguish();
            target.removeTag(Tag.OIL); target.oilTurns = 0;
            target.removeTag(Tag.WET); target.wetTurns = 0;
            target.temp = Balance.BODY_TEMP;
            ThermalState.spawnGas(world, target.pos(), Tag.STEAM);
            if (grease && (burstHitEntities == null || burstHitEntities.add(target))) {
                int bypassedArmor = target.armor();
                ThermalState.addLog(log, "Grease flare-up on " + target.name() + "!");
                ThermalState.damageEntity(world, target, Balance.SCALD_FLASH_DMG, false, log);
                return new Hit(Balance.SCALD_FLASH_DMG, bypassedArmor, true, true);
            }
            ThermalState.addLog(log, target.name() + " is extinguished!");
            return Hit.NONE;
        }

        if (coolTemp) {
            coolTowardWaterTemp(target);
        }
        if (target.temp >= Balance.BOIL_TEMP) {
            ThermalState.spawnGas(world, target.pos(), Tag.STEAM);
            ThermalState.addLog(log, "Water flashes to steam on " + target.name() + "!");
            ThermalState.syncThermalState(world, target, log);
            return Hit.NONE;
        }
        boolean wasWet = target.has(Tag.WET);
        target.removeTag(Tag.OIL);
        target.oilTurns = 0;
        target.applyTag(Tag.WET);
        target.wetTurns = Balance.WET_TURNS;
        if (!wasWet) {
            ThermalState.addLog(log, coolTemp ? target.name() + " is soaked (WET, " + target.temp + "C)." : target.name() + " is soaked (WET).");
        }
        ThermalState.syncThermalState(world, target, log);
        return Hit.NONE;
    }

    private static void coolTowardWaterTemp(Entity target) {
        if (target.temp <= Balance.AMBIENT_TEMP) return;
        int delta = (int) Math.floor(Balance.TD_WATER / target.thermalMass);
        target.temp = Math.max(Balance.AMBIENT_TEMP, target.temp + delta);
    }

    public static void applyOilToEntity(Entity target) {
        applyOilToEntity(null, target, null);
    }

    public static void applyOilToEntity(World world, Entity target) {
        applyOilToEntity(world, target, null);
    }

    public static void applyOilToEntity(World world, Entity target, List<String> log) {
        boolean wasOiled = target.has(Tag.OIL);
        target.removeTag(Tag.WET);
        target.wetTurns = 0;
        target.applyTag(Tag.OIL);
        target.oilTurns = Balance.OIL_TURNS;
        if (target.has(Tag.BURNING)) {
            target.temp = Math.max(target.temp, Balance.BURN_CAP);
        }
        if (world != null) {
            GameMap.Tile tile = world.tile(target.pos());
            if (tile != null && tile.has(Tag.BURNING) && tile.has(Tag.OIL)) {
                tile.oilTurns = Balance.OIL_TURNS;
            }
        }
        if (!wasOiled) {
            ThermalState.addLog(log, target.name() + " is coated in oil (" + Balance.OIL_TURNS + "t).");
        }
    }

    private static Hit applyAcid(
            World world, Actor caster, Entity target, boolean isBlast,
            double critMult, boolean ignoreArmorOnCrit, int baseDmg, List<String> log) {
        if (target.kind() == Entity.Kind.CREATURE && target.has(Tag.FROZEN)) {
            ThermalState.applyAcidToFloorOnly(world, target.pos());
            ThermalState.addLog(log, target.name() + "'s ice shell blocks acid (spills onto floor).");
            return new Hit(0, 0, false, true);
        }
        ThermalState.shredArmor(target, Balance.ACID_ARMOR_SHRED);
        target.applyTag(Tag.CORRODED);
        int rawArmor = target.armor();
        int raw = baseDmg > 0 ? baseDmg : Balance.DMG_ACID;
        int scaled = scaleBlast(raw + ThermalState.magicBonus(caster), critMult, isBlast);
        int dmg = ignoreArmorOnCrit ? Math.max(1, scaled) : Math.max(1, scaled - rawArmor);
        ThermalState.addLog(log, target.name() + " is corroded (-" + Balance.ACID_ARMOR_SHRED + " armor -> " + rawArmor + ").");
        boolean wasBurning = target.has(Tag.BURNING);
        ThermalState.damageEntity(world, target, dmg, false, log);
        if (wasBurning) {
            ThermalState.spawnGas(world, target.pos(), Tag.CAUSTIC_GAS);
            ThermalState.addLog(log, "Acid boils into caustic gas!");
        }
        return new Hit(dmg, rawArmor, ignoreArmorOnCrit, false);
    }

    private static Hit applyBlood(World world, Actor caster, Entity target, double critMult, int baseDmg, List<String> log) {
        if (target.kind() != Entity.Kind.CREATURE) return Hit.NONE;
        int rawArmor = target.armor();
        int raw = baseDmg > 0 ? baseDmg : Balance.DMG_BLOOD_BLAST;
        boolean wasBleeding = target.has(Tag.BLEEDING);
        boolean canBleed = !target.has(Tag.FROZEN) && target.temp > Balance.FREEZE_TEMP && target.temp < Balance.BOIL_TEMP;
        int hemorrhage = (wasBleeding && canBleed) ? Balance.BLOOD_HEMORRHAGE_DMG : 0;
        int dmg = Math.max(1, (int) Math.floor((raw + ThermalState.magicBonus(caster)) * critMult)) + hemorrhage;
        if (hemorrhage > 0) {
            ThermalState.addLog(log, target.name() + " suffers blood hemorrhage (+" + hemorrhage + " dmg)!");
        }
        ThermalState.damageEntity(world, target, dmg, false, log);
        if (!target.alive()) return new Hit(dmg, rawArmor, true, false);
        if (target.has(Tag.FROZEN) || target.temp <= Balance.FREEZE_TEMP) {
            ThermalState.addLog(log, target.name() + "'s blood clots in the cold (no bleeding).");
            return new Hit(dmg, rawArmor, true, false);
        }
        if (target.temp >= Balance.BOIL_TEMP) {
            ThermalState.addLog(log, target.name() + "'s wound cauterizes in the heat (no bleeding).");
            return new Hit(dmg, rawArmor, true, false);
        }
        target.applyTag(Tag.BLEEDING);
        target.bleedTurns = Balance.BLEED_TURNS;
        if (!target.has(Tag.WET)) target.applyTag(Tag.BLOOD);
        ThermalState.addLog(log, target.name() + (wasBleeding ? " bleeds anew (" : " begins bleeding (") + Balance.BLEED_TURNS + "t)!");
        return new Hit(dmg, rawArmor, true, false);
    }

    public static void applySparkToEntity(World world, Actor caster, Item torch, Entity target, List<String> log) {
        if (!target.alive()) return;
        if (target.has(Tag.OIL) || (target.kind() != Entity.Kind.CREATURE && target.kind() != Entity.Kind.DOOR && target.has(Tag.FLAMMABLE))) {
            ThermalState.igniteEntity(target);
            ThermalState.addLog(log, target.name() + " catches fire from " + (torch != null ? torch.name() : "sparks") + "!");
        }
    }

    private static int scaleBlast(int base, double critMult, boolean isBlast) {
        int scaled = (int) Math.floor(base * critMult);
        return isBlast ? (int) Math.floor(scaled * Balance.BLAST_DMG_MULT) : scaled;
    }
}
