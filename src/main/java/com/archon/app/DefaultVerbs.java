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
                        "pass", new PassVerb(), List.of(), zero, true, none, none,
                        new Documentation(
                                "FREE UTILITIES", "", "End the current round",
                                "Ends the round, forfeiting unspent AP. Must be the final command.",
                                List.of("pass", "strike o1 ; pass"))),
                new VerbDefinition(
                        "step", new StepVerb(), List.of(careful),
                        new ApCost.ByPresence("careful", 1, 2),
                        false, none, none,
                        new Documentation(
                                "MOVEMENT", "<dir>", "Advance one tile",
                                "Moves n, s, e, w, ne, nw, se, or sw. Walls and occupants block movement. "
                                        + "Movement may trigger readied reactions.",
                                List.of("step e", "step nw -c", "step w ; strike o1"))),
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
                        "siphon", new SiphonVerb(), List.of(), one, false, none, substances,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "<source>", "Extract one liquid portion",
                                "Consumes substance from a selected container or one liquid tag from a "
                                        + "reachable floor tile. Emits the extracted substance.",
                                List.of("siphon b1/contents", "siphon @self",
                                        "siphon b1/contents | pour @o1"))),
                new VerbDefinition(
                        "ignite", new IgniteVerb(), List.of(), one, false, none, none,
                        new Documentation(
                                "ENVIRONMENT & ELEMENTAL", "<entity|tile>", "Ignite a target",
                                "Requires a lit item in hand and a reachable whole entity or floor tile. "
                                        + "Does not consume piped material; sequence it after pouring.",
                                List.of("ignite o1", "ignite @o1",
                                        "siphon b1/contents | pour @o1 && ignite @o1")))
        ));
        manual.bind(catalog);
        return catalog;
    }
}