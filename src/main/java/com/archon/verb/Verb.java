package com.archon.verb;

import com.archon.command.Ast;

public interface Verb {
    String name();
    VerbDoc doc();

    /** Free verbs never cost AP, never count as a line, never break a chain. */
    default boolean free() { return false; }
    /** Terminal verbs (pass, resync) may only appear as the final stage. */
    default boolean terminal() { return false; }
    default boolean acceptsMaterial() { return doc() != null && doc().acceptsMaterial(); }
    default boolean producesMaterial() { return doc() != null && doc().producesMaterial(); }

    int apCost(Ast.Invocation inv);

    /** Always run at submission. Syntax, flags, address shape, existence of named ids. */
    Check validateStructural(VerbContext ctx);

    /** Run at submission for the FIRST stage only: adjacency, range, reachability. */
    default Check validateState(VerbContext ctx) { return Check.ok(); }

    ExitCode execute(VerbContext ctx);
}
