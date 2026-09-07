package com.archon;

import com.archon.app.Scenario;
import com.archon.event.EventBus;
import com.archon.exec.Executor;
import com.archon.exec.RoundState;
import com.archon.model.Dice;
import com.archon.model.World;
import com.archon.verb.ExitCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionEconomyTest {

    private record Rig(World world, Executor exec, RoundState round) {}

    private Rig rig(boolean alwaysHit) {
        World w = Scenario.testRoom(new Dice.Always(alwaysHit));
        EventBus bus = new EventBus();
        RoundState r = new RoundState();
        return new Rig(w, new Executor(w, bus, r), r);
    }
    private Rig rig() { return rig(true); }

    @Test void t01_singleCommandChargesTaxFree() {
        var g = rig();
        var o = g.exec().submit("strike o1");
        assertEquals(Executor.Kind.COMPLETE, o.kind());
        assertEquals(ExitCode.PARTIAL, o.lastCode());
        assertEquals(0, o.tax());
        assertEquals(3, g.round().ap());
        assertEquals(1, g.round().linesUsed());
    }

    @Test void t02_seqChainChargesBothStages() {
        var g = rig();
        var o = g.exec().submit("strike o1 ; step w");
        assertEquals(Executor.Kind.COMPLETE, o.kind());
        assertEquals(3, o.allocation());
        assertEquals(3, o.charged());
        assertEquals(2, g.round().ap());
    }

    @Test void t03_andSkipsOnPartial_skippedStageIsFree() {
        var g = rig();
        var o = g.exec().submit("strike d1 -p heavy && step n");
        assertEquals(Executor.Kind.COMPLETE, o.kind());
        assertEquals(4, o.allocation());
        assertEquals(3, o.charged());        // step n never ran
        assertEquals(2, g.round().ap());
    }

    @Test void t04_orFiresOnMiss() {
        var g = rig(false);
        var o = g.exec().submit("strike o1/head -p heavy || guard");
        assertEquals(Executor.Kind.COMPLETE, o.kind());
        assertEquals(4, o.charged());        // 3 + 1
        assertEquals(1, g.round().ap());
    }

    @Test void t05_orSkippedOnSuccess() {
        var g = rig();
        g.world().get("o1").hp = 4;
        var o = g.exec().submit("strike o1/head -p heavy || guard");
        assertEquals(ExitCode.SUCCESS, o.lastCode());
        assertEquals(3, o.charged());
        assertEquals(2, g.round().ap());
    }

    @Test void t06_runtimeBlockedBreaksAndForfeitsWholeAllocation() {
        var g = rig();
        g.world().get("o1").hp = 5;
        var o = g.exec().submit("strike o1 -p heavy ; take o1/hand/right");
        assertEquals(Executor.Kind.BROKE, o.kind());
        assertEquals(4, o.allocation());
        assertEquals(0, g.round().ap());     // 4 allocation + 1 penalty, clamped
    }

    @Test void t07_staticallyKnownWallIsFreeReject() {
        var g = rig();
        var o = g.exec().submit("step n");
        assertEquals(Executor.Kind.REJECTED, o.kind());
        assertEquals(ExitCode.BLOCKED, o.lastCode());
        assertEquals(5, g.round().ap());
        assertEquals(0, g.round().linesUsed());
    }

    @Test void t08_unknownVerbIsFreeReject() {
        var g = rig();
        var o = g.exec().submit("smite o1");
        assertEquals(Executor.Kind.REJECTED, o.kind());
        assertEquals(ExitCode.INVALID, o.lastCode());
        assertEquals(5, g.round().ap());
        assertEquals(0, g.round().linesUsed());
    }

    @Test void t09_queriesAreUnlimitedAndFree() {
        var g = rig();
        for (int i = 0; i < 20; i++) g.exec().submit("scan");
        assertEquals(5, g.round().ap());
        assertEquals(0, g.round().linesUsed());
    }

    @Test void t10_dryRunIsFree() {
        var g = rig();
        var o = g.exec().submit("strike o1 -n");
        assertEquals(Executor.Kind.AUDIT, o.kind());
        assertEquals(5, g.round().ap());
        assertEquals(0, g.round().linesUsed());
    }

    @Test void t11_secondLinePaysTaxAndFits() {
        var g = rig();
        g.exec().submit("strike o1 -p heavy");       // 3 AP, line 1
        assertEquals(2, g.round().ap());
        var o = g.exec().submit("guard");            // tax 1 + cost 1
        assertEquals(Executor.Kind.COMPLETE, o.kind());
        assertEquals(1, o.tax());
        assertEquals(0, g.round().ap());
    }

    @Test void t12_secondLineRejectedWhenTaxUnaffordable() {
        var g = rig();
        g.exec().submit("strike o1 -p heavy ; step w");   // 4 AP, line 1
        assertEquals(1, g.round().ap());
        var o = g.exec().submit("guard");                // needs 1 tax + 1 = 2
        assertEquals(Executor.Kind.REJECTED, o.kind());
        assertEquals(1, g.round().ap());
        assertEquals(1, g.round().linesUsed());          // slot not consumed
    }

    @Test void t13_pipelineChargesOnlyFinalVerb() {
        var g = rig();
        var o = g.exec().submit("siphon b1/contents | pour @o1/floor | ignite @o1/floor");
        assertEquals(Executor.Kind.COMPLETE, o.kind());
        assertEquals(1, o.allocation());
        assertEquals(1, o.charged());
        assertEquals(4, g.round().ap());
        assertTrue(g.world().get("o1").has(com.archon.model.Tag.BURNING));
    }

    @Test void t14_pipelineWithMissingMaterialIsStaticReject() {
        var g = rig();
        g.world().get("b1").held = null;
        var o = g.exec().submit("siphon b1/contents | pour @o1/floor | ignite @o1/floor");
        assertEquals(Executor.Kind.REJECTED, o.kind());
        assertEquals(5, g.round().ap());
        assertEquals(0, g.round().linesUsed());
    }

    @Test void t15_terminalVerbMidChainIsFreeReject() {
        var g = rig();
        var o = g.exec().submit("strike o1 ; pass ; step w");
        assertEquals(Executor.Kind.REJECTED, o.kind());
        assertEquals(ExitCode.INVALID, o.lastCode());
        assertEquals(5, g.round().ap());
    }

    @Test void t16_deterministicInterruptBreaksChain() {
        var g = rig();
        var o = g.exec().submit("step e ; step e ; strike g1");
        assertEquals(Executor.Kind.BROKE, o.kind());
        assertEquals(4, o.allocation());          // 1 + 1 + 2
        assertEquals(0, g.round().ap());          // 4 + 1 penalty, clamped
        assertTrue(g.world().thrall.hp < 40);     // archer connected
    }

    @Test void t17_unspentApIsDestroyedAtRoundEnd() {
        var g = rig();
        g.exec().submit("strike o1 -p light");    // 1 AP
        assertEquals(4, g.round().ap());
        g.exec().endRound();
        assertEquals(5, g.round().ap());
        assertEquals(0, g.round().linesUsed());
        assertEquals(2, g.round().roundNo());
    }

    @Test void t18_roundOverIsReportedNotApplied() {
        var g = rig();
        var o = g.exec().submit("strike o1 -p heavy ; step w");   // 4 AP
        assertFalse(o.roundOver());
        assertEquals(1, g.round().ap());

        var o2 = g.exec().submit("strike o1 -p light");           // tax 1 + 1 = 2 > 1
        assertEquals(Executor.Kind.REJECTED, o2.kind());

        g.exec().endRound();                                      // caller advances
        assertEquals(5, g.round().ap());
        assertEquals(0, g.round().linesUsed());
    }

    @Test void t19_passEndsRoundWithoutTax() {
        var g = rig();
        g.exec().submit("strike o1 -p light");     // line 1, 1 AP
        var o = g.exec().submit("pass");
        assertEquals(Executor.Kind.FREE, o.kind());
        assertTrue(o.roundOver());
        assertEquals(0, o.tax());
        assertEquals(1, g.round().linesUsed());    // pass never counts as a line
        assertEquals(4, g.round().ap());           // not yet reset — caller decides
    }
}