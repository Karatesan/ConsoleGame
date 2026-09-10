package com.archon.verb;

import java.util.List;

public final class HelpVerb extends FreeVerb {

    private static final VerbDoc DOC = new VerbDoc(
            "help",
            "Command manual and reference guides",
            "FREE UTILITIES",
            "help [verb | topic]",
            "0 AP (Free action, never counts as a line)",
            "With no arguments, displays the command manual overview and available topics.\n"
                    + "Given a verb name, displays the full manual page for that verb.\n"
                    + "Given a topic name (operators, address, economy, flags), displays the corresponding reference guide.",
            List.of(),
            false,
            false,
            List.of(
                    "help",
                    "help strike",
                    "help operators",
                    "help economy"
            )
    );

    @Override public String name() { return "help"; }
    @Override public VerbDoc doc() { return DOC; }

    @Override
    public ExitCode execute(VerbContext c) {
        String query = c.inv.arg(0);
        if (query == null) {
            c.say(CommandManual.renderIndex());
        } else {
            String doc = CommandManual.lookup(query);
            if (doc != null) {
                c.say(doc);
            } else {
                c.say("no such verb or topic: \"" + query + "\"\nType 'help' to see available commands and topics.");
            }
        }
        return ExitCode.SUCCESS;
    }
}
