package com.archon.verb;

/**
 * Result of a submission-time check.
 * NOTE: the component is `valid`, not `ok` — a record component named `ok`
 * would collide with the static factory Check.ok().
 */
public record Check(boolean valid, ExitCode code, String reason, String hint) {

    private static final Check OK = new Check(true, ExitCode.SUCCESS, null, null);

    public static Check ok() { return OK; }

    public static Check invalid(String reason, String hint) {
        return new Check(false, ExitCode.INVALID, reason, hint);
    }

    public static Check blocked(String reason, String hint) {
        return new Check(false, ExitCode.BLOCKED, reason, hint);
    }
}