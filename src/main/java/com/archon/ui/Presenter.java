package com.archon.ui;

import com.archon.event.GameEvent;
import com.archon.model.World;

import java.util.ArrayDeque;

/**
 * Sits between the bus and a View. The simulation runs synchronously, so a whole command chain
 * or enemy phase arrives in one burst; multi-stage lines and enemy phases are queued and played
 * back one beat at a time by update(dt). Pump update() from any loop: a console drain loop today,
 * a render frame later.
 */
public final class Presenter {
    private static final float DOT_DELAY = 0.5f, BEAT_DELAY = 0.9f, LINE_DELAY = 0.4f, STAGE_DELAY = 0.55f;

    private record Beat(GameEvent event, Scene scene, float delay) {}

    private final View view;
    private final World world;
    private final ArrayDeque<Beat> queue = new ArrayDeque<>();
    private boolean buffering;
    private float wait;
    private Scene shown;
    /** 0 skips all pauses (tests, piped input). */
    public float timeScale = 1f;

    public Presenter(View view, World world) {
        this.view = view;
        this.world = world;
    }

    public void prime(Scene initial) {
        this.shown = initial;
    }

    public void handle(GameEvent e) {
        if (e instanceof GameEvent.LineStart ls) {
            if (ls.chain()) {
                buffering = true;
                queue.add(new Beat(e, new Scene(world), 0));
            } else {
                view.handle(e);
            }
        } else if (e instanceof GameEvent.EnemyPhaseStart) {
            buffering = true;
            queue.add(new Beat(e, new Scene(world), 0));
            for (int dots = 1; dots <= 3; dots++) queue.add(new Beat(new GameEvent.Thinking(dots), null, DOT_DELAY));
        } else if (!buffering) {
            view.handle(e);
        } else if (e instanceof GameEvent.StageResult sr) {
            queue.add(new Beat(e, new Scene(world), sr.total() > 1 ? STAGE_DELAY : 0));
        } else if (e instanceof GameEvent.InterruptFired || e instanceof GameEvent.LineBroke) {
            queue.add(new Beat(e, new Scene(world), STAGE_DELAY));
        } else if (e instanceof GameEvent.ActorBeat beat) {
            queue.add(new Beat(e, new Scene(world), BEAT_DELAY + LINE_DELAY * beat.lines().size()));
        } else if (e instanceof GameEvent.EnvBeat beat) {
            queue.add(new Beat(e, new Scene(world), BEAT_DELAY + LINE_DELAY * beat.lines().size()));
        } else {
            queue.add(new Beat(e, null, 0));
        }
    }

    public boolean busy() {
        return !queue.isEmpty() || wait > 0;
    }

    public void update(float dt) {
        wait -= dt;
        while (!queue.isEmpty() && wait <= 0) {
            Beat b = queue.poll();
            view.handle(b.event);
            if (b.scene != null) {
                view.scene(b.scene, shown);
                shown = b.scene;
            }
            wait = b.delay * timeScale;
        }
        if (queue.isEmpty() && wait <= 0) buffering = false;
    }
}
