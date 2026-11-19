package com.archon.system.combat;

import com.archon.model.BodyPart;
import com.archon.model.Dice;
import com.archon.model.Entity;
import com.archon.model.Tag;
import com.archon.system.physics.EntityReactions;
import com.archon.verb.ExitCode;
import com.archon.verb.VerbContext;

import java.util.List;

public final class CombatNarrative {
    private CombatNarrative() {}

    private static final String[][] HEAD_CRITS = {
        // KILL
        {
            "You strike %s between the eyes, cracking their skull!",
            "A devastating blow caves in %s's skull — they collapse instantly.",
            "You shatter %s's jaw with sickening force, snapping their neck!",
            "A crushing head strike pulverizes bone, ending %s on the spot!",
            "You strike %s through the temple; they drop lifeless!"
        },
        // BLIND
        {
            "A savage strike across %s's brow sends blood streaming into their eyes, blinding them!",
            "You strike %s across the eyes, plunging them into darkness!",
            "A gouging blow to %s's face destroys their vision!",
            "A brutal strike catches %s's eye socket, blinding them in agony!",
            "You smash %s's forehead; blinded and stunned, they reel back!"
        },
        // CRIPPLE
        {
            "A ringing blow catches %s on the jaw, concussing them!",
            "You smash %s across the cheekbone, fracturing their face!",
            "A brutal hit snaps %s's head sideways, wrenching their neck!",
            "You crack %s's skull with a sickening crunch, leaving them dazed!",
            "A vicious strike leaves %s clutching a fractured jaw!"
        },
        // STANDARD
        {
            "A vicious strike snaps %s's head back with brutal force!",
            "You catch %s square in the face, knocking teeth loose!",
            "A heavy blow rattles %s's skull, jarring their entire body!",
            "You deliver a punishing strike across %s's temple!",
            "A brutal strike to the head catches %s completely off guard!"
        }
    };

    private static final String[][] TORSO_CRITS = {
        // KILL
        {
            "You drive clean through %s's chest, tearing through vital organs!",
            "A crushing blow caves in %s's ribcage — they gasp and drop dead!",
            "You rupture %s's chest with massive trauma, ending their fight!",
            "A lethal strike punches deep into %s's torso, stopping their heart!",
            "You pulverize %s's ribs, collapsing both lungs as they fall lifeless!"
        },
        // ARMOR BYPASS
        {
            "You find a weak seam in %s's armor, puncturing straight through!",
            "Your blow bypasses %s's armor entirely, delivering full impact!",
            "You pierce clean through %s's protective gear with devastating force!",
            "A precision strike slips past %s's armor, biting deep into flesh!",
            "Your strike ignores %s's armor, driving deep into their core!"
        },
        // STANDARD
        {
            "A ferocious strike to the solar plexus drives all the air from %s!",
            "You smash into %s's ribs with an audible crack!",
            "A heavy blow to the abdomen doubles %s over in agony!",
            "You deliver a bone-jarring strike to %s's midsection!",
            "A powerful hit rocks %s's torso, tearing muscle and bruising bone!"
        }
    };

    private static final String[][] ARM_CRITS = {
        // KILL
        {
            "A vicious strike tears through %s's shoulder, severing an artery!",
            "You crush %s's arm with lethal force, sending shock through their vitals!",
            "A brutal blow cleaves %s's arm, bringing them down permanently!",
            "You batter %s's arm into their ribs, crushing the vitals beneath!",
            "A ferocious strike shatters %s's arm and ends their life!"
        },
        // CRIPPLE
        {
            "You strike %s's arm with a loud snap, breaking the bone!",
            "A brutal blow dislocates %s's shoulder, leaving the limb limp and useless!",
            "You crush %s's elbow joint into splintered bone!",
            "A savage impact shatters %s's forearm with a sickening crunch!",
            "You wrench %s's arm with brutal leverage, tearing tendons apart!"
        },
        // STANDARD
        {
            "A deep, vicious strike tears across %s's arm, spraying blood!",
            "You batter %s's weapon-arm, numbing nerves with blunt trauma!",
            "A savage blow rips into %s's bicep with tearing force!",
            "You catch %s across the forearm with punishing impact!",
            "A furious strike gashes deep into %s's arm!"
        }
    };

    private static final String[][] LEG_CRITS = {
        // KILL
        {
            "A devastating strike severs the femoral artery, and %s collapses lifeless!",
            "You shatter %s's pelvis with catastrophic trauma — they collapse dead!",
            "A brutal blow crushes %s's thigh, stopping them dead in their tracks!",
            "You sweep into %s with bone-breaking force, smashing them to the ground dead!",
            "A savage strike breaks %s's hip and spine, ending their life!"
        },
        // CRIPPLE
        {
            "You sweep into %s's knee, bending the joint backward with an agonizing crunch!",
            "A crushing strike shatters %s's shin — they can barely stay upright!",
            "You batter %s's kneecap into bone fragments, crippling their stance!",
            "A heavy impact fractures %s's femur, dropping them to one knee in agony!",
            "You tear through the tendons behind %s's knee, ruining their footing!"
        },
        // STANDARD
        {
            "A savage blow bites deep into %s's thigh, severing muscle!",
            "You catch %s across the calf with bone-jarring impact!",
            "A fierce strike digs into %s's leg, leaving them staggering in pain!",
            "You smash low into %s's thigh with brutal force!",
            "A punishing strike to %s's lower leg draws a howl of agony!"
        }
    };

    private static final String[][] GENERIC_CRITS = {
        // KILL
        {
            "A catastrophic blow obliterates %s completely!",
            "A crushing impact demolishes %s into pieces!",
            "A lethal strike shatters %s beyond repair!",
            "A devastating hit destroys %s instantly!",
            "A tremendous strike completely reduces %s to rubble!"
        },
        // STANDARD
        {
            "A devastating blow strikes %s with critical force!",
            "A punishing impact hits %s dead center!",
            "A ferocious strike lands with tremendous power on %s!",
            "A mighty blow lands squarely on %s!",
            "A massive impact hammers directly into %s!"
        }
    };

    public static String describeCrit(Dice dice, BodyPart part, CombatEngine.HitResult res, String targetName) {
        String[] pool;
        if (part == BodyPart.HEAD) {
            if (res.killed()) pool = HEAD_CRITS[0];
            else if (res.statusApplied() == Tag.BLIND) pool = HEAD_CRITS[1];
            else if (res.crippledPart()) pool = HEAD_CRITS[2];
            else pool = HEAD_CRITS[3];
        } else if (part == BodyPart.TORSO) {
            if (res.killed()) pool = TORSO_CRITS[0];
            else if (res.armorBlocked() > 0) pool = TORSO_CRITS[1];
            else pool = TORSO_CRITS[2];
        } else if (part == BodyPart.ARM_L || part == BodyPart.ARM_R) {
            if (res.killed()) pool = ARM_CRITS[0];
            else if (res.crippledPart()) pool = ARM_CRITS[1];
            else pool = ARM_CRITS[2];
        } else if (part == BodyPart.LEGS) {
            if (res.killed()) pool = LEG_CRITS[0];
            else if (res.crippledPart()) pool = LEG_CRITS[1];
            else pool = LEG_CRITS[2];
        } else {
            if (res.killed()) pool = GENERIC_CRITS[0];
            else pool = GENERIC_CRITS[1];
        }
        int idx = dice.between(0, pool.length - 1);
        return String.format(pool[idx], targetName);
    }

    public static ExitCode narrateMelee(VerbContext c, Dice dice, String weaponName, Entity target, CombatEngine.HitResult res) {
        return narrateHit(c, dice, weaponName, target, res);
    }

    public static ExitCode narrateRanged(VerbContext c, Dice dice, Entity target, CombatEngine.HitResult res) {
        return narrateHit(c, dice, "Arrow", target, res);
    }

    public static String formatRolls(boolean hit, int hitChance, int hitRoll, boolean crit, int critChance, int critRoll) {
        if (hitRoll <= 0) {
            return "[auto-hit]";
        }
        String hitPart = hitChance + "% | roll " + (101 - hitRoll) + " vs " + (101 - hitChance) + "+";
        if (!hit || !crit || critChance <= 0 || critRoll <= 0) {
            return "[" + hitPart + "]";
        }
        return "[" + hitPart + " | crit " + critChance + "%: " + (101 - critRoll) + " vs " + (101 - critChance) + "+]";
    }

    public static String formatArmor(int armorBlocked, boolean armorBypassed) {
        if (armorBypassed && armorBlocked > 0) return " (armor bypassed)";
        if (armorBlocked > 0) return " (" + armorBlocked + " blocked)";
        return "";
    }

    public static String formatAttack(
            String source, String targetName, String zone,
            boolean hit, int hitChance, int hitRoll,
            boolean crit, int critChance, int critRoll, int critTier,
            int damage, int armorBlocked, boolean armorBypassed) {
        String target = zone == null ? targetName : targetName + "'s " + zone;
        String rolls = formatRolls(hit, hitChance, hitRoll, crit, critChance, critRoll);
        if (!hit) {
            return source + " misses " + target + ". " + rolls;
        }
        String prefix = crit ? (critTier > 1 ? "CRITICAL T" + critTier + "! " : "CRITICAL! ") : "";
        String dmgPart = damage > 0 ? " for " + damage + " dmg" + formatArmor(armorBlocked, armorBypassed) : "";
        return prefix + source + " hits " + target + dmgPart + ". " + rolls;
    }

    public static String formatAttack(String source, Entity target, CombatEngine.HitResult res) {
        BodyPart part = res.part();
        return formatAttack(
                source, target.name(), part == null ? null : part.path,
                res.hit(), res.hitChance(), res.hitRoll(),
                res.crit(), res.critChance(), res.hit() ? res.critRoll() : 0, res.crit() ? 1 : 0,
                res.damage(), res.armorBlocked(), res.crit());
    }

    public static String formatAutoHit(
            String source, String targetName, int damage, int armorBlocked, boolean armorBypassed) {
        return formatAttack(source, targetName, null, true, 100, 0, false, 0, 0, 0,
                damage, armorBlocked, armorBypassed);
    }

    public static void narrateAutoHit(
            VerbContext c, String source, String targetName, int damage, int armorBlocked, boolean armorBypassed) {
        c.say(formatAutoHit(source, targetName, damage, armorBlocked, armorBypassed));
        c.flushLog();
    }

    public static void narrateAutoHit(VerbContext c, String source, Entity target, EntityReactions.Hit hit) {
        narrateAutoHit(c, source, target.name(), hit.damage(), hit.armorBlocked(), hit.armorBypassed());
    }

    public static void narrateSpellHit(
            VerbContext c, String source, Entity target, BodyPart zone, boolean aimed,
            SpellCombat.RollResult roll, int damage, EntityReactions.Hit hit) {
        c.say(formatAttack(
                source, target.name(), aimed && zone != null ? zone.path : null,
                true, roll.hitChance(), roll.hitRoll(),
                roll.crit(), roll.critChance(), roll.critRoll(), roll.severityTier(),
                damage, hit.armorBlocked(), hit.armorBypassed()));
        c.flushLog();
    }

    public static void narrateSpellMiss(
            VerbContext c, String source, Entity target, BodyPart zone, SpellCombat.RollResult roll) {
        c.say(formatAttack(
                source, target.name(), zone != null ? zone.path : null,
                false, roll.hitChance(), roll.hitRoll(),
                false, 0, 0, 0,
                0, 0, false));
        c.flushLog();
    }

    public static void insertHit(List<String> log, int index, String source, Entity target, CombatEngine.HitResult res) {
        int i = index;
        log.add(i++, formatAttack(source, target, res));
        if (!res.hit()) {
            return;
        }
        BodyPart part = res.part();
        if (res.crippledPart() && part != null) {
            log.add(i++, target.name() + "'s " + part.path + " is crippled!");
        }
        if (res.blindedTarget()) {
            log.add(i++, target.name() + " is blinded!");
        }
        if (res.killed() && i == log.size()) {
            log.add(i++, target.name() + (target.kind() == Entity.Kind.CREATURE ? " falls." : " is destroyed."));
            List<String> notes = res.destruction().messages();
            for (int n = 0; n < notes.size(); n++) {
                log.add(i++, notes.get(n));
            }
        }
    }

    private static ExitCode narrateHit(VerbContext c, Dice dice, String source, Entity target, CombatEngine.HitResult res) {
        BodyPart part = res.part();
        c.say(formatAttack(source, target, res));

        if (!res.hit()) {
            return ExitCode.MISS;
        }
        if (res.crippledPart() && part != null) {
            c.say(target.name() + "'s " + part.path + " is crippled!");
        }
        if (res.blindedTarget()) {
            c.say(target.name() + " is blinded!");
        }
        if (res.crit()) {
            c.say(describeCrit(dice, part, res, target.name()));
        }
        if (res.killed()) {
            c.say(target.name() + (target.kind() == Entity.Kind.CREATURE ? " falls." : " is destroyed."));
            c.sayAll(res.destruction().messages());
        }
        return ExitCode.SUCCESS;
    }
}
