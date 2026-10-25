// src/main/java/com/archon/verb/Check.java
package com.archon.verb;

public record Check(boolean valid, String reason, String hint) {

    private static final Check OK = new Check(true, null, null);

    public static Check ok() {
        return OK;
    }

    public static Check invalid(String reason) {
        return new Check(false, reason, null);
    }

    public static Check invalid(String reason, String hint) {
        return new Check(false, reason, hint);
    }
}