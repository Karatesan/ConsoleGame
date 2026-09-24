package com.archon.app;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.ui.ConsoleInput;
import com.archon.ui.ConsoleView;
import com.archon.ui.View;

import static com.archon.app.AliasProcessor.ALIAS_PREFIX;

public final class Repl {

    private final AliasProcessor aliasProcessor = new AliasProcessor();

    public void run(long seed) {
        try (ConsoleInput input = new ConsoleInput()) {
            World world = Scenario.testRoom(new Dice.Seeded(seed));
            EventBus bus = new EventBus();
            View view = new ConsoleView();
            bus.subscribe(view::handle);

            RoundState round = new RoundState(EconomyRules.prototype());
            Executor exec = ExecutionSetup.create(world, bus, round);

            bus.post(new GameEvent.RoundStart(round.roundNo(), round.ap()));
            bus.narrate("Type 'help'. Use Up/Down for history. Free commands never cost AP or count as a line.");
            view.frame(world, round);

            while (world.thrall().alive()) {
                String raw = input.readLine();
                if (raw == null) break;
                raw = raw.trim();

                if (raw.equalsIgnoreCase("quit") || raw.equalsIgnoreCase("exit")) break;
                if (raw.isEmpty()) continue;

                if (raw.startsWith(ALIAS_PREFIX)) {
                    aliasProcessor.defineAlias(raw, bus);
                    view.frame(world, round);
                    continue;
                }

                Executor.Outcome outcome = exec.submit(aliasProcessor.expandAliases(raw));
                if (world.thrall().alive() && outcome.roundOver()) {
                    exec.endRound();
                }
                view.frame(world, round);
            }
        }
    }
}