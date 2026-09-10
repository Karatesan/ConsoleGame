package com.archon.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {

    private CommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new CommandParser();
    }

    @Test
    @DisplayName("1. Prosta komenda z argumentami i tekstami w cudzysłowach")
    void testSimpleCommandWithArgs() {
        // Testuje: dzielenie na słowa, cudzysłowy oraz liczby ujemne jako argumenty (nie flagi)
        Ast.Line line = parser.parse("cast \"fire ball\" target -50");

        assertEquals(1, line.stages().size());
        assertFalse(line.isChain());

        Ast.Invocation inv = line.stages().getFirst().pipeline().getFirst();
        assertEquals("cast", inv.verb());
        assertEquals("fire ball", inv.arg(0));
        assertEquals("target", inv.arg(1));
        assertEquals("-50", inv.arg(2)); // Wyryte jako liczba, nie flaga -5
        assertNull(inv.arg(3));
    }

    @Test
    @DisplayName("2. Długie flagi i wartości domyślne (bareDefault)")
    void testLongFlagsAndBareDefault() {
        // --power ma bareDefault = "heavy"
        // --dry-run ustawia flage dryRun w całej linii
        Ast.Line line = parser.parse("strike dragon --power --aim=head --dry-run");

        assertTrue(line.dryRun());
        Ast.Invocation inv = line.stages().getFirst().pipeline().getFirst();

        assertEquals("strike", inv.verb());
        assertEquals("dragon", inv.arg(0));
        assertEquals("heavy", inv.flag("power")); // Wartość pobrana z bareDefault()
        assertEquals("head", inv.flag("aim"));
        assertTrue(inv.hasFlag("dry-run"));
    }

    @Test
    @DisplayName("3. Łączone flagi krótkie (-fcq) oraz flaga krótka z wartością domyślną (-p)")
    void testShortFlagsGroupedAndBareDefault() {
        // -fcq to połączone: -f (force), -c (careful), -q (quiet)
        // -p używa bareDefault ("heavy")
        Ast.Line line = parser.parse("move north -fcq -p");

        Ast.Invocation inv = line.stages().get(0).pipeline().get(0);

        assertEquals("move", inv.verb());
        assertTrue(inv.hasFlag("force"));
        assertTrue(inv.hasFlag("careful"));
        assertTrue(inv.hasFlag("quiet"));
        assertEquals("heavy", inv.flag("power"));
    }

    @Test
    @DisplayName("4. Potok komend (|) wewnątrz jednego etapu")
    void testPipeline() {
        // Trzy komendy w jednym potoku
        Ast.Line line = parser.parse("scan --all | filter --layer=top | render");

        assertEquals(1, line.stages().size());
        assertEquals(3, line.verbCount());
        assertTrue(line.isChain());

        Ast.Stage stage = line.stages().get(0);
        assertEquals(3, stage.pipeline().size());
        assertEquals("scan", stage.pipeline().get(0).verb());
        assertEquals("filter", stage.pipeline().get(1).verb());
        assertEquals("render", stage.last().verb());
    }

    @Test
    @DisplayName("5. Sekwencje i spójniki logiczne (;, &&, ||)")
    void testSequentialStages() {
        // Trzy osobne etapy połączone operatorami logicznymi
        Ast.Line line = parser.parse("aim -a head && strike --power=max || retreat");

        assertEquals(3, line.stages().size());
        assertEquals(3, line.verbCount());

        // Etap 1: aim -a head
        assertEquals("aim", line.stages().get(0).pipeline().get(0).verb());
        assertEquals(Ast.Op.NONE, line.stages().get(0).op());

        // Etap 2: strike --power=max (po &&)
        assertEquals("strike", line.stages().get(1).pipeline().get(0).verb());
        assertEquals(Ast.Op.AND, line.stages().get(1).op());

        // Etap 3: retreat (po ||)
        assertEquals("retreat", line.stages().get(2).pipeline().get(0).verb());
        assertEquals(Ast.Op.OR, line.stages().get(2).op());
    }

    @Test
    @DisplayName("6. Błąd: Nieznana flaga")
    void testUnknownFlagError() {
        CommandParser.ParseError ex = assertThrows(
                CommandParser.ParseError.class,
                () -> parser.parse("attack --unknown")
        );
        assertTrue(ex.getMessage().contains("unknown flag --unknown"));
        assertNotNull(ex.hint); // Powinno zawierać listę znanych flag
    }

    @Test
    @DisplayName("7. Błąd: Brak wymaganej wartości dla flagi bez bareDefault")
    void testMissingRequiredValue() {
        // --aim wymaga wartości i nie ma domyślnej (bareDefault == null)
        CommandParser.ParseError ex = assertThrows(
                CommandParser.ParseError.class,
                () -> parser.parse("attack --aim")
        );
        assertTrue(ex.getMessage().contains("--aim requires a value"));
    }
}