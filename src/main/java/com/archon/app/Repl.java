package com.archon.app;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.EconomyRules;
import com.archon.exec.Executor;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.ui.ConsoleView;
import com.archon.ui.View;

import java.util.Scanner;

public final class Repl {

    private String lastLine = "";
    private final AliasProcessor aliasProcessor = new AliasProcessor();

    public void run(long seed) {
        World world = Scenario.testRoom(new Dice.Seeded(seed));
        EventBus bus = new EventBus();
        View view = new ConsoleView();
        bus.subscribe(view::handle);

        RoundState round = new RoundState(EconomyRules.prototype());
        Executor exec = ExecutionSetup.create(world, bus, round);

        bus.post(new GameEvent.RoundStart(round.roundNo(), round.ap()));
        bus.narrate("Type 'help'. Free commands never cost AP or count as a line.");
        view.frame(world, round);

        Scanner in = new Scanner(System.in);
        while (world.thrall().alive()) {
            view.prompt();
            if (!in.hasNextLine()) break;
            String raw = in.nextLine().trim();

            if (raw.equalsIgnoreCase("quit") || raw.equalsIgnoreCase("exit")) break;
            if (raw.isEmpty() || raw.equals("!!")) raw = lastLine;
            if (raw.isEmpty()) continue;

            if (raw.startsWith("alias ")) {
                aliasProcessor.defineAlias(raw, bus);
                view.frame(world, round);
                continue;
            }
            raw = aliasProcessor.expandAliases(raw);
            lastLine = raw;

            Executor.Outcome outcome = exec.submit(raw);
            if (world.thrall().alive() && outcome.roundOver()) {
                exec.endRound();
            }
            view.frame(world, round);
        }
    }
}