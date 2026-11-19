package com.archon.app;

import com.archon.ui.ConsoleInput;
import com.archon.ui.ConsoleView;
import com.archon.ui.Presenter;
import com.archon.ui.View;

public final class Repl {

    /** Plays queued enemy-phase beats in real time; the prompt only returns once they are done. */
    private static void drain(Presenter presenter) {
        long last = System.nanoTime();
        while (presenter.busy()) {
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            long now = System.nanoTime();
            presenter.update((now - last) / 1e9f);
            last = now;
        }
    }

    public void run(long seed) {
        run(seed, true);
    }

    public void run(long seed, boolean magicArena) {
        try (ConsoleInput input = new ConsoleInput()) {
            Game game = new Game(seed, magicArena);
            View view = new ConsoleView();
            Presenter presenter = new Presenter(view, game.world);
            game.bus.subscribe(presenter::handle);
            game.start();
            view.frame(game.world, game.round);

            while (game.world.thrall().alive()) {
                String raw = input.readLine();
                if (raw == null) break;
                raw = raw.trim();

                if (raw.equalsIgnoreCase("quit") || raw.equalsIgnoreCase("exit")) break;
                if (raw.isEmpty()) continue;

                game.submit(raw);
                drain(presenter);
                view.frame(game.world, game.round);
            }
        }
    }
}
