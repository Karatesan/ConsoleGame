package com.archon.app;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.Interrupts;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.system.combat.ReactionSystem;
import com.archon.system.environment.SimulationSystem;
import com.archon.verb.VerbCatalog;

import static com.archon.app.AliasProcessor.ALIAS_PREFIX;

/** Wires one run of the simulation; shared by every front end (console, libGDX). */
public final class Game {
    public final World world;
    public final EventBus bus = new EventBus();
    public final RoundState round;
    private final Executor exec;
    private final AliasProcessor aliases = new AliasProcessor();

    public Game(long seed) {
        this(seed, true);
    }

    public Game(long seed, boolean magicArena) {
        Dice dice = new Dice.Seeded(seed);
        world = magicArena ? Scenario.magicArena(dice) : Scenario.testRoom(dice);
        round = new RoundState(EconomyRules.prototype());
        VerbCatalog catalog = DefaultVerbs.create(round.rules());
        Interrupts interrupts = new Interrupts(() -> ReactionSystem.fire(world), bus);
        exec = new Executor(world, bus, round, catalog, interrupts, () -> SimulationSystem.tick(world, bus));
    }

    /** Call after the front end has subscribed to the bus. */
    public void start() {
        bus.post(new GameEvent.RoundStart(round.roundNo(), round.ap()));
        bus.narrate("Type 'help'. Free commands never cost AP or count as a line.");
    }

    public void submit(String raw) {
        if (raw.startsWith(ALIAS_PREFIX)) aliases.defineAlias(raw, bus);
        else exec.submit(aliases.expandAliases(raw));
    }
}
