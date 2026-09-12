package com.archon.verb;

import com.archon.model.Actor;
import com.archon.model.Entity;
import com.archon.model.Tag;

public final class ScanVerb extends FreeVerb {
    @Override public String name() { return "scan"; }
    @Override public String help() { return "scan — list perceived entities. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        StringBuilder sb = new StringBuilder("VISIBLE:\n");
        for (Entity e : c.world.entities.values()) {
            if (!e.alive()) continue;
            sb.append(String.format("  %-5s %-16s %-7s HP %2d/%-2d %s%s%s%n",
                    e.id, e.name, e.pos().toString(), e.hp(), e.maxHp(),
                    e.tags.contains(Tag.BURNING) ? "[BURNING] " : "",
                    e instanceof Actor a && a.readied() != null ? "[READIED: " + a.readied().description() + "] " : "",
                    e.pos().chebyshev(c.thrall.pos()) <= 1 ? "adjacent" : "range " + e.pos().chebyshev(c.thrall.pos())));
        }
        c.say(sb.toString().stripTrailing());
        return ExitCode.SUCCESS;
    }
}