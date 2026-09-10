package com.archon.verb;

public final class HelpVerb extends FreeVerb {
    @Override public String name() { return "help"; }
    @Override public String help() { return "help [verb | topic] — command manual and reference guides. 0 AP."; }

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
                Verb verb = Verbs.get(query);
                if (verb != null) {
                    c.say(verb.help());
                } else {
                    c.say("no such verb or topic: \"" + query + "\"\nType 'help' to see available commands and topics.");
                }
            }
        }
        return ExitCode.SUCCESS;
    }
}
