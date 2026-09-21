package com.archon.exec;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.model.Actor;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Resolves one pending reaction and publishes its execution event. */
public final class Interrupts {

    private final Supplier<Actor> pending;
    private final ToIntFunction<Actor> resolve;
    private final EventBus bus;

    public Interrupts(
            Supplier<Actor> pending,
            ToIntFunction<Actor> resolve,
            EventBus bus
    ) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.resolve = Objects.requireNonNull(resolve, "resolve");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /** Returns the actor whose reaction fired, or null when none was pending. */
    public Actor fire() {
        Actor source = pending.get();
        if (source != null) {
            int damage = resolve.applyAsInt(source);
            bus.post(new GameEvent.InterruptFired(
                    source.id(),
                    source.readied().description(),
                    damage
            ));
        }
        return source;
    }
}