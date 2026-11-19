package com.archon.verb;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.Tag;

import java.util.List;

public final class ScanVerb implements Verb {

    private static final Tag[] SCAN_TAGS = {
            Tag.BURNING, Tag.FROZEN, Tag.CHILLED, Tag.WET, Tag.OIL, Tag.BLEEDING,
            Tag.CORRODED, Tag.STUNNED, Tag.PRONE, Tag.BLIND, Tag.DAZED, Tag.CRIPPLED
    };

    @Override
    public ExitCode execute(VerbContext c) {
        StringBuilder sb = new StringBuilder("VISIBLE:\n");
        List<Entity> entities = c.world.entities();

        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (!e.alive()) {
                continue;
            }

            StringBuilder tags = new StringBuilder();
            for (int t = 0; t < SCAN_TAGS.length; t++) {
                if (e.has(SCAN_TAGS[t])) {
                    tags.append('[').append(SCAN_TAGS[t].name()).append("] ");
                }
            }
            if (e instanceof Actor actor) {
                if (actor.isGuarded()) {
                    tags.append("[GUARDED] ");
                }
                if (actor.readied() != null && !actor.isReadiedSpent()) {
                    tags.append("[READIED: ").append(actor.readied().description()).append("] ");
                }
            }

            int dist = e.pos().chebyshev(c.thrall.pos());
            sb.append(String.format(
                    "  %-5s %-16s %-7s HP %2d/%-2d  %4dC %s%s%n",
                    e.id(),
                    e.name(),
                    e.pos().toString(),
                    e.hp(),
                    e.maxHp(),
                    e.temp,
                    tags,
                    dist <= 1 ? "adjacent" : "range " + dist
            ));
        }

        c.say(sb.toString().stripTrailing());
        return ExitCode.SUCCESS;
    }
}