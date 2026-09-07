package com.archon.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The single seam between simulation and presentation.
 * A graphical front end subscribes here instead of ConsoleView; no game code changes.
 */
public final class EventBus {
    private final List<Consumer<GameEvent>> listeners = new ArrayList<>();

    public void subscribe(Consumer<GameEvent> l) { listeners.add(l); }
    public void post(GameEvent e) { for (var l : listeners) l.accept(e); }

    public void narrate(String text) { post(new GameEvent.Narrative(text)); }
    public void redraw()             { post(new GameEvent.Redraw()); }
    public void audit(String text)   { post(new GameEvent.Audit(text)); }
}