package com.archon.verb;

public final class HelpVerb extends FreeVerb {
    @Override public String name() { return "help"; }
    @Override public String help() { return "help [verb] — syntax. 0 AP."; }

    @Override
    public ExitCode execute(VerbContext c) {
        String v = c.inv.arg(0);
        if (v == null) {
            c.say("verbs: " + String.join(" ", Verbs.names())
                    + "\noperators: ;  &&  ||  |     flags: -a -p -f -c -n"
                    + "\nfree: scan inspect status ls help map pass  (never cost AP, never a line)");
        } else {
            Verb verb = Verbs.get(v);
            c.say(verb == null ? "no such verb: " + v : verb.help());
        }
        return ExitCode.SUCCESS;
    }
}
