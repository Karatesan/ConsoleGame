package com.archon.app;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.Executor;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.ui.ConsoleView;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public final class Repl {

    private final Map<String, String> aliases = new HashMap<>();
    private String lastLine = "";

    public void run(long seed) {
        World world = Scenario.testRoom(new Dice.Seeded(seed));
        EventBus bus = new EventBus();
        ConsoleView view = new ConsoleView();
        bus.subscribe(view::handle);

        RoundState round = new RoundState();
        Executor exec = new Executor(world, bus, round);

        bus.post(new GameEvent.RoundStart(1, RoundState.BASE_AP));
        bus.narrate("Type 'help'. Free commands never cost AP or count as a line.");
        view.frame(world, round);

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("ARCHON> ");
            if (!in.hasNextLine()) break;
            String raw = in.nextLine().trim();

            if (raw.equalsIgnoreCase("quit") || raw.equalsIgnoreCase("exit")) break;
            if (raw.isEmpty() || raw.equals("!!")) raw = lastLine;                 // bare Enter repeats
            if (raw.isEmpty()) continue;

            if (raw.startsWith("alias ")) {
                defineAlias(raw, bus);
                view.frame(world, round);
                continue;
            }
            raw = expandAliases(raw);
            lastLine = raw;

            Executor.Outcome o = exec.submit(raw);

            if (!world.thrall.alive()) {
                view.frame(world, round);
                System.out.println("Run over.");
                break;
            }

            view.frame(world, round);          // show the exhausted round + settlement
            if (o.roundOver()) {
                exec.endRound();               // world tick + AP reset happen here
                view.frame(world, round);      // then the fresh round
            }
        }
    }

    private void defineAlias(String raw, EventBus bus) {
        String body = raw.substring(6).trim();
        int eq = body.indexOf('=');
        if (eq < 0) {
            bus.narrate("usage: alias k=\"strike -p heavy\"");
            return;
        }
        String name = body.substring(0, eq).trim();
        String value = body.substring(eq + 1).trim().replaceAll("^\"|\"$", "");
        aliases.put(name, value);
        bus.narrate("alias " + name + " -> " + value);
    }

    /**
     * Expansion happens before parsing, so verb count and allocation are computed post-expansion.
     */
    private String expandAliases(String raw) {
        String out = raw;
        for (int depth = 0; depth < 5; depth++) {
            String[] parts = out.split("(?=\\s*(;|&&|\\|\\||\\|))");
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            for (String part : parts) {
                String trimmed = part.stripLeading();
                String lead = part.substring(0, part.length() - trimmed.length());
                String[] toks = trimmed.split("\\s+", 2);
                String head = toks.length > 0 ? toks[0].replaceAll("^(;|&&|\\|\\||\\|)\\s*", "") : "";
                String opPrefix = toks.length > 0 ? toks[0].substring(0, toks[0].length() - head.length()) : "";
                if (aliases.containsKey(head)) {
                    changed = true;
                    sb.append(lead).append(opPrefix).append(aliases.get(head));
                    if (toks.length > 1) sb.append(' ').append(toks[1]);
                } else sb.append(part);
            }
            out = sb.toString();
            if (!changed) break;
        }
        return out;
    }
}