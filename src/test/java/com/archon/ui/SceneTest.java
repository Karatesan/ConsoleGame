package com.archon.ui;

import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.system.environment.SimulationSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneTest {

    @Test
    void enemyPhaseEmitsBeatsAndSceneDiffSeesMovement() {
        World world = Scenario.testRoom(new Dice.Seeded(42L));
        EventBus bus = new EventBus();
        List<GameEvent.ActorBeat> beats = new ArrayList<>();
        bus.subscribe(e -> { if (e instanceof GameEvent.ActorBeat b) beats.add(b); });

        Scene before = new Scene(world);
        SimulationSystem.tick(world, bus);
        Scene after = new Scene(world);

        assertFalse(beats.isEmpty());
        assertEquals("self", after.id[0]);
        for (GameEvent.ActorBeat b : beats) {
            int i = after.indexOf(b.id());
            assertEquals(!b.from().equals(b.to()), after.moved(i, before), b.id());
        }
    }
}
