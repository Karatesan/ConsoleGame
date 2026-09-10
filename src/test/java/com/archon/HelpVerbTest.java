package com.archon;

import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.event.GameEvent;
import com.archon.exec.Executor;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.verb.CommandManual;
import com.archon.verb.ExitCode;
import com.archon.verb.VerbDoc;
import com.archon.verb.Verbs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HelpVerbTest {

    @Test
    void testCommandManualHasDocsForAllRegisteredVerbs() {
        for (String verbName : Verbs.names()) {
            VerbDoc doc = CommandManual.getVerbDoc(verbName);
            assertNotNull(doc, "Verb '" + verbName + "' should have a documentation entry in CommandManual");
            assertFalse(doc.summary().isBlank());
            assertFalse(doc.synopsis().isBlank());
            assertFalse(doc.category().isBlank());
            assertFalse(doc.description().isBlank());
            assertFalse(doc.examples().isEmpty(), "Verb '" + verbName + "' should have examples");
        }
    }

    @Test
    void testCommandManualHasRequiredTopics() {
        assertNotNull(CommandManual.renderTopic("operators"));
        assertNotNull(CommandManual.renderTopic("address"));
        assertNotNull(CommandManual.renderTopic("economy"));
        assertNotNull(CommandManual.renderTopic("flags"));

        assertTrue(CommandManual.renderTopic("operators").contains("&&"));
        assertTrue(CommandManual.renderTopic("address").contains("@self"));
        assertTrue(CommandManual.renderTopic("economy").contains("LINE TAX"));
        assertTrue(CommandManual.renderTopic("flags").contains("--dry-run"));
    }

    @Test
    void testHelpBareCommandPrintsIndex() {
        World w = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState r = new RoundState();
        List<String> messages = new ArrayList<>();
        bus.subscribe(e -> {
            if (e instanceof GameEvent.Narrative n) {
                messages.add(n.text());
            }
        });

        Executor exec = new Executor(w, bus, r);
        Executor.Outcome o = exec.submit("help");

        assertEquals(Executor.Kind.FREE, o.kind());
        assertEquals(5, r.ap());
        assertEquals(0, r.linesUsed());
        assertFalse(messages.isEmpty());

        String output = messages.get(messages.size() - 1);
        assertTrue(output.contains("ARCHON COMMAND MANUAL"));
        assertTrue(output.contains("COMBAT:"));
        assertTrue(output.contains("strike"));
        assertTrue(output.contains("FREE UTILITIES:"));
        assertTrue(output.contains("REFERENCE TOPICS:"));
    }

    @Test
    void testHelpWithVerbPrintsDetailedManPage() {
        World w = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState r = new RoundState();
        List<String> messages = new ArrayList<>();
        bus.subscribe(e -> {
            if (e instanceof GameEvent.Narrative n) {
                messages.add(n.text());
            }
        });

        Executor exec = new Executor(w, bus, r);
        Executor.Outcome o = exec.submit("help strike");

        assertEquals(Executor.Kind.FREE, o.kind());
        assertFalse(messages.isEmpty());

        String output = messages.get(messages.size() - 1);
        assertTrue(output.contains("NAME:"));
        assertTrue(output.contains("strike — Melee attack or disarm attempt"));
        assertTrue(output.contains("SYNOPSIS:"));
        assertTrue(output.contains("OPTIONS & FLAGS:"));
        assertTrue(output.contains("--aim"));
        assertTrue(output.contains("EXAMPLES:"));
        assertTrue(output.contains("strike o1 || guard"));
    }

    @Test
    void testHelpWithTopicPrintsGuide() {
        World w = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState r = new RoundState();
        List<String> messages = new ArrayList<>();
        bus.subscribe(e -> {
            if (e instanceof GameEvent.Narrative n) {
                messages.add(n.text());
            }
        });

        Executor exec = new Executor(w, bus, r);
        Executor.Outcome o = exec.submit("help operators");

        assertEquals(Executor.Kind.FREE, o.kind());
        assertFalse(messages.isEmpty());

        String output = messages.get(messages.size() - 1);
        assertTrue(output.contains("TOPIC: COMMAND OPERATORS"));
        assertTrue(output.contains("Sequential execution"));
    }

    @Test
    void testHelpWithUnknownQueryProvidesGuidance() {
        World w = Scenario.testRoom(new Dice.Always(true));
        EventBus bus = new EventBus();
        RoundState r = new RoundState();
        List<String> messages = new ArrayList<>();
        bus.subscribe(e -> {
            if (e instanceof GameEvent.Narrative n) {
                messages.add(n.text());
            }
        });

        Executor exec = new Executor(w, bus, r);
        Executor.Outcome o = exec.submit("help nonexistent");

        assertEquals(Executor.Kind.FREE, o.kind());
        assertFalse(messages.isEmpty());

        String output = messages.get(messages.size() - 1);
        assertTrue(output.contains("no such verb or topic"));
    }
}
