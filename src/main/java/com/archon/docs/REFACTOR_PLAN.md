# Archon Architecture Refactor Plan

## Purpose

This document records the architecture review, the agreed refactoring plan, and implementation progress.

Archon is a console roguelike controlled through Unix-like commands. The goal of this refactor is not to introduce enterprise-style abstraction or defensive checks everywhere. The goal is to establish clear ownership boundaries so new gameplay mechanics can be added without modifying many unrelated classes.

The working rule is:

> Be strict at API and ownership boundaries, and keep trusted gameplay code and hot loops lean.

When an API changes, all callers must be migrated directly. No compatibility shims, deprecated wrappers, temporary overloads, or legacy fallbacks should be added.

## Core API boundaries

The following types are treated as the application's core API boundaries:

- Model: `World`, `Entity`, `Actor`, `Thrall`, `Prop`, `Door`, `Inventory`, `Item`, `CreatureStats`, `EquipmentSlot`, `Tag`, `BodyPart`, `Vec2`, `Dice`.
- Command/gameplay: `Verb`, `VerbContext`, `Verbs`, `Material`, `Check`, `ExitCode`.
- Parsing/addressing: `CommandParser`, `Ast`, `Address`, `Resolved`.
- Execution economy: `Executor`, `Validator`, `PipelineRunner`, `StageRunner`, `SettlementManager`, `RoundState`, `Auditor`.
- Game systems: `CombatEngine`, `ReactionSystem`, `SimulationSystem`, `SpatialService`.
- Presentation seam: `EventBus`, `GameEvent`, `View`, `ConsoleView`.

Ownership rules:

- `Entity` owns common entity state.
- `Actor` owns actor-specific state.
- `Inventory` owns item placement.
- `Item` owns durability and substance state.
- `Resolved` owns command-address interpretation.
- Verbs orchestrate actions.
- Systems own reusable cross-entity rules.
- Executor classes own command, AP, and pipeline flow.
- Presentation receives `GameEvent` values instead of being called directly by model or system logic.

## Initial architecture review

The prototype has a good architectural direction, but the initial review concluded that it needed a focused stabilization pass before broad feature development.

### Existing strengths

- The package split between model, verbs, execution, systems, addressing, events, and UI is sensible.
- `Inventory` already owns most item placement and limits direct access to its collections.
- `Item` owns durability and substance state.
- `CombatEngine`, `ReactionSystem`, `SimulationSystem`, and `SpatialService` are a good basis for reusable game rules.
- Command execution is already divided among `Executor`, `Validator`, `StageRunner`, `PipelineRunner`, and `SettlementManager`.
- `GameEvent` and `EventBus` establish a presentation seam.
- Injected `Dice` permits deterministic gameplay tests.
- Value-oriented types such as `Vec2`, `CreatureStats`, `Check`, and `Material` are appropriate.

### Main concerns found by the review

1. `World` was the largest coupling point. It exposed mutable entities, map, dimensions, thrall, dice, round state, and movement state, while also forwarding calls to game systems.
2. Actor-specific reaction, guard, and body state was stored on `Entity`, allowing non-actors to enter actor-only states.
3. Address resolution used nulls and encoded container strings, forcing verbs to interpret address syntax.
4. Verb registration, flags, costs, and documentation were maintained in separate global registries and could drift.
5. Inventory ownership was stronger than map ownership; callers directly mutated ground-item and tile-tag collections.
6. Execution classes were separated but still shared static registry knowledge and inconsistent free/paid pipeline semantics.
7. Several verbs implemented reusable transfer, environment, and cross-entity rules directly.
8. Presentation received events but also read the complete mutable model.
9. Core invariants such as duplicate IDs, HP bounds, durability bounds, AP overspending, and invalid dice ranges were not consistently enforced.

## Refactoring plan

### Priority 1 — stabilize ownership and core boundaries

- **`model/World.java`** -> Make fields private; expose read-only entity iteration and explicit lookup/add/remove operations; reject duplicate IDs; require explicit thrall installation; remove public mutation of dice, round counters, and movement flags.
- **`model/World.java`** -> Remove forwarding methods that call `SpatialService`, `ReactionSystem`, and `SimulationSystem`; orchestration code should invoke systems explicitly.
- **`model/GameMap.java`** -> Own the tile type instead of storing `World.Tile`; make dimensions private; provide controlled tile queries and mutations.
- **`model/World.java`, `model/GameMap.java`** -> Replace mutable wall, tags, ceiling, and ground collections with read-only views and named operations for terrain, tile tags, and ground items.
- **`model/Entity.java`** -> Retain common identity, position, health, defenses, tags, and identification state; remove actor-only reaction, guard, and body-condition state.
- **`model/Actor.java`** -> Own reactions, guard, body-part condition, inventory, and weapon-readiness state; make inventory final.
- **`model/WeaponState.java`, `model/Actor.java`** -> Choose one owner for nocking state. Keep the simple state in `Actor` and remove unused duplicate state unless the concept grows enough to justify its own class.
- **`model/Inventory.java`** -> Add explicit transfer-oriented operations and prevent setup placement from silently overwriting occupied equipment slots.
- **`model/Item.java`** -> Define durability semantics and validate construction without imposing speculative gameplay restrictions.
- **`model/Entity.java`** -> Enforce HP invariants while leaving potentially useful negative armor/evasion values available for debuffs.
- **`model/Dice.java`** -> Reject invalid percentages and invalid ranges rather than silently manufacturing results.

### Priority 2 — make addressing typed and authoritative

- **`address/Address.java`** -> Keep this type limited to parsed unresolved syntax. Move world-relative interpretation out of it.
- **`address/Resolved.java`** -> Make this the authoritative address-interpretation boundary; replace nullable resolution and nullable item values with explicit variants or a structured result.
- **`address/Resolved.java`** -> Replace raw container strings with typed locations such as pack root, packed item, equipment slot, prop contents, and ground location.
- **`address/Resolved.java`** -> Return structured resolution failures instead of null for ordinary invalid addresses.
- **`verb/VerbHelpers.java`** -> Adapt helpers to the typed resolution contract and remove syntax interpretation.
- **`verb/StrikeVerb.java`** -> Consume typed equipment ownership instead of parsing container strings.
- **`verb/TakeVerb.java`** -> Consume typed item locations instead of reconstructing owners from strings.
- **`verb/SiphonVerb.java`** -> Use resolved ownership/location data instead of reparsing the original address.
- **All address call sites** -> Migrate directly to the new contract without compatibility overloads.

### Priority 3 — make verbs extensible and execution injectable

- **`verb/Verbs.java`** -> Replace the static global registry with an immutable injectable verb catalog assembled at application composition time; reject duplicate names.
- **`exec/Executor.java`** -> Receive parser, verb catalog, systems, event publisher, and round state through construction rather than creating or statically locating them.
- **`exec/Validator.java`** -> Use the same injected catalog as execution; return validation data without depending on presentation infrastructure.
- **`exec/Auditor.java`** -> Use injected lookup/cost policy and read-only world queries.
- **`exec/StageRunner.java`** -> Stop calling `Executor.stageCost`; use a shared execution/cost policy.
- **`exec/SettlementManager.java`** -> Stop looking up terminal verbs statically; consume execution-plan data or the injected catalog.
- **`command/FlagSpec.java`, `command/CommandParser.java`** -> Remove the global gameplay flag registry from parsing; parse generic flags or derive accepted options from verb metadata.
- **`verb/Verb.java`** -> Define one metadata contract for names, flags, AP cost, material input/output, free/terminal behavior, and documentation.
- **`verb/CommandManual.java`** -> Generate command pages from verb metadata; retain only cross-command topics manually.
- **`command/Ast.java`** -> Defensively copy collections and validate non-empty invocation/pipeline invariants.

### Priority 4 — unify execution and pipeline semantics

- **`verb/Material.java`** -> Preserve typed item/substance variants and expose material kinds through verb metadata.
- **`exec/Validator.java`** -> Replace fake placeholder material with material-kind propagation through a validation plan.
- **`exec/PipelineRunner.java`** -> Remove broad `RuntimeException` handling; expected gameplay failures should use domain results while programming errors remain visible.
- **`exec/PipelineRunner.java`, `exec/StageRunner.java`** -> Use one interpretation path for free and paid verbs so pipelines, operators, exit codes, and material propagation behave consistently.
- **`exec/StageRunner.java`** -> Return a complete trace of executed/skipped stages, costs, outputs, and interruptions.
- **`exec/RoundState.java`** -> Reject overspending instead of silently clamping; keep mutable test setup in tests rather than production hooks.
- **`exec/RoundState.java`, `verb/CommandManual.java`** -> Choose one line-tax rule and make implementation and documentation agree.

### Priority 5 — move reusable gameplay rules into systems

- **`system/combat/CombatEngine.java`** -> Generalize attackers from `Thrall` to `Actor`; retain one authoritative ranged-combat contract.
- **`system/combat/CombatEngine.java`** -> Return complete combat outcomes and place drops through owned map operations.
- **`system/combat/ReactionSystem.java`** -> Operate on actor-owned reaction state and return typed reaction outcomes.
- **`system/environment/SimulationSystem.java`** -> Own ignition, extinguishing, spilling, extraction, and propagation rules.
- **New item-transfer system or focused model service** -> Centralize atomic movement among inventory, equipment, prop contents, and ground.
- **`system/spatial/SpatialService.java`** -> Consume read-only world/map queries and separate occupancy from hostility rules.
- **`verb/StepVerb.java`** -> Orchestrate movement through the spatial or movement system.
- **`verb/TakeVerb.java`** -> Delegate stealing, reach checks, source removal, and destination placement to transfer rules.
- **`verb/PourVerb.java`** -> Correctly honor resolved tile destinations and delegate spilling/consumption atomically.
- **`verb/SiphonVerb.java`** -> Define source capacity/consumption semantics so substances cannot be duplicated indefinitely.
- **`verb/ThrowVerb.java`** -> Reject invalid destinations and delegate removal, impact, breakage, and spilling atomically.
- **`verb/IgniteVerb.java`** -> Delegate ignition rules to `SimulationSystem` and keep the verb focused on resolution and narration.

### Priority 6 — complete the presentation seam

- **`event/EventBus.java`** -> Introduce an event-publisher interface for gameplay code; keep subscriptions in application composition.
- **`event/GameEvent.java`** -> Prefer semantic events for combat, movement, transfer, fire, and simulation changes; reserve narrative events for genuinely textual output.
- **`ui/View.java`** -> Replace direct mutable `World`/`RoundState` access with an immutable presentation snapshot or read-only query.
- **`ui/ConsoleView.java`** -> Render only events and immutable snapshots.
- **`app/Repl.java`** -> Limit the REPL to input, aliases, lifecycle, and application coordination.
- **`app/Scenario.java`** -> Build worlds through explicit setup/build APIs rather than mutating internals.

### Priority 7 — tests required before broad feature work

- **`test/.../CommandParserTest.java`** -> Cover quoting, operators, malformed pipelines, negative numeric arguments, flags, and trailing operators.
- **`test/.../ResolvedTest.java`** -> Cover every address variant, empty slots, pack roots, equipment ownership, dead entities, relative tiles, invalid directions, and bounds.
- **`test/.../InventoryTest.java`** -> Prove one item cannot occupy multiple locations and transfers remain atomic when packs or slots are full.
- **Execution tests** -> Cover free/paid mixed lines, `&&`, `||`, material pipelines, refunds, interruptions, terminal verbs, line tax, break penalties, and dry-run equivalence.
- **System tests** -> Cover actor combat, drops, reactions, line of sight, fire propagation, liquid consumption, and movement triggers.
- **Architecture tests** -> Verify duplicate IDs and invalid model state are rejected and domain/system code does not depend on console presentation classes.

## Implementation progress

### Priority 1 status: implemented on `gpt_refactor_1`

Priority 1 was split into four stages to reduce risk and migrate all callers without compatibility shims.

#### Stage 1 — low-risk model cleanup

Completed work:

- Made `Actor`'s inventory final.
- Kept nocking state under `Actor` as the single owner and removed the unused duplicate `WeaponState` implementation.
- Changed scenario-only equipment placement so an occupied slot is rejected instead of silently overwritten.
- Added restrained entity invariants:
  - invalid identity/construction inputs are rejected;
  - damage cannot be negative;
  - HP cannot fall below zero;
  - healing cannot exceed maximum HP;
  - invalid maximum HP is rejected;
  - negative armor and evasion remain possible for gameplay effects.
- Added restrained item invariants:
  - invalid IDs, names, tags, damage, and durability are rejected;
  - wear cannot be negative;
  - durability cannot fall below zero.
- Added consistent dice validation:
  - chance percentages must be between 0 and 100;
  - inclusive ranges require `lo <= hi`.

#### Stage 2 — actor-state ownership

Completed work:

- Moved reaction trigger/readiness state from `Entity` to `Actor`.
- Moved guarding state from `Entity` to `Actor`.
- Moved body-part condition state from `Entity` to `Actor`.
- Moved per-round reaction/guard reset behavior to `Actor`.
- Updated scenario setup to configure reactions through `Actor.Trigger`.
- Updated `ReactionSystem` contracts to return and accept `Actor` where actor state is required.
- Updated combat guard handling to check actor state while preserving force-based guard bypass.
- Updated auditing, scan, inspect, simulation, stage execution, and settlement call sites to use actor-owned state.
- Added no actor-state compatibility methods back to `Entity`.

#### Stage 3 — map and tile ownership

Completed work:

- Moved `Tile` ownership from `World` to `GameMap`.
- Made map dimensions and tile state private.
- Added query-only tile APIs for walls, tags, ceiling tags, and ground items.
- Added named `GameMap` mutation operations for terrain, tile tags, ceiling tags, and ground-item placement/removal.
- Migrated scenario construction, rendering, spatial rules, simulation, combat drops, and relevant verbs away from direct tile collection mutation.
- Ground-item and tile-tag collections are no longer intended to be mutated by callers.

#### Stage 4 — World encapsulation and system-cycle removal

Completed work:

- Encapsulated the map, entity registry, thrall, dice, round number, and movement-trigger state in `World`.
- Added narrow accessors and read-only entity iteration.
- Added explicit thrall installation.
- Added duplicate and reserved entity-ID rejection.
- Removed `World` forwarding methods for spatial queries, reactions, and simulation ticks.
- Updated callers to invoke `SpatialService`, `ReactionSystem`, and `SimulationSystem` explicitly.
- Migrated scenario setup, addressing, execution, settlement, auditing, rendering, combat, environment simulation, and verbs to the new contracts.
- Corrected actor inventory resolution so an addressed actor's inventory is resolved against that actor rather than the thrall.
- Added no backward-compatible field accessors or forwarding wrappers.

### Scope intentionally deferred

The following work was deliberately not folded into Priority 1:

- Fully typed address locations and structured resolution failures — Priority 2.
- A complete atomic item-transfer service — Priority 5, after typed locations exist.
- Injectable verb catalogs and unified metadata — Priority 3.
- Unified free/paid execution and material validation — Priority 4.
- Immutable presentation snapshots — Priority 6.
- Comprehensive automated test coverage — Priority 7.

## Current next step

Proceed with **Priority 2: typed and authoritative addressing**. This should happen before implementing an atomic transfer system, because transfers need typed source and destination locations rather than nullable items and encoded container strings.

Before merging the branch, compile the complete workspace and run available smoke tests. After Priority 2, add focused address and inventory tests before beginning broad feature development.
