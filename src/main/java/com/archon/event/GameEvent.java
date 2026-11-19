package com.archon.event;

import com.archon.model.Vec2;
import com.archon.verb.ExitCode;
import java.util.List;
import java.util.Objects;

public sealed interface GameEvent {
    record RoundStart(int round, int ap)                                              implements GameEvent {}
    record RoundEnd(int wasted)                                                       implements GameEvent {}
    /** Synthesized by the presenter, never by the simulation: the "enemy is thinking" dots. */
    record Thinking(int dots)                                                         implements GameEvent {}
    record EnemyPhaseStart()                                                          implements GameEvent {}
    /** One actor's whole enemy-phase turn; from == to when it did not move. */
    record ActorBeat(String id, String name, Vec2 from, Vec2 to, List<String> lines)  implements GameEvent {}
    /** Burning damage, blindness recovery and fire spread; ignited lists newly burning tiles. */
    record EnvBeat(List<String> lines, List<Vec2> ignited)                            implements GameEvent {}
    record Rejected(ExitCode code, String reason, String hint)                        implements GameEvent {}
    record LineStart(String raw, int lineNo, int allocation, int tax, boolean chain)   implements GameEvent {}
    record StageStart(int index, int total, String render)                             implements GameEvent {}
    record StageResult(int index, int total, String render, ExitCode code,
                       int charged, int apLeft)                                       implements GameEvent {}
    record StageSkipped(int index, int total, String render, String why)               implements GameEvent {}
    record LineComplete(int charged, int tax, int returnedUnused, int apLeft)          implements GameEvent {}
    record LineBroke(int stageIndex, String reason, int allocation, int penalty,
                     int forfeited, int apLeft, String hint)                          implements GameEvent {}
    record InterruptFired(String source, String description, int damage)               implements GameEvent {}
    record Narrative(String text)                                                     implements GameEvent {
        public Narrative { Objects.requireNonNull(text, "narrative text"); }
    }
    record Redraw()                                                                   implements GameEvent {}
    record ThrallDied()                                                               implements GameEvent {}
}