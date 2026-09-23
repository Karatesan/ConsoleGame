package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.World;

/** Renders events and current state without mutating gameplay. */
public interface View {
    void handle(GameEvent event);
    void frame(World world, RoundState round);
    default void prompt() {}
}