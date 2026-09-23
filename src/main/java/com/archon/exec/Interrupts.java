package com.archon.exec;

import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.system.combat.Reaction;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Resolves one reaction and publishes the facts captured by that resolution. */
public final class Interrupts {

    private final Supplier<Optional<Reaction>> resolve;
    private final EventBus bus;

    public Interrupts(Supplier<Optional<Reaction>> resolve, EventBus bus) {
        this.resolve = Objects.requireNonNull(resolve, "resolve");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    public Optional<Reaction> fire() {
        Optional<Reaction> reaction = Objects.requireNonNull(resolve.get(), "reaction result");
        reaction.ifPresent(result -> bus.post(
                new GameEvent.InterruptFired(result.sourceId(), result.description(), result.damage())));
        return reaction;
    }
}