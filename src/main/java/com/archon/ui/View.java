package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.World;

/** Implement this for a graphical front end; the simulation never changes. */
public interface View {
    void handle(GameEvent event);
    void frame(World world, RoundState round);
    default void prompt() {}
}