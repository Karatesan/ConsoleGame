package com.archon.verb;

import com.archon.command.Ast;
import com.archon.event.EventBus;
import com.archon.model.Item;
import com.archon.model.Thrall;
import com.archon.model.World;

public final class VerbContext {
    public final World world;
    public final Thrall thrall;
    public final Ast.Invocation inv;
    public final EventBus bus;
    public final Material materialIn;
    public Material materialOut;
    /** True only for the first stage of a line: full state validation applies. */
    public final boolean firstStage;

    public VerbContext(World world, Ast.Invocation inv, EventBus bus,
                       Material materialIn, boolean firstStage) {
        this.world = world; this.thrall = world.thrall; this.inv = inv;
        this.bus = bus; this.materialIn = materialIn; this.firstStage = firstStage;
    }

    public void say(String text) { bus.narrate(text); }

    public boolean acceptsPipedMaterial() { return materialIn != null; }

    public Item itemFromMaterialOrArg(int argIndex) {
        if (materialIn instanceof Material.OfItem(Item item)) return item;
        String a = inv.arg(argIndex);
        if (a == null) return null;
        String name = a.startsWith("/") ? a.substring(a.lastIndexOf('/') + 1) : a;
        Item i = thrall.findInPack(name);
        if (i != null) return i;
        return thrall.inventory().equipment().values().stream()
                .filter(x -> x != null && (x.id.equalsIgnoreCase(name) || x.name.equalsIgnoreCase(name)))
                .findFirst().orElse(null);
    }
}