package com.archon.app;

import com.archon.command.FlagSpec;
import com.archon.exec.EconomyRules;
import com.archon.model.Balance;
import com.archon.verb.*;
import com.archon.verb.VerbDefinition.Documentation;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.archon.verb.MaterialKind.ITEM;
import static com.archon.verb.MaterialKind.SUBSTANCE;

public final class DefaultVerbs {

    private DefaultVerbs() {}

    public static VerbCatalog create(EconomyRules rules) {
        CommandManual manual = new CommandManual(rules);

        ApCost zero = new ApCost.Fixed(0);
        ApCost one = new ApCost.Fixed(1);
        ApCost two = new ApCost.Fixed(2);
        Set<MaterialKind> none = Set.of();
        Set<MaterialKind> items = Set.of(ITEM);
        Set<MaterialKind> substances = Set.of(SUBSTANCE);

        FlagSpec aim = new FlagSpec(
                "aim", "a", "part", null,
                "Aim for head, torso, arm.l, arm.r, or legs.");
        FlagSpec power = new FlagSpec(
                "power", "p", "level", "heavy",
                "Choose light, normal, or heavy power. Bare -p selects heavy.");
        FlagSpec force = new FlagSpec(
                "force", "f", null, null,
                "Use a forceful melee attack.");
        FlagSpec careful = new FlagSpec(
                "careful", "c", null, null,
                "Use careful movement pricing.");
        FlagSpec roll = new FlagSpec(
                "roll", "r", null, null,
                "Drop prone and roll over the ground to smother flames.");
        FlagSpec thermal = new FlagSpec(
                "thermal", "t", null, null,
                "Extract thermal heat instead of matter, condensing gas or freezing water.");
        FlagSpec channelForce = new FlagSpec(
                "force", "f", null, null,
                "Store or emit telekinetic force focus.");
        FlagSpec blood = new FlagSpec(
                "blood", "b", null, null,
                "Spend caster HP for forbidden blood magic.");

        VerbCatalog catalog = new VerbCatalog(List.of(
                new VerbDefinition(
                        "scan", new ScanVerb(), List.of(), zero, false, none, none,
                        new Documentation(
                                "FREE UTILITIES", "", "List perceived entities",
                                "Lists living entities, their IDs, positions, HP, burning state, "
                                        + "readied reactions, and distance from the thrall.",
                                List.of("scan"))),
                new VerbDefinition(
                        "inspect", new InspectVerb(), List.of(), zero, false, none, none,
                        new Documentation(
                                "FREE UTILITIES", "<address>", "Inspect an entity, item, or tile",
                                "Displays entity health and defenses, actor hit locations and reactions, "
                                        + "item contents, or tile information.",
                                List.of("inspect o1", "inspect o1/body/head", "inspect /pack/torch"))),
                new VerbDefinition(
                        "status", new StatusVerb(), List.of(), zero, false, none, none,
                        new Documentation(
                                "FREE UTILITIES", "", "Display thrall state",
                                "Reports HP, tags, held equipment, pack contents, and nocking state.",
                                List.of("status"))),
                new VerbDefinition(
                        "ls", new LsVerb(), List.of(), zero, false, none, none,
                        new Documentation(
                                "FREE UTILITIES", "[address]", "List contents",
                                "Lists an addressed location, item, or tile. Defaults to /pack.",
                                List.of("ls", "ls /pack", "ls @self"))),
                new VerbDefinition(
                        "help", new HelpVerb(manual), List.of(), zero, false, none, none,
                        new Documentation(
                                "FREE UTILITIES", "[verb | topic]", "Command manual and reference guides",
                                "Without an argument, lists commands and topics. Otherwise displays "
                                        + "the selected command or reference topic.",
                                List.of("help", "help strike", "help operators"))),
                new VerbDefinition(
                        "map", new MapVerb(), List.of(), zero, false, none, none,
                        new Documentation(
                                "FREE UTILITIES", "", "Redraw the map and HUD",
                                "Requests a full redraw of the current view.",
                                List.of("map"))),
                new VerbDefinition(
                        "pass", new PassVerb(), List.of(roll),
                        new ApCost.ByPresence("roll", 0, Balance.AP_PASS_R),
                        true, none, none,
                        new Documentation(
                                "FREE UTILITIES", "[-r]", "End the current round or roll to smother flames",
                                "Ends the round, forfeiting unspent AP. With -r, drops prone and rolls to smother flames without ending the round.",
                                List.of("pass", "pass -r", "strike o1 ; pass"))),
                new VerbDefinition(
                        "step", new StepVerb(), List.of(careful),
                        new ApCost.ByPresence("careful", 1, 2),
                        false, none, none,
                        new Documentation(
                                "MOVEMENT", "<dir>", "Advance one tile",
                                "Moves n, s, e, or w. Walls and occupants block movement. "
                                        + "Movement may trigger readied reactions.",
                                List.of("step e", "step n -c", "step w ; strike o1"))),
                new VerbDefinition(
                        "strike", new StrikeVerb(), List.of(aim, power, force),
                        new ApCost.Aimed(
                                new ApCost.ByValue("power", Balance.NORMAL_AP_COST, Map.of(
                                        "light", Balance.LIGHT_AP_COST,
                                        "normal", Balance.NORMAL_AP_COST,
                                        "heavy", Balance.HEAVY_AP_COST)),
                                Balance.AIM_EXTRA_AP),
                        false, none, none,
                        new Documentation(
                                "COMBAT", "[target]", "Melee attack or disarm attempt",
                                "Attacks an adjacent target. Without a target, selects the sole adjacent "
                                        + "hostile. Address an occupied hand slot to attempt a disarm.",
                                List.of("strike o1", "strike o1 -a head -p heavy",
                                        "strike o1/hand/right", "strike o1 || guard"))),
                new VerbDefinition(
                        "guard", new GuardVerb(), List.of(), one, false, none, none,
                        new Documentation(
                                "COMBAT", "", "Raise defense",
                                "Raises guard, increasing armor by " + Balance.GUARD_ARMOR_BONUS_PCT
                                        + "% and reducing enemy melee hit chance by " + Balance.GUARD_MELEE_HIT_PENALTY
                                        + "% until the next round.",
                                List.of("guard", "strike o1 || guard"))),
                new VerbDefinition(
                        "nock", new NockVerb(), List.of(), one, false, none, none,
                        new Documentation(
                                "COMBAT", "", "Load a missile",
                                "Nocks an arrow. Nocking persists across rounds until cleared. "
                                        + "Fails when already nocked.",
                                List.of("nock", "nock ; shoot g1"))),
                new VerbDefinition(
                        "shoot", new ShootVerb(), List.of(aim),
                        new ApCost.Aimed(two, Balance.AIM_EXTRA_AP),
                        false, none, none,
                        new Documentation(
                                "COMBAT", "<target>", "Ranged attack",
                                "Fires a nocked arrow at an entity or body part with a clear line of fire. "
                                        + "Invalid targets do not consume nocking.",
                                List.of("shoot g1", "shoot o1 -a head", "nock ; shoot g1"))),
                new VerbDefinition(
                        "take", new TakeVerb(), List.of(), one, false, none, items,
                        new Documentation(
                                "ITEMS & PIPELINE", "<address>", "Take or select an item",
                                "Takes an addressed item into the pack, or selects an already carried item. "
                                        + "Taking from another actor attempts a snatch. Emits the selected item.",
                                List.of("take @self", "take /pack/flask_oil | throw o1",
                                        "take o1/hand/right"))),
                new VerbDefinition(
                        "drop", new DropVerb(), List.of(), one, false, items, none,
                        new Documentation(
                                "ITEMS & PIPELINE", "[item]", "Drop a carried item",
                                "Drops the selected or piped carried item onto the current tile.",
                                List.of("drop blade", "take /pack/stone | drop"))),
                new VerbDefinition(
                        "wield", new WieldVerb(), List.of(), one, false, items, none,
                        new Documentation(
                                "ITEMS & PIPELINE", "[item]", "Equip a packed item",
                                "Moves the selected or piped item from the pack into an empty hand. "
                                        + "Prefers the right hand. Fails when both hands are occupied.",
                                List.of("wield sword", "take /pack/torch | wield"))),
                new VerbDefinition(
                        "throw", new ThrowVerb(), List.of(), two, false, items, none,
                        new Documentation(
                                "ITEMS & PIPELINE", "[item] <target>", "Throw a carried item",
                                "Removes the selected item from inventory and throws it at an entity, "
                                        + "body target, or floor tile. Spills any contained substance.",
                                List.of("throw flask_oil o1", "take /pack/flask_oil | throw @3,5"))),
                new VerbDefinition(
                        "pour", new PourVerb(), List.of(), one, false,
                        Set.of(ITEM, SUBSTANCE), none,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "[liquid] <tile|entity>", "Pour liquid",
                                "Pours from a carried container or piped substance onto an adjacent "
                                        + "entity or floor tile. Keeps emptied containers and emits no material.",
                                List.of("pour flask_oil @self", "siphon b1/contents | pour @o1"))),
                new VerbDefinition(
                        "siphon", new SiphonVerb(), List.of(thermal, blood),
                        new ApCost.ByPresence("blood", Balance.AP_SIPHON, Balance.AP_SIPHON_BLOOD),
                        false, none, substances,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "[-t|-b] <source>", "Extract fire, matter, or thermal heat",
                                "Extracts fire (inferno), heat from creatures/thermal (-t), water/oil/acid/gas payloads, "
                                        + "or drains blood from living targets (-b, bonus & consumes BLEEDING). "
                                        + "Unpiped thermal energy (heat/inferno) blasts the caster's tile; "
                                        + "unpiped liquids spill over the caster.",
                                List.of("siphon br1 | bolt o1", "siphon @3,3 -t | bolt o1",
                                        "siphon -b o1"))),
                new VerbDefinition(
                        "bolt", new BoltVerb(), List.of(aim, blood),
                        new ApCost.Aimed(
                                new ApCost.ByPresence("blood", Balance.AP_BOLT_KIN, Balance.AP_BOLT_BLOOD),
                                1),
                        false, Set.of(ITEM, SUBSTANCE), none,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "[-a <part>|-b] <target>", "Fire a single-target psionic bolt",
                                "Delivers kinetic force, piped items/elements, or auto-hit Bloodlance (-b) to a target.",
                                List.of("bolt o1", "bolt -a head o1", "siphon br1 | bolt o1", "bolt -b o1"))),
                new VerbDefinition(
                        "blast", new BlastVerb(), List.of(blood),
                        new ApCost.ByPresence("blood", Balance.AP_BLAST, Balance.AP_BLAST_BLOOD),
                        false, substances, none,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "[-b] <epicenter>", "Detonate a 5-tile cardinal cross",
                                "Saturates a 5-tile cross with kinetic crush, piped elements, or LoS Crimson Nova (-b).",
                                List.of("blast @5,5", "siphon br1 | blast @5,5", "blast -b @5,5"))),
                new VerbDefinition(
                        "push", new PushVerb(false), List.of(),
                        new ApCost.Fixed(Balance.AP_PUSH), false, substances, none,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "<target> [n]", "Telekinetically shove target away",
                                "Moves creatures, wood props, gases, or liquids away along cardinal LoF.",
                                List.of("push o1 2", "push @3,4/gas 3", "channel -f | push o1 4"))),
                new VerbDefinition(
                        "pull", new PushVerb(true), List.of(),
                        new ApCost.Fixed(Balance.AP_PULL), false, substances, items,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "<target> [n]", "Telekinetically pull target toward caster",
                                "Pulls creatures, props, gases, liquids, or catches loose ground items.",
                                List.of("pull o1 2", "pull @4,4/stone | bolt o1", "channel -f | pull @e2/stone | bolt -a head o1"))),
                new VerbDefinition(
                        "channel", new ChannelVerb(), List.of(channelForce, blood),
                        new ApCost.ByPresence("force", Balance.AP_CHANNEL_THERMAL, Balance.AP_CHANNEL_FORCE),
                        false, substances, substances,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "[-f|-b]", "Focus buffer or Blood Surge",
                                "Stores/releases heat/inferno or force focus (-f), promotes heat with siphon, or surges +AP (-b).",
                                List.of("siphon br1 | channel", "channel | bolt o1", "channel -f | push o1 4", "channel -b"))),
                new VerbDefinition(
                        "ignite", new IgniteVerb(), List.of(), one, false, none, none,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "<entity|item|tile>", "Ignite a target",
                                "Requires a lit item in hand and a reachable entity, item, or floor tile. "
                                        + "Does not consume piped material; sequence it after pouring.",
                                List.of("ignite o1", "ignite @o1",
                                        "siphon b1/contents | pour @o1 && ignite @o1")))
        ));
        manual.bind(catalog);
        return catalog;
    }
}