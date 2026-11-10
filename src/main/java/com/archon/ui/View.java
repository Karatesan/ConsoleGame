package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.World;

/** Renders events and current state without mutating gameplay. */
public interface View {
    void handle(GameEvent event);
    void frame(World world, RoundState round);
    /** Draws an intermediate state during enemy-phase playback; prev is the previously shown scene. */
    void scene(Scene scene, Scene prev);
}