package com.archon.ui;

import java.util.regex.Pattern;

/**
 * ANSI escape codes and string formatting utilities for terminal UI.
 */
public final class Ansi {

    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";

    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";

    public static final String CLEAR_SCREEN = "\u001B[H\u001B[2J";

    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;?0-9]*[a-zA-Z]");

    private static boolean enabled = true;

    private Ansi() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Wrap text in one or more ANSI codes followed by RESET if ANSI is enabled.
     */
    public static String style(String text, String... codes) {
        if (!enabled || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder();
        for (String code : codes) {
            sb.append(code);
        }
        sb.append(text).append(RESET);
        return sb.toString();
    }

    /**
     * Strip all ANSI escape sequences from a string.
     */
    public static String strip(String text) {
        if (text == null) return "";
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Compute visible character length ignoring ANSI escape codes.
     */
    public static int length(String text) {
        return strip(text).length();
    }

    /**
     * Pad text to the target visual width by adding trailing spaces.
     */
    public static String padRight(String text, int targetWidth) {
        if (text == null) text = "";
        int visibleLen = length(text);
        if (visibleLen >= targetWidth) return text;
        return text + " ".repeat(targetWidth - visibleLen);
    }

    /**
     * Pad text to the target visual width by adding leading spaces.
     */
    public static String padLeft(String text, int targetWidth) {
        if (text == null) text = "";
        int visibleLen = length(text);
        if (visibleLen >= targetWidth) return text;
        return " ".repeat(targetWidth - visibleLen) + text;
    }
}
