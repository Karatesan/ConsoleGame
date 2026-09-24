package com.archon.ui;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Owns editable terminal input and in-memory command history. */
public final class ConsoleInput implements AutoCloseable {

    private final Terminal terminal;
    private final LineReader reader;

    public ConsoleInput() {
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(false)
                    .build();
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Cannot open an interactive terminal. Run Archon from a terminal window.",
                    exception);
        }

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
    }

    /** Returns null on end of input, or an empty string when input is cancelled. */
    public String readLine() {
        try {
            return reader.readLine("\nARCHON> ");
        } catch (UserInterruptException exception) {
            return "";
        } catch (EndOfFileException exception) {
            return null;
        }
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot close the terminal cleanly.", exception);
        }
    }
}