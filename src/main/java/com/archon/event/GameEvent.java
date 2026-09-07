package com.archon.event;

import com.archon.verb.ExitCode;
import java.util.List;

public sealed interface GameEvent {
    record RoundStart(int round, int ap)                                              implements GameEvent {}
    record RoundEnd(int wasted, List<String> worldLog)                                implements GameEvent {}
    record Rejected(ExitCode code, String reason, String hint)                        implements GameEvent {}
    record LineStart(String raw, int lineNo, int allocation, int tax, boolean chain)   implements GameEvent {}
    record StageResult(int index, int total, String render, ExitCode code,
                       int charged, int apLeft)                                       implements GameEvent {}
    record StageSkipped(int index, String render, String why)                          implements GameEvent {}
    record LineComplete(int charged, int tax, int returnedUnused, int apLeft)          implements GameEvent {}
    record LineBroke(int stageIndex, String reason, int allocation, int penalty,
                     int forfeited, int apLeft, String hint)                          implements GameEvent {}
    record InterruptFired(String source, String description, int damage)               implements GameEvent {}
    record Narrative(String text)                                                     implements GameEvent {}
    record Audit(String text)                                                         implements GameEvent {}
    record Redraw()                                                                   implements GameEvent {}
    record ThrallDied()                                                               implements GameEvent {}
}