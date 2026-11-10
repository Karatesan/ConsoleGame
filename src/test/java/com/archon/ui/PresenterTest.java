package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.system.environment.SimulationSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PresenterTest {

    @Test
    void enemyPhaseIsQueuedThenPlayedInOrder() {
        World world = Scenario.testRoom(new Dice.Seeded(42L));
        List<String> played = new ArrayList<>();
        int[] scenes = {0};
        View view = new View() {
            public void handle(GameEvent e) { played.add(e.getClass().getSimpleName()); }
            public void frame(World w, RoundState r) {}
            public void scene(Scene s, Scene prev) { scenes[0]++; }
        };
        Presenter presenter = new Presenter(view, world);
        EventBus bus = new EventBus();
        bus.subscribe(presenter::handle);

        bus.post(new GameEvent.Redraw());
        assertEquals(List.of("Redraw"), played, "player phase passes straight through");

        bus.post(new GameEvent.EnemyPhaseStart());
        SimulationSystem.tick(world, bus);
        bus.post(new GameEvent.RoundEnd(0));
        assertEquals(1, played.size(), "enemy phase is held back");
        assertTrue(presenter.busy());

        float t = 0;
        while (presenter.busy() && t < 60) { presenter.update(0.1f); t += 0.1f; }

        assertTrue(t >= 1.2f, "dots take time");
        assertEquals("Thinking", played.get(2));
        assertEquals("RoundEnd", played.get(played.size() - 1));
        assertTrue(played.contains("ActorBeat"));
        assertTrue(scenes[0] > 0);
    }

    @Test
    void multiStagePlayerChainIsPacedWithScenePerStage() {
        World world = Scenario.testRoom(new Dice.Seeded(42L));
        List<String> played = new ArrayList<>();
        int[] scenes = {0};
        View view = new View() {
            public void handle(GameEvent e) { played.add(e.getClass().getSimpleName()); }
            public void frame(World w, RoundState r) {}
            public void scene(Scene s, Scene prev) { scenes[0]++; }
        };
        Presenter presenter = new Presenter(view, world);
        EventBus bus = new EventBus();
        bus.subscribe(presenter::handle);

        bus.post(new GameEvent.LineStart("step e && strike o1", 1, 3, 1, true));
        bus.post(new GameEvent.StageStart(1, 2, "step e"));
        bus.post(new GameEvent.StageResult(1, 2, "step e", com.archon.verb.ExitCode.SUCCESS, 1, 4));
        bus.post(new GameEvent.StageStart(2, 2, "strike o1"));
        bus.post(new GameEvent.StageResult(2, 2, "strike o1", com.archon.verb.ExitCode.SUCCESS, 2, 2));
        bus.post(new GameEvent.LineComplete(3, 1, 0, 2));

        assertTrue(presenter.busy(), "chain stages are queued for sequential playback");
        assertTrue(played.isEmpty(), "nothing played before update()");

        presenter.update(0.01f); // plays LineStart, StageStart(1), StageResult(1) and pauses
        assertEquals(List.of("LineStart", "StageStart", "StageResult"), played);
        assertEquals(2, scenes[0]);
        assertTrue(presenter.busy(), "waiting for stage 1 slide before stage 2");

        while (presenter.busy()) presenter.update(0.2f);
        assertEquals(List.of("LineStart", "StageStart", "StageResult", "StageStart", "StageResult", "LineComplete"), played);
        assertEquals(3, scenes[0]);
    }
}
