package com.archon.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnsiTest {

    @BeforeEach
    void setUp() {
        Ansi.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        Ansi.setEnabled(true);
    }

    @Test
    void testStyleWhenEnabled() {
        String colored = Ansi.style("hello", Ansi.RED, Ansi.BOLD);
        assertEquals("\u001B[31m\u001B[1mhello\u001B[0m", colored);
    }

    @Test
    void testStyleWhenDisabled() {
        Ansi.setEnabled(false);
        String colored = Ansi.style("hello", Ansi.RED, Ansi.BOLD);
        assertEquals("hello", colored);
    }

    @Test
    void testStripRemovesAnsiCodes() {
        String styled = Ansi.style("TARGET: Orc Guard", Ansi.BRIGHT_RED, Ansi.BOLD);
        assertEquals("TARGET: Orc Guard", Ansi.strip(styled));

        assertEquals("", Ansi.strip(null));
        assertEquals("plain text", Ansi.strip("plain text"));
    }

    @Test
    void testLengthIgnoresAnsiCodes() {
        String plain = "hello world";
        String styled = Ansi.style(plain, Ansi.GREEN, Ansi.BOLD);

        assertEquals(11, Ansi.length(plain));
        assertEquals(11, Ansi.length(styled));
        assertEquals(0, Ansi.length(null));
    }

    @Test
    void testPadRightConsidersVisibleLength() {
        String plain = "test";
        assertEquals("test      ", Ansi.padRight(plain, 10));

        String styled = Ansi.style("test", Ansi.CYAN);
        String padded = Ansi.padRight(styled, 10);
        assertEquals(10, Ansi.length(padded));
        assertEquals("test      ", Ansi.strip(padded));
    }

    @Test
    void testPadLeftConsidersVisibleLength() {
        String styled = Ansi.style("42", Ansi.YELLOW);
        String padded = Ansi.padLeft(styled, 5);
        assertEquals(5, Ansi.length(padded));
        assertEquals("   42", Ansi.strip(padded));
    }
}
