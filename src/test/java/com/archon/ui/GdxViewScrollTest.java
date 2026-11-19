package com.archon.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdxViewScrollTest {

    @Test
    void clampScrollBoundsToVisibleWindow() {
        assertEquals(0f, GdxView.clampScroll(-5f, 30, 9));
        assertEquals(21f, GdxView.clampScroll(50f, 30, 9));
        assertEquals(0f, GdxView.clampScroll(3f, 6, 9));
        assertEquals(7.4f, GdxView.clampScroll(7.4f, 30, 9), 1e-5f);
    }

    @Test
    void thumbSizeScalesAndEnforcesMinimum() {
        assertEquals(110f, GdxView.thumbSize(220f, 18, 9), 1e-4f);
        assertEquals(20f, GdxView.thumbSize(220f, 500, 9), 1e-4f);
    }

    @Test
    void scrollFromThumbMapsTrackSpanLinearly() {
        float trackY = 69f, trackH = 220f, thumbH = 40f;
        int maxScroll = 30;
        assertEquals(0f, GdxView.scrollFromThumb(trackY, trackY, trackH, thumbH, maxScroll), 1e-4f);
        assertEquals(15f, GdxView.scrollFromThumb(trackY + 90f, trackY, trackH, thumbH, maxScroll), 1e-4f);
        assertEquals(30f, GdxView.scrollFromThumb(trackY + trackH - thumbH, trackY, trackH, thumbH, maxScroll), 1e-4f);
        assertEquals(0f, GdxView.scrollFromThumb(trackY - 50f, trackY, trackH, thumbH, maxScroll), 1e-4f);
        assertEquals(30f, GdxView.scrollFromThumb(trackY + trackH + 50f, trackY, trackH, thumbH, maxScroll), 1e-4f);
    }

    @Test
    void inputCursorMovesEditsMidStringAndClamps() {
        GdxView view = new GdxView(1L, true);
        for (char c : "botl r1".toCharArray()) view.editInputChar(c);
        assertEquals("botl r1", view.inputText());
        assertEquals(7, view.cursorPos());

        view.moveCursor(-4); // between "bot" and "l r1" -> index 3
        assertEquals(3, view.cursorPos());
        view.editInputChar('\b'); // deletes 't' -> "bol r1", cursor=2
        view.moveCursor(1); // after 'l' -> index 3
        view.editInputChar('t'); // inserts 't' -> "bolt r1", cursor=4
        assertEquals("bolt r1", view.inputText());
        assertEquals(4, view.cursorPos());

        view.moveCursor(-100);
        assertEquals(0, view.cursorPos());
        view.editInputChar('\b'); // no-op at 0
        view.deleteForward(); // deletes 'b' -> "olt r1"
        assertEquals("olt r1", view.inputText());
        view.editInputChar('b');
        view.moveCursor(100);
        assertEquals(7, view.cursorPos());
        assertEquals("bolt r1", view.inputText());
    }
}
