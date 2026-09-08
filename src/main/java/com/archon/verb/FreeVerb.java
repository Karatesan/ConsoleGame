package com.archon.verb;

import com.archon.command.Ast;

/** Base class for free query verbs that never cost AP or count as lines. */
public abstract class FreeVerb implements Verb {
    @Override public boolean free() { return true; }
    @Override public int apCost(Ast.Invocation inv) { return 0; }
    @Override public Check validateStructural(VerbContext c) { return Check.ok(); }
}
