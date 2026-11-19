# Psionic Physics Magic v2 — Implementation Plan

- **Branch / Baseline:** `new_magic_design` at commit `433c012` (`Fixed crit effects and logging`)
- **Authoritative Spec:** `Psionic Physics Magic v2.xlsx` (Tabs `0`–`6`)
- **Engineering Rules:** `GEMINI.md` (C++/data-oriented Java, flat public fields over getter/setter boilerplate, KISS, fail-fast, low LOC, no `Stream` allocations inside tick/update loops)

---

## Architecture Principles (Zero-Overlap File Ownership)

1. **Data-Contract First, Then Disjoint Subsystems:** All constants, tags, and flat state fields land once in **Phase 1**. Spatial/physics rules land in **Phase 2**. Execution/economy plumbing lands in **Phase 3**. All new magic verbs and `DefaultVerbs` wiring land once in **Phase 4**. Simulation/AI integration lands in **Phase 5**. UI/inspection/docs land last in **Phase 6**.
2. **Every file is owned by strictly one phase** so consecutive phases do not re-open earlier files.
3. **No Redundant Classes (`GEMINI.md`):**
   - Do **not** create a separate `Payload` class/enum: Tab `0C` defines the 8 payloads directly as `Tag` values (`FORCE`, `ITEM`, `FIRE`, `INFERNO`, `WATER`, `OIL`, `ACID`, `BLOOD`), and `Material` already carries `OfItem(Item)` and `OfSubstance(Tag)`.
   - Do **not** duplicate `PushVerb` into `PullVerb`: implement one `PushVerb(boolean pull)` registered twice (`"push"`, `"pull"`) in `DefaultVerbs`.
   - Use **public fields** (C++ struct style) on `Entity`, `Actor`, and `GameMap.Tile` for the new magic/thermal/status fields rather than getter/setter boilerplate.
4. **Preserve Melee/Ranged Baseline:** Existing `CombatEngineTest` and `AiSystemTest` assert exact numbers against `Balance.BASE_HIT_CHANCE` (`70`), `HEAD_HIT_MOD` (`-40`), `CRIT_DMG_MULT` (`2.0`), etc. Keep melee/ranged constants intact and add the v2 spell constants (`BASE_HIT = 80`, `ZONE_*`, `CRIT_MULT_*`, etc.) cleanly alongside them.
5. **All Math & Rounding:** Temperatures and damages are `int`. All division and multipliers (`delta / thermalMass`, `CHILL_WARM_STEP * thermalMass`, `FROZEN_DELTA_MULT`, `BLAST_DMG_MULT`, `CRIT_MULT_*`, `NUMB_ARM_DMG_MULT`) use `Math.floor` (`int` floor). Damaging hits deal at least `1` damage.

---

## File-Ownership Matrix

| Phase | Files Owned (Touched Once) |
|---|---|
| **Phase 1: Model & Constants** | `Balance.java`, `Tag.java`, `CreatureStats.java`, `Entity.java`, `Actor.java`, `Thrall.java`, `GameMap.java`, `Material.java` |
| **Phase 2: Spatial & Physics Engine** | `SpatialService.java`, `PhysicsReactions.java` *(new)*, `Substances.java`, `Ignition.java`, `Destruction.java` |
| **Phase 3: Addressing, Exec & Base Verbs** | `Resolved.java`, `RoundState.java`, `VerbContext.java`, `ApCost.java`, `Validator.java`, `Executor.java`, `StepVerb.java`, `PassVerb.java`, `PourVerb.java`, `ThrowVerb.java`, `IgniteVerb.java` |
| **Phase 4: Spell Combat & Magic Verbs** | `SpellCombat.java` *(new)*, `BoltVerb.java` *(new)*, `BlastVerb.java` *(new)*, `SiphonVerb.java`, `PushVerb.java` *(new, handles push + pull)*, `ChannelVerb.java` *(new)*, `DefaultVerbs.java` |
| **Phase 5: Tick, Combat Integration & AI** | `SimulationSystem.java`, `CombatEngine.java`, `StrikeVerb.java`, `ShootVerb.java`, `AiSystem.java` |
| **Phase 6: UI, Inspection, Manual & Scenario** | `Scene.java`, `GdxView.java`, `ConsoleView.java`, `InspectVerb.java`, `StatusVerb.java`, `ScanVerb.java`, `CommandManual.java`, `Scenario.java` |

---

## Phase 1 — Model, Constants & Flat State Fields

**Goal:** Establish every named constant, tag, stat, and mutable runtime field once so Phases 2–6 never modify core model schemas.

### Files
- **`src/main/java/com/archon/model/Balance.java`**
  - Keep existing melee/ranged/AI/economy constants (`BASE_HIT_CHANCE = 70`, `HEAD_HIT_MOD = -40`, `CRIT_DMG_MULT = 2.0`, `BLIND_HIT_PENALTY = 30`, etc.) untouched so existing tests remain valid.
  - Add all Tab `1` constants grouped by section (`1A`–`1I`):
    - `1A` Thresholds: `ABS_ZERO = -273`, `DEEP_FREEZE_TEMP = -20`, `FREEZE_TEMP = 0`, `CHILLED_MAX_TEMP = 15`, `AMBIENT_TEMP = 20`, `BODY_TEMP = 36`, `WARM_MELT_TEMP = 36`, `BOIL_TEMP = 100`, `CAUSTIC_CLEAR_TEMP = 66`, `STEAM_CLEAR_TEMP = 80`, `IGNITE_TEMP = 200`, `BURN_CAP = 250`, `INFERNO_MIN_TEMP = 300`.
    - `1B` Temp deltas/round: `CHILL_WARM_STEP = 10`, `CREATURE_HOT_COOL_STEP = 30`, `TILE_DECAY_STEP = 10`, `TILE_HOT_COOL_STEP = 60`, `FROZEN_DELTA_MULT = 0.5`, `THAW_RESET_TEMP = 36`.
    - `1C` AP costs: `AP_PER_ROUND = 6`, `AP_SIPHON = 2`, `AP_PUSH = 2`, `AP_PULL = 2`, `AP_BOLT_PAYLOAD = 1`, `AP_BOLT_KIN = 2`, `AP_BOLT_KIN_AIMED = 3`, `AP_BLAST = 2`, `AP_CHANNEL_THERMAL = 0`, `AP_CHANNEL_FORCE = 1`, `AP_SIPHON_BLOOD = 3`, `AP_BOLT_BLOOD = 3`, `AP_BLAST_BLOOD = 3`, `AP_CHANNEL_BLOOD = 0`, `BLOOD_SURGE_AP = 2`, `AP_PASS_R = 0`, `AP_STAND_UP = 1`.
    - `1D` Magic/Dmg/Temp: `BASE_MAGIC = 3`, `DMG_KIN = 3`, `DMG_HEAT = 3`, `DMG_INFERNO = 6`, `DMG_ACID = 4`, `DMG_BLOOD = 6`, `BLAST_DMG_MULT = 0.5`, `TD_HEAT = 100`, `TD_INFERNO = 200`, `TD_WATER = -20`, `SIPHON_COOL_DELTA = -30`, `FUEL_AIR_DMG_HEAT = 8`, `FUEL_AIR_DMG_INFERNO = 10`, `RUPTURE_ACID_DMG = 3`, `STONE_STRESS_DMG = 1`.
    - `1E` Status dmg/durations: `SCALD_FLASH_DMG = 5`, `SCALD_DOT_DMG = 3`, `BURN_TICK_DMG = 5`, `INFERNO_TICK_DMG = 15`, `FROSTBITE_DMG = 3`, `CRYO_DMG = 6`, `CRYO_ARMOR_SHRED = 1`, `THERMAL_SHOCK_DMG = 12`, `THERMAL_SHOCK_ARMOR_SHRED = 1`, `SHATTER_BASE_DMG = 8`, `SHATTER_ARMOR_SHRED = 1`, `ACID_ARMOR_SHRED = 1`, `CAUSTIC_DMG = 3`, `BLEED_DMG = 2`, `BLEED_TURNS = 3`, `CRIT_BLEED_BONUS_TURNS = 2`, `CAUTERIZE_SCALD_DMG = 3`, `OIL_TURNS = 3`, `PROP_BURN_TURNS = 3`, `WET_TURNS = 3`, `SLIP_CHANCE = 40`, `EVA_CHILLED = -15`, `EVA_FROZEN = -20`, `CHILLED_AP_STEP = 1`, `CRIPPLED_AP_STEP = 1`, `BLIND_LINGER_TURNS = 1`, `WEAPON_CLATTER_WEAR = 1`.
    - `1F` Range/Force/Movement: `BOLT_RANGE = 8`, `BLAST_RANGE = 6`, `PUSH_RANGE = 5`, `FORCE_COLLISION_DMG = 6`, `SLAM_MIN_TRAVEL = 1`, `RES_GAS = 0`, `RES_LIGHT = 1`, `RES_MEDIUM = 2`, `RES_HEAVY = 99`, `FORCE_FOCUS_BONUS = 2`, `SIPHON_REACH_CHEBYSHEV = 1`, `SIPHON_REACH_MANHATTAN = 4`.
    - `1G` Channel: `CHANNEL_MAX_TURNS = 2`, `CONC_BASE = 75`, `CONC_PER_DMG = 3`.
    - `1H` Spell Hit/Crit/Zones: `BASE_HIT = 80`, `BASE_CRIT = 5`, `SEVERITY_TIERS = 3`, `CRIT_MULT_TORSO = 1.5`, `CRIT_MULT_LIMB_T1 = 2`, `CRIT_MULT_LIMB_T2 = 2.5`, `CRIT_MULT_LIMB_T3 = 3`, `CRIT_MULT_HEAD_T1 = 2`, `CRIT_MULT_HEAD_T2 = 3`, `CRIT_MULT_HEAD_T3 = 4`, `CRIT_HEAT_TEMP_MULT = 1.5`, `AIMED_NONCRIT_STATUS_CHANCE = 20`, `CRIT_STATUS_CHANCE_T1 = 60`, `CRIT_STATUS_CHANCE_T2 = 80`, `CRIT_STATUS_CHANCE_T3 = 90`, `ARM_DISARM_CHANCE_T1 = 20`, `ARM_DISARM_CHANCE_T2 = 45`, `ARM_DISARM_CHANCE_T3 = 75`, `LEG_CRIPPLE_CHANCE_T2 = 45`, `LEG_CRIPPLE_CHANCE_T3 = 75`, `ZONE_HEAD_HIT = -25`, `ZONE_HEAD_CRIT = 30`, `ZONE_ARM_HIT = -15`, `ZONE_ARM_CRIT = 15`, `ZONE_LEGS_HIT = -10`, `ZONE_LEGS_CRIT = 10`, `DAZED_TURNS = 2`, `DAZED_HIT_PENALTY = -20`, `SPELL_BLIND_HIT_PENALTY = -35`, `BLIND_MAX_RANGE = 1`, `STUN_TURNS = 2`, `NUMB_ARM_TURNS = 2`, `NUMB_ARM_DMG_MULT = 0.5`, `CRIPPLED_TURNS = 3`, `LEG_RESIST_BASE = 80`, `LEG_RESIST_PER_STR = 5`.
    - `1I` Forbidden Blood: `HP_COST_BOLT_BLOOD = 6`, `HP_COST_BLAST_BLOOD = 7`, `HP_COST_SIPHON_BLOOD = 5`, `HP_COST_CHANNEL_BLOOD = 5`, `BLOOD_BOIL_BASE = 6`, `BLOOD_KILL_HEAL = 5`.
- **`src/main/java/com/archon/model/Tag.java`**
  - Add: `FORCE`, `ITEM`, `INFERNO`, `BLEEDING`, `CHILLED`, `DAZED`, `NUMB_ARM`, `CRIPPLED`, `STEAM`, `CAUSTIC_GAS`, `OIL_MIST`, `ICE`.
  - Note: `Tag.BLOOD` on a creature represents the cosmetic blood surface coat (`0C` Row 28); `Tag.OIL_MIST` is an exclusive gas tag (not `STEAM + OIL` simultaneously).
- **`src/main/java/com/archon/model/CreatureStats.java`**
  - Expand record: `CreatureStats(int maxHp, int armor, int evasion, int strength, int magic, double thermalMass)`.
  - Preserve 3-arg and 4-arg convenience constructors defaulting `magic = Balance.BASE_MAGIC`, `thermalMass = 1.0`.
- **`src/main/java/com/archon/model/Entity.java`**
  - Add flat **public** fields (C++ struct style):
    - `public int temp;` — initialized in constructor to `kind == Kind.CREATURE ? Balance.BODY_TEMP : Balance.AMBIENT_TEMP`.
    - `public double thermalMass = 1.0;`
    - `public int wetTurns, oilTurns, propBurnTurns, bleedTurns, dazedTurns, numbArmTurns, crippledTurns, stunTurns, blindLingerTurns;`
    - `public BodyPart numbArmPart;` (`ARM_L` or `ARM_R` when `numbArmTurns > 0`; `null` otherwise).
    - `public boolean deepFreezeTriggered;`
- **`src/main/java/com/archon/model/Actor.java` & `Thrall.java`**
  - Add flat **public** fields on `Actor`:
    - `public int magic;` (from `CreatureStats.magic()`)
    - `public Tag channeledPayload;` (`null` when empty; exclusive single slot: `FIRE`, `INFERNO`, or `FORCE`)
    - `public int channelTurns;`
    - `public boolean bloodSurgedThisRound;`
  - Update `Actor` constructor to copy `stats.magic()` and `stats.thermalMass()`, and `resetRoundState()` to reset `bloodSurgedThisRound = false` while **preserving** `channeledPayload` and `channelTurns` across rounds.
- **`src/main/java/com/archon/model/GameMap.java`**
  - Add flat **public** fields on `GameMap.Tile` for independent floor and gas thermal layers (`5A`):
    - `public int temp = Balance.AMBIENT_TEMP;` (floor thermal layer)
    - `public int gasTemp = Balance.AMBIENT_TEMP;` (gas thermal layer; set to `BOIL_TEMP` when `STEAM`, `CAUSTIC_GAS`, or `OIL_MIST` spawns so hot gas above never melts/boils floor `ICE`/`WATER`)
    - `public int oilTurns = 0;` (floor oil / Grease Fire fuel counter)
    - `public int burnTurns = 0;` (non-oiled burning floor/prop counter)
- **`src/main/java/com/archon/verb/Material.java`**
  - Keep `Material.OfItem(Item)` and `Material.OfSubstance(Tag substance)` (`Tag.FIRE`, `Tag.INFERNO`, `Tag.WATER`, `Tag.OIL`, `Tag.ACID`, `Tag.FORCE`).

### Verification (Phase 1)
- Run `mvn test` — all existing tests pass unchanged.
- Add `StatsAndStateTest.java`: verify creature `BODY_TEMP = 36` vs prop/tile `AMBIENT_TEMP = 20`, independent `tile.temp` and `tile.gasTemp`, `thermalMass` defaults, and `Actor.resetRoundState()` channel preservation.

---

## Phase 2 — Spatial LoS/LoF & Physics Reaction Engine

**Goal:** Build the thermodynamic state machine, Tab `6` sparse Payload × State matrix (with deterministic multi-state precedence), two-layer tile state transitions, tile-entry routing, gas/liquid displacement, and concentration disruption.

### Files
- **`src/main/java/com/archon/system/spatial/SpatialService.java`**
  - Add `hasGas(GameMap.Tile t)` (`t.has(Tag.STEAM) || t.has(Tag.CAUSTIC_GAS) || t.has(Tag.OIL_MIST)`).
  - Update `lineOfSight(GameMap, Vec2 a, Vec2 b)` and add overload `lineOfSight(GameMap, Vec2 a, Vec2 b, boolean ignoreGas)`: all three gases block normal/elemental LoS; `bolt -b` passes `ignoreGas = true`.
  - Add `firstGasOnLine(World, Vec2 origin, Vec2 target)` along cardinal rays so elemental bolts, blasts, `LIT` torches, and thrown flasks intercept the **first gas tile** on the ray (`3B` R21).
  - Add `siphonLineOfFire(World, Vec2 a, Vec2 b)`: cardinal LoF up to `SIPHON_REACH_MANHATTAN = 4` (or Chebyshev `1`), allowing gas endpoints and blocked by walls or intervening non-target solid entities.
  - Replace `Stream` usage in `entityAt` / `hostilesAdjacentTo` with classic index loops (`GEMINI.md` Rule 4).
- **`src/main/java/com/archon/system/environment/PhysicsReactions.java`** *(new)*
  - **Temperature & Threshold Engine (`5A`, `5B`):**
    - `applyCreatureTempDelta(World, Entity, int delta, boolean ignoreThermalMass, List<String> log)`:
      - `int scaled = ignoreThermalMass ? delta : (int) Math.floor(delta / entity.thermalMass);`
      - If `scaled < 0 && entity.has(Tag.FROZEN)`, `scaled = (int) Math.floor(scaled * Balance.FROZEN_DELTA_MULT);`
      - Clamps `entity.temp = Math.max(Balance.ABS_ZERO, entity.temp + scaled)`.
    - `syncThermalState(World, Entity, List<String> log)`:
      - `WET` and `temp <= FREEZE_TEMP`: enter `FROZEN` + `STUNNED` (`clearReadied`, `setGuarded(false)`, strip `WET`, `wetTurns = 0`).
      - If `entity.has(Tag.FROZEN) && entity.temp <= Balance.DEEP_FREEZE_TEMP && !entity.deepFreezeTriggered`: set `deepFreezeTriggered = true`, deal `CRYO_DMG` (ignores armor), shred `CRYO_ARMOR_SHRED` armor (min 0).
      - If `!entity.has(Tag.FROZEN)`: if `temp <= CHILLED_MAX_TEMP` add `CHILLED`, else remove `CHILLED`.
      - If `entity.has(Tag.BLEEDING)` and (`temp <= FREEZE_TEMP` or `temp >= BOIL_TEMP`): remove `BLEEDING` and `BLOOD` (`bleedTurns = 0`; clotted or cauterized).
      - If `!entity.has(Tag.WET) && entity.temp >= Balance.IGNITE_TEMP && !entity.has(Tag.BURNING)`: enter `BURNING` at `temp = Math.max(entity.temp, Balance.BURN_CAP)`.
    - `exitFrozen(Entity)`: removes `FROZEN`, `STUNNED`, `CHILLED`, sets `deepFreezeTriggered = false`, removes `WET` (`wetTurns = 0`), sets `temp = Balance.THAW_RESET_TEMP` (`36`).
  - **Multi-State Precedence & Sparse Matrix (`6. Reactions`):**
    - Creature reactions evaluate matching columns left-to-right (`Wet` -> `Bleeding` -> `Chilled` -> `Frozen` -> `Oiled` -> `Burning std` -> `Burning grease` -> `Corroded`).
    - **Consuming reactions** (`Steam Flash` B7, `Thermal Shock` E7, `Mechanical Shatter` E6, `Snap-Freeze` D8, `Extinguish` G8, `Flare-Up` H8, `Ice Shell Block` E10) resolve immediately and stop further thermal/default processing for that hit (except `Cauterize` C7 / `Rinse` C8 still strip `BLEEDING`/`BLOOD` if present, and `Oiled` Frozen shell E9 ignites to `Burning (grease)` at `BURN_CAP` right after `Thermal Shock` E7).
    - **Additive reactions** (`Cauterize` C7, `Thaw` D7, `Rinse` C8/F8) apply alongside remaining columns/default.
    - **Damage & Armor Rules (`2. Payloads`, `6. Reactions`):**
      - `FORCE` (`Pure Kinetic`) & `Piped Item` (weapons/stones): mitigated by armor `max(1, dmg - armor)` (Crit T1 multiplies before subtracting armor: `max(1, floor(dmg * mult) - armor)`; Crit T2–T3 bypasses armor). On `FROZEN` (`E6` `Mechanical Shatter`): shreds `SHATTER_ARMOR_SHRED` first, then deals `SHATTER_BASE_DMG + floor(Magic/2)` mitigated by remaining armor (halved by `BLAST_DMG_MULT` on Blast `S6`), and calls `exitFrozen`. Props: wood crates/barrels take kinetic damage and shatter at `0 HP` (`Q6`); doors and stone/metal props take `0` kinetic damage (piped weapon takes `WEAPON_CLATTER_WEAR` durability wear on stone/metal `R6`; stone or `HEAVY` weapon fractures `ICE` to `WATER` `N6`).
      - `FIRE` / `INFERNO`: **bypasses armor**.
        - `B7` (`Wet`): normal `Heat`/`Inferno` dmg + `SCALD_FLASH_DMG`, `temp = min(result, BOIL_TEMP)`, strip `WET`, spawn `STEAM` on tile (`gasTemp = BOIL_TEMP`), blocks burn.
        - `C7` (`Bleeding`): adds `+CAUTERIZE_SCALD_DMG`, strips `BLEEDING` & `BLOOD`, no steam.
        - `D7` (`Chilled`): strips `CHILLED`, applies temp delta (ignites at `>= IGNITE_TEMP`, setting `temp = max(result, BURN_CAP)`).
        - `E7` (`Frozen` - `Thermal Shock`): `THERMAL_SHOCK_DMG` **replaces** base fire damage (full damage on Blast `S7`, ignores armor), shreds `THERMAL_SHOCK_ARMOR_SHRED`, calls `exitFrozen`, spawns `STEAM` (if `Oiled` per `E9`, ignites to `Burning (grease)` at `BURN_CAP` instead of staying Dry).
        - `F7` (`Oiled`): ignites at any temp (`OIL + BURNING`, `temp = max(result, BURN_CAP)`, `oilTurns = OIL_TURNS`).
        - `G7`/`H7` (`Burning`): `Heat` clamps `temp = max(temp, BURN_CAP)`; `Inferno` applies delta allowing `temp >= INFERNO_MIN_TEMP` overcharge.
        - `J7` (`Steam`): `SCALD_FLASH_DMG` to occupant, clears `STEAM`.
        - `K7` (`Caustic gas` - `Rupture`): normal `Heat`/`Inferno` dmg `+ RUPTURE_ACID_DMG` (ignores armor) + `-ACID_ARMOR_SHRED` + `CORRODED` to occupant, clears `CAUSTIC_GAS`.
        - `L7` (`Oil Mist` - `Fuel-Air Detonation`): deals `FUEL_AIR_DMG_HEAT` (or `FUEL_AIR_DMG_INFERNO`) `+ floor(Magic/2)` (ignores armor) to all entities within 1-tile Chebyshev (3×3), chains adjacent `OIL_MIST` tiles once via BFS queue, caps at max 1 detonation hit per creature per cast (`Set<Entity>`), consumes mist, ignites Dry/flammable entities & oiled tiles at `BURN_CAP` (flashes `WET` entities/puddles to `STEAM` instead).
        - `M7` (`Water puddle`): vaporizes puddle to `STEAM` (`gasTemp = BOIL_TEMP`), deals `SCALD_FLASH_DMG` to occupant.
        - `N7` (`Ice tile`): `Heat` melts `ICE` to `WATER` (`temp = AMBIENT_TEMP`); `Inferno` sublimates `ICE` directly to `STEAM` (`gasTemp = BOIL_TEMP`); `Oiled ice` (`N9`) flashes to `STEAM` and strips oil.
        - `O7` (`Oil slick`): ignites to `Grease Fire tile` (`OIL + BURNING`, `temp = BURN_CAP`, `oilTurns = OIL_TURNS`).
        - `P7` (`Acid pool`): boils to `CAUSTIC_GAS` (`gasTemp = BOIL_TEMP`); `Toxic Tar` boils acid to `CAUSTIC_GAS` and ignites oil to `Grease Fire tile`.
        - `Q7` (`Wood prop`): `Heat` ignites if `FLAMMABLE` or `OIL` (`BURNING`, `temp = BURN_CAP`, `propBurnTurns = PROP_BURN_TURNS`; heavy wooden doors require `temp >= IGNITE_TEMP`, oiled, or `Inferno`); `Inferno` auto-ignites all wooden props/doors.
        - `R7` (`Stone/Metal prop`): stone takes `STONE_STRESS_DMG` (unmitigated); metal heats normally (`temp += TD_HEAT` / `TD_INFERNO`).
      - `LIT torch` spark (`5A`, `6` R3): applies a **spark** on impact — ignites `OIL` creature/tile, `FLAMMABLE` wood prop, or detonates `OIL_MIST` (`FUEL_AIR_DMG_HEAT`); deals no fire damage or temp delta to non-flammables; extinguishes (`removes LIT`) if it hits `WATER` or `STEAM`.
      - `WATER`: no direct damage; applies `TD_WATER` (`-20`). Rinses `BLOOD` surface coat (`C8`), rinses unlit `OIL` (`F8`/`O8`), snap-freezes `CHILLED` if `<= FREEZE_TEMP` (`D8`), deepens `FROZEN` cold with `FROZEN_DELTA_MULT` (`E8`), extinguishes `Burning std` to `STEAM` at `BODY_TEMP` (`G8`), triggers `Flare-Up` on `Burning grease` / `Grease Fire tile` (`SCALD_FLASH_DMG`, `STEAM`, strips `OIL + BURNING`, resets to `BODY_TEMP` / `AMBIENT_TEMP`, `H8`), condenses `STEAM` to `WATER` (`J8`), dilutes `CAUSTIC_GAS` / `ACID` / `Toxic Tar` to `WATER` (`K8`/`P8`), precipitates `OIL_MIST` to `OIL` slick (`L8`), drenches burning wood props to `STEAM` (`Q8`), flashes hot `>= BOIL_TEMP` stone/metal props to `STEAM` (`R8`).
      - `OIL`: overrides `WET` (`B9`), coats `FROZEN` shell (`E9`), promotes `Burning std` to `Burning (grease)` (`G9`), refuels `oilTurns = OIL_TURNS` (`H9`), turns `STEAM` into `OIL_MIST` (`J9`), binds `CAUSTIC_GAS` / `ACID` into `Toxic Tar` (`K9`/`P9`), refreshes `OIL_MIST` (`L9`), overrides `WATER` puddle (`M9`), creates `Oiled ice` (`ICE + OIL`, `N9`), coats props (`Q9`/`R9`).
      - `ACID`: shreds `ACID_ARMOR_SHRED` permanently first, then deals `DMG_ACID + floor(Magic/2)` mitigated by remaining armor (Crit T2–T3 bypasses armor), applies `CORRODED`. Blocked completely by `FROZEN` ice shell (`E10`, spills `ACID` pool on tile instead). On `Burning std` (`G10`) and `Burning grease` (`H10`): deals normal Acid dmg/shred, boils into `CAUSTIC_GAS`, target keeps burning. Turns `STEAM` into `ACID` puddle (`J10`), refreshes `CAUSTIC_GAS` to `BOIL_TEMP` (`K10`, Blast `S10` expands `CAUSTIC_GAS` 1 cardinal tile outward into open non-wall tiles without gas), turns `OIL_MIST`/`OIL` into `Toxic Tar` (`L10`/`O10`), acidifies `WATER` (`M10`), creates `Acid on ice` (`ICE + ACID`, `N10`), shreds prop armor (`Q10`/`R10`).
      - `BLOOD`: deals `DMG_BLOOD + floor(Magic/2)` pierce (bypasses armor) + `BLEEDING` (`bleedTurns = BLEED_TURNS`, + `Tag.BLOOD` coat unless `WET` `B11`), except when target is `<= FREEZE_TEMP` (`D11`/`E11` clotted) or `>= BOIL_TEMP` (`G11`/`H11` cauterized), which take pierce damage only without `BLEEDING`. No effect on non-living tiles/gas/props.
  - **Tile Entry & Displacement (`3B`, `5A`, `5C`):**
    - `onEnterTile(World, Entity, Vec2 dest, boolean canSlip, List<String> log)`: moves entity to `dest`, routes floor substances (`WATER`, `OIL`, `ACID`) through `6. Reactions` without direct payload damage (e.g., `BURNING` creature stepping into `WATER` extinguishes to `STEAM`; `WET` creature at `<= 0°C` freezes; `OILED` creature stepping into `BURNING` tile ignites to `Burning (grease)`), applies `Acid pool` / `Toxic Tar` entry (`CAUSTIC_DMG` ignoring armor, `-ACID_ARMOR_SHRED`, `CORRODED`), `Grease Fire` / `BURNING` tile entry (`BURNING` at `BURN_CAP`), `ICE` / `OIL` / `Toxic Tar` / `Oiled ice` slip (`SLIP_CHANCE` -> `PRONE` if `canSlip`; `Oiled ice` also coats `OIL`), and gas cloud `BLIND` (`clearReadied`, `blindLingerTurns = BLIND_LINGER_TURNS`). Return `boolean slipped` so Push/Pull halts remaining movement if target slips `PRONE`.
    - `relocateGasOrLiquid(World, Vec2 start, Vec2 dir, int steps, Vec2 stopBefore, List<String> log)`: moves gas or floor liquid up to `steps` tiles; colliding with another gas/liquid resolves via `6. Reactions`; colliding with a wall splashes 1 tile perpendicular to each open side (`3B` R25).
  - **Concentration Disruption (`4B`):**
    - `onCreatureDamaged(World, Entity victim, int dmg, boolean intentionalSelfLoss, List<String> log)`: when `victim == world.thrall() && world.thrall().channeledPayload != null && !intentionalSelfLoss && dmg > 0`, rolls `d100 <= clamp(CONC_BASE - dmg * CONC_PER_DMG, 0, 100)`; on failure, clears `channeledPayload` / `channelTurns` first, then triggers backfire on caster's tile (`FIRE`/`INFERNO` -> applies payload to caster and caster's tile; `FORCE` -> `FORCE_COLLISION_DMG` ignoring armor + `PRONE` to caster).
- **`src/main/java/com/archon/system/environment/Substances.java`, `Ignition.java`, `Destruction.java`**
  - Delegate spill/pour/extract/ignite to `PhysicsReactions` helpers; destroyed `LIT`/`BURNING` props set floor `BURNING` (`temp = BURN_CAP`, `burnTurns = PROP_BURN_TURNS` or `oilTurns = OIL_TURNS` if oiled).

### Verification (Phase 2)
- Run `mvn test` (update `AiSystemTest.waterExtinguishesFireAndWetPreventsIgnition` if needed for `G8` extinguish -> Dry + `STEAM`).
- Add `SpatialServiceTest.java` & `PhysicsReactionsTest.java`: verify all Tab `6` cells, multi-state precedence (`WET + BLEEDING + CHILLED`), two-layer `gasTemp` vs floor `temp` on `ICE`, Fuel-Air Detonation chain + 1-hit cap, `onEnterTile` matrix routing, `relocateGasOrLiquid` wall splash, and concentration backfire.

---

## Phase 3 — Addressing, Pipeline AP/Validation, Executor & Base Verbs

**Goal:** Wire substance/item tile addressing, pipeline AP summing, `channel` structural rules, `FROZEN`/`STUNNED` gates, non-terminal `pass -r`, and base movement/item verbs.

### Files
- **`src/main/java/com/archon/address/Resolved.java`**
  - Resolve `@water`, `@steam`, `@oil_mist`, `@caustic` (`@caustic_gas`), `@acid`, `@oil` to nearest matching tile (or water barrel / ground flask container for `@water`), and `@<item>` / `@item` to nearest tile with matching ground item.
- **`src/main/java/com/archon/exec/RoundState.java` & `src/main/java/com/archon/verb/VerbContext.java`**
  - Add `RoundState.gain(int amount)` for `BLOOD_SURGE_AP` and pass `RoundState` into `VerbContext`.
- **`src/main/java/com/archon/verb/ApCost.java` & `src/main/java/com/archon/exec/Validator.java`**
  - Sum every invocation in a pipeline (`for (int i = 0; i < stage.pipeline().size(); i++)`):
    - `bolt` at `i > 0` (piped from `siphon`, `channel`, or `pull @item`): `AP_BOLT_KIN_AIMED` (`3`? No: `pull @item | bolt -a` or piped `bolt`: plain piped `bolt` costs `AP_BOLT_PAYLOAD = 1`; piped `bolt -a` with weapon/stone costs `AP_BOLT_PAYLOAD + 1 = 2` so `pull (2) | bolt -a (2) = 4 AP`, while standalone `bolt` is `AP_BOLT_KIN = 2`, standalone `bolt -a` is `AP_BOLT_KIN_AIMED = 3`, `bolt -b` is `AP_BOLT_BLOOD = 3`). Wait: even simpler per Tab `3A` Row 9–10: `bolt` is `AP_BOLT_PAYLOAD` (`1`) when piped (or `AP_BOLT_KIN_AIMED = 3` whenever `-a` is used, or `AP_BOLT_PAYLOAD` when piped without `-a` and `AP_BOLT_KIN_AIMED` with `-a`). Let's follow Tab `3A` Row 9–10 literally: Row 9 `bolt` = `AP_BOLT_PAYLOAD` (kinetic: `AP_BOLT_KIN`); Row 10 `bolt -a` = `AP_BOLT_KIN_AIMED` (`3`, or `AP_BOLT_PAYLOAD` when piped? In `3A` C10 it says `AP_BOLT_KIN_AIMED`, so `bolt -a` is always `AP_BOLT_KIN_AIMED` when standalone and `AP_BOLT_PAYLOAD` + `1` or `AP_BOLT_KIN_AIMED`—Wait: let's make `bolt` cost `AP_BOLT_BLOOD` if `-b`, else if piped (`i > 0`) `inv.hasFlag("aim") ? AP_BOLT_KIN : AP_BOLT_PAYLOAD` (1 AP un-aimed, 2 AP aimed when piped; 2 AP un-aimed, 3 AP aimed when standalone—i.e. `-a` always adds `+1 AP` over the base bolt cost!). That means standalone kinetic `2 -> 3 AP` with `-a`, and piped item `1 -> 2 AP` with `-a`!
    - `step`: base (`1`, or `2` with `-c`) + (`CHILLED` ? `CHILLED_AP_STEP` : `0`) + (`CRIPPLED` ? `CRIPPLED_AP_STEP` : `0`). Standing up from `PRONE` on step adds `AP_STAND_UP` (`1`).
  - In `Validator.quote`: include `+BLOOD_SURGE_AP` when the command line contains a valid `channel -b` stage so an `8 AP` turn validates cleanly.
  - In `Validator.validateStructure`:
    - `pass` is terminal **only** when `!inv.hasFlag("roll")` (`-r` is non-terminal mid-turn action).
    - Thermal `channel` (no `-f`, no `-b`) is pipe-only: valid at `p == 0` (`channel | ...`) or `p == last` (`... | channel`) in a multi-verb pipeline; reject standalone `channel`, `channel | channel`, and `channel -b` inside any pipe.
    - Reject `pull | blast`, `bolt -a` with `-b`, and `bolt -a` when piped from `siphon` (elemental).
- **`src/main/java/com/archon/exec/Executor.java`**
  - When `thrall.has(Tag.FROZEN) || thrall.has(Tag.STUNNED)`: reject all paid commands, `pass -r`, and `0`-AP `-b` verbs (`channel -b`); allow only `free` inspection commands and plain round-ending `pass`.
  - Do not set `terminalExecuted = true` when `pass -r` executes.
  - When an interrupt deals damage to `thrall`, call `PhysicsReactions.onCreatureDamaged(world, thrall, reaction.damage(), false, ...)`.
- **`src/main/java/com/archon/verb/StepVerb.java` & `PassVerb.java`**
  - `StepVerb`: if `thrall.has(Tag.PRONE)`, clear `PRONE` (`+AP_STAND_UP` already in cost) and step via `PhysicsReactions.onEnterTile(c.world, c.thrall, dest, !careful, log)`.
  - `PassVerb`: with `-r` (`roll`), reject if `c.world.map().tile(c.thrall.pos()).has(Tag.BURNING)`; set `PRONE`, clear `readied`/`guarded`, remove `BURNING` (and `OIL` + `oilTurns = 0` if `Burning (grease)`), set `temp = BODY_TEMP`.
- **`src/main/java/com/archon/verb/PourVerb.java`, `ThrowVerb.java`, `IgniteVerb.java`**
  - Route through `PhysicsReactions`; `ThrowVerb` checks `SpatialService.firstGasOnLine` for flasks and `LIT` torches (`3B` R21).

### Verification (Phase 3)
- Run `mvn test`.
- Add `ExecutionAndGatesTest.java`: verify `@water`/`@steam`/`@<item>` resolution, pipeline AP summing, `channel` pipe validation, `FROZEN`/`STUNNED` gates, non-terminal `pass -r`, `CHILLED`/`CRIPPLED`/`PRONE` step AP, and interrupt concentration checks.

---

## Phase 4 — Spell Combat Math & All Magic Verbs (+ `DefaultVerbs`)

**Goal:** Implement Tab `3D`/`3E` spell hit/crit/tier math and all 6 magic verbs (`bolt`, `blast`, `siphon`, `push`, `pull`, `channel`) in one sweep, wired in `DefaultVerbs.java`.

### Files
- **`src/main/java/com/archon/system/combat/SpellCombat.java`** *(new)*
  - `hitChance(Actor caster, Entity target, BodyPart zone)`:
    - Props and tiles auto-hit (`100%`, no crit).
    - Against creatures: `clamp(BASE_HIT - effectiveEvasion(target) + zoneHitMod + (caster.has(DAZED) ? DAZED_HIT_PENALTY : 0) + (caster.has(BLIND) ? SPELL_BLIND_HIT_PENALTY : 0), 0, 100)` where `effectiveEvasion` includes `EVA_CHILLED` (`-15`) and `EVA_FROZEN` (`-20`).
  - `critChance(int hitChance, BodyPart zone, Tag payload)`:
    - `0` for `WATER` and `OIL` (`3E` R52); otherwise `min(hitChance, BASE_CRIT + zoneCritMod)`.
  - `rollSingleD100(Dice, int hitChance, int critChance)`:
    - Roll `d100` (`1..100`) once. Hit if `roll <= hitChance`; Crit if `roll <= critChance` (`critChance > 0`).
    - `severityTier = Math.min(SEVERITY_TIERS, 1 + (int) Math.floor((double) SEVERITY_TIERS * (critChance - roll) / critChance))`.
  - Apply zone/tier damage multipliers (`CRIT_MULT_TORSO`, `CRIT_MULT_LIMB_T1..3`, `CRIT_MULT_HEAD_T1..3`), T2–T3 armor bypass, non-crit aimed status rolls (`AIMED_NONCRIT_STATUS_CHANCE`; Legs resist roll `d100 <= clamp(LEG_RESIST_BASE + target.strength() * LEG_RESIST_PER_STR, 0, 100)`), crit status rolls (`CRIT_STATUS_CHANCE_T1..3`, `ARM_DISARM_CHANCE_T1..3` dropping `HAND_LEFT` for `ARM_L` or `HAND_RIGHT` for `ARM_R`, `LEG_CRIPPLE_CHANCE_T2..3`, Head T3 `STUNNED` + `PRONE`), and Tab `2` element crit bonuses (`CRIT_HEAT_TEMP_MULT`, Acid extra shred / T3 pool spill, Blood `+CRIT_BLEED_BONUS_TURNS` / T3 instant `BLEED_DMG` tick).
- **`src/main/java/com/archon/verb/BoltVerb.java`** *(new)*
  - Range: `BOLT_RANGE = 8` (`BLIND_MAX_RANGE = 1` when `BLIND`). Cardinal LoF required (except `bolt -b` which requires LoS with `ignoreGas = true`).
  - Modes:
    1. Standalone Pure Kinetic `bolt [-a <part>] <target>` (flies over gas).
    2. Piped Elemental `bolt <target>` (rejects `-a`; intercepts first gas tile on ray via `SpatialService.firstGasOnLine`, triggering gas reaction on that tile and its occupant).
    3. Piped Item `pull @item | bolt [-a <part>] <target>`:
       - Weapon/stone -> Pure Kinetic (`weapon dmg + floor(Magic/2)` or `DMG_KIN + floor(Magic/2)`); drops item on target tile after hit or miss; `WEAPON_CLATTER_WEAR` on stone/metal prop; stone or `HEAVY` weapon fractures `ICE` tile.
       - `LIT` torch -> intercepts gas first; Pure Kinetic dmg + Fire spark on flammables (`OIL`, `FLAMMABLE`, `OIL_MIST`); extinguishes in `WATER`/`STEAM`; drops torch on target tile.
       - Flask -> rejects `-a`; intercepts gas first; shatters on impact (hit or miss), applying its liquid payload (`WATER`, `OIL`, `ACID`) and consuming the flask.
    4. Forbidden `bolt -b <target>` (Bloodlance): pays `HP_COST_BOLT_BLOOD` up front (lethal if `hp <= cost`, `intentionalSelfLoss = true`), LoS within `BOLT_RANGE` ignoring gas/obstacles, delivers `BLOOD` payload (`DMG_BLOOD + floor(Magic/2)` ignoring armor + `BLEEDING`), heals `BLOOD_KILL_HEAL` once if target was already `BLEEDING` before the cast and died.
  - Single-target delivery (`3B` R28): affects occupant if occupied, else floor/prop. On a creature miss, elemental/kinetic/blood bolts dissipate harmlessly (piped weapons/stones/torches drop on target tile; flasks shatter on target floor).
- **`src/main/java/com/archon/verb/BlastVerb.java`** *(new)*
  - Range: Cardinal LoF to epicenter within `BLAST_RANGE = 6` (`BLIND_MAX_RANGE = 1` when `BLIND`). 5-tile cardinal cross (epicenter + N/S/E/W; walls block arms; caster excluded). Auto-hits both floor/gas and occupant on each tile; no crits; `BLAST_DMG_MULT` (`0.5`) on direct payload damage and Shatter (`S6`); full temp deltas; flat reaction bursts deal full damage capped at max 1 hit per creature per cast.
  - Standalone Pure Kinetic Blast: vertical crush — knocks `PRONE` creatures with `floor(Strength / 2) <= RES_MEDIUM` (`Light/Medium`), crushes wood crates/barrels, shatters fragile ground glass flasks (applying liquid to tile + occupant), fractures all `ICE` in cross (`S6`), no gas interaction.
  - Piped Elemental Blast: intercepts first gas tile if path to epicenter crosses gas; resolves payload across cross with Tab `6` Col `S` deviations (`S7` full Thermal Shock dmg & simultaneous gas detonation; `S8` condense/dilute/precipitate cross; `S9` Steam->Oil Mist / Caustic->Toxic Tar; `S10` Acid rain on Steam & 1-tile cardinal perimeter expansion on Caustic).
  - Forbidden `blast -b <epicenter>` (Crimson Nova): pays `HP_COST_BLAST_BLOOD` up front, deals full `DMG_BLOOD + floor(Magic/2)` (ignores `BLAST_DMG_MULT` and armor) + `BLEEDING` to all living targets in cross, heals `BLOOD_KILL_HEAL` once if any already-`BLEEDING` target dies.
- **`src/main/java/com/archon/verb/SiphonVerb.java`**
  - Reach: `SIPHON_REACH_CHEBYSHEV` (`1`) or `SIPHON_REACH_MANHATTAN` (`4`) with `SpatialService.siphonLineOfFire` (gas endpoints allowed). Auto-hits.
  - Sources (`4A`):
    1. True fire (brazier, `LIT` torch, `BURNING` creature/prop/tile) takes priority over Living creature -> extinguishes fire & burning oil (`oilTurns = 0`, `burnTurns = 0`), resets to `BODY_TEMP` / `AMBIENT_TEMP`, emits `INFERNO`.
    2. Living creature -> fails if `temp <= ABS_ZERO` or `FROZEN`; deals `FROSTBITE_DMG` (ignores armor) and `SIPHON_COOL_DELTA / thermalMass`, emits `FIRE` (`Heat`).
    3. `siphon self` -> requires `OIL` on caster; clears `OIL` (`oilTurns = 0`), emits `OIL`.
    4. `@water` (puddle, water barrel, ground water flask), `@steam`, `@oil_mist`, `@caustic`, `@acid`, `@oil`: default clears source and emits liquid (`WATER`, `OIL`, `ACID`); `-t` on `@water` cools puddle by `SIPHON_COOL_DELTA` (to `ICE` at `<= FREEZE_TEMP`) and emits `FIRE`; `-t` on gas (`@steam`, `@oil_mist`, `@caustic`) condenses to floor (`WATER`, `OIL`, `ACID`) and emits `FIRE`; `-t` rejected on `@oil` and `@acid` pools.
  - Thermal promotion (`4B` R18): in `channel | siphon <heat src>`, when `materialIn` is `FIRE` and extracted payload is `FIRE` (or `INFERNO`), merges into `INFERNO` — if at end of pipeline, stores `INFERNO` in `thrall.channeledPayload` (`channelTurns = CHANNEL_MAX_TURNS`); if piped further (`| bolt` / `| blast`), emits `INFERNO` into `materialOut`.
  - Forbidden `siphon -b <creature>` (Blood Boil, `4C`): pays `HP_COST_SIPHON_BLOOD` up front, rejects self or non-`BLEEDING` target, consumes `BLEEDING` (`bleedTurns`), deals `BLOOD_BOIL_BASE + floor(Magic/2) + bleedTurns * BLEED_DMG` (ignores armor), applies `+TD_HEAT` ignoring `thermalMass` without `FIRE` tag (if `WET`, strips `WET` without Steam Flash and clamps temp at `BOIL_TEMP`; if Dry, enters `Scalded` / `Burning` per `5B`), heals `BLOOD_KILL_HEAL` on killing blow.
- **`src/main/java/com/archon/verb/PushVerb.java`** *(new; handles `push` and `pull` via `boolean pull`)*
  - `AP_PUSH` / `AP_PULL` (`2 AP`), Cardinal LoF within `PUSH_RANGE = 5`.
  - Force = `thrall.magic + (materialIn is FORCE ? FORCE_FOCUS_BONUS : 0)`.
  - Weight classes (`3C`):
    - Gas/liquid (`RES_GAS = 0`): moves `min(n, Force)` tiles via `PhysicsReactions.relocateGasOrLiquid` (pull stops before caster).
    - Light loose item (`RES_LIGHT = 1`): `push` rejected; `pull` requires distance `<= Force - RES_LIGHT` and catches item to pack (or emits `Material.OfItem` when piped into `| bolt [-a]`).
    - Creature (`Res = floor(Strength / 2)`) or wood prop (`RES_MEDIUM = 2`): moves `min(n, max(0, Force - Res))` tiles along axis (pull stops before caster), calling `PhysicsReactions.onEnterTile` per step (stopping early if creature slips `PRONE` or dies).
    - Heavy (`RES_HEAVY = 99` — doors, stone, metal, walls): `0` tiles.
  - Slam (`3B` R24): if blocked by wall or entity after travelling `>= SLAM_MIN_TRAVEL` (`1`) tile, deals `FORCE_COLLISION_DMG` (ignores armor) + `PRONE` to moving entity (and to struck entity on entity-into-entity slam); non-heavy wood props shatter at `0 HP`.
- **`src/main/java/com/archon/verb/ChannelVerb.java`** *(new)*
  - Single exclusive buffer (`thrall.channeledPayload`, `thrall.channelTurns`).
  - Thermal store (`<source> | channel`, `AP_CHANNEL_THERMAL = 0`): accepts `FIRE` or `INFERNO`, rejects liquids/items, stores payload with `channelTurns = CHANNEL_MAX_TURNS`.
  - Buffer release (`channel | <consumer>`, `0 AP`): requires `channeledPayload != null`, emits `Material.OfSubstance(channeledPayload)`, clears buffer (`channeledPayload = null`, `channelTurns = 0`).
  - Force focus (`channel -f`, `AP_CHANNEL_FORCE = 1`): standalone stores `Tag.FORCE` with `channelTurns = CHANNEL_MAX_TURNS`; at start of pipe (`channel -f | push/pull/bolt`) emits `Tag.FORCE` directly into pipe.
  - Forbidden `channel -b` (Blood Surge, `AP_CHANNEL_BLOOD = 0`): standalone only, max once per round (`!thrall.bloodSurgedThisRound`), pays `HP_COST_CHANNEL_BLOOD` up front, sets `bloodSurgedThisRound = true`, calls `c.round.gain(BLOOD_SURGE_AP)`.
- **`src/main/java/com/archon/app/DefaultVerbs.java`**
  - Register `bolt`, `blast`, `push` (`new PushVerb(false)`), `pull` (`new PushVerb(true)`), `channel`; add `-t` (`thermal`), `-b` (`blood`), `-r` (`roll`) `FlagSpec`s; update `siphon` AP (`AP_SIPHON`), `pass` flags, and pipeline `MaterialKind` metadata.

### Verification (Phase 4)
- Run `mvn test`.
- Add `SpellCombatTest.java`, `BoltAndBlastTest.java`, `SiphonAndChannelTest.java`, and `TelekinesisTest.java`: cover hit/crit/3-tier math, aimed zones & per-hand disarm/numb, missed bolt & item drop rules, all `4A` siphons, `channel` promotion/release/timeout, `push`/`pull` with `floor(Strength/2)` resistance & wall splash, and all 4 `-b` Blood verbs.

---

## Phase 5 — Round-End Simulation, Melee/Ranged Hooks & AI

**Goal:** Wire the 4-step `5A` round-end simulation loop, apply magic status modifiers to melee/ranged combat, and update enemy AI.

### Files
- **`src/main/java/com/archon/system/environment/SimulationSystem.java`**
  - Implement strict `5A` round-end 4-step loop (classic index `for` loops, zero `Stream` allocations):
    1. **Status & tile DoTs** (all ignore armor):
       - Creature status DoTs: `BLEED_DMG` (`BLEEDING`), `SCALD_DOT_DMG` (Dry at `BOIL_TEMP .. < IGNITE_TEMP`), `BURN_TICK_DMG` (`BURNING`), plus `+INFERNO_TICK_DMG` if `temp >= INFERNO_MIN_TEMP` (then clamp `temp = BURN_CAP`).
       - Tile DoTs on occupants: `CAUSTIC_DMG` + `-ACID_ARMOR_SHRED` + `CORRODED` (`CAUSTIC_GAS`, `Acid pool`, `Toxic Tar`); `BURNING` tile (`Grease Fire` or burning floor/prop) deals `BURN_TICK_DMG` and ignites occupant only if occupant is not already `BURNING` (no double burn tick).
       - Call `PhysicsReactions.onCreatureDamaged` on Thrall if total round-end damage > 0.
    2. **Duration decrements & Channel timeout:**
       - Decrement `wetTurns` (remove `WET` at 0 unless standing in `WATER`), `oilTurns` (both unlit and burning; remove unlit `OIL` at 0), `bleedTurns` (remove `BLEEDING` & `BLOOD` at 0), `dazedTurns` (`DAZED`), `numbArmTurns` (`NUMB_ARM` + clear `numbArmPart`), `crippledTurns` (`CRIPPLED`), `stunTurns` (`STUNNED`, only when `!has(FROZEN)`), `blindLingerTurns` (if currently in gas cloud, keep `BLIND` and `blindLingerTurns = BLIND_LINGER_TURNS`; if outside gas, decrement `blindLingerTurns` and remove `BLIND` at 0), `propBurnTurns` (destroy burning wood prop at 0).
       - Decrement `thrall.channelTurns`: when it reaches `0`, clear buffer and trigger `4B` overload backfire on caster's tile.
    3. **Creature temp drift & thermal transitions:**
       - Below `BODY_TEMP`: warm by `floor(CHILL_WARM_STEP * thermalMass)`.
       - Above `BODY_TEMP`: hold `BURN_CAP` if `BURNING` and `oilTurns > 0` (`Burning (grease)`); otherwise cool by fixed `CREATURE_HOT_COOL_STEP` toward `BODY_TEMP`.
       - If `FROZEN` and `temp > FREEZE_TEMP`, call `PhysicsReactions.exitFrozen`.
       - If `BURNING` and `temp < IGNITE_TEMP`, remove `BURNING` and `OIL`, and reset cleanly to Dry at `BODY_TEMP` (never enters `Scalded`).
       - Sync `CHILLED` (`1..CHILLED_MAX_TEMP`, or `<= FREEZE_TEMP` while Dry) and clot/cauterize `BLEEDING`.
    4. **Tile, prop & gas decay:**
       - Gas layer: if tile has `STEAM`, `CAUSTIC_GAS`, or `OIL_MIST`, decrement `gasTemp` by `TILE_DECAY_STEP`; clear `STEAM` below `STEAM_CLEAR_TEMP`; clear `OIL_MIST` below `STEAM_CLEAR_TEMP` and add floor `OIL` slick (`oilTurns = OIL_TURNS`); clear `CAUSTIC_GAS` below `CAUSTIC_CLEAR_TEMP`.
       - Floor layer & props: hold `BURN_CAP` while `oilTurns > 0` (`Grease Fire`) or `burnTurns > 0` (decrementing each); once `0`, cool by `TILE_HOT_COOL_STEP` above `BOIL_TEMP` (removing `BURNING` and `OIL` below `IGNITE_TEMP`) or drift by `TILE_DECAY_STEP` toward `AMBIENT_TEMP` at `<= BOIL_TEMP`; melt `ICE` at `>= WARM_MELT_TEMP` to `WATER`; freeze `WATER` at `<= FREEZE_TEMP` to `ICE`.
- **`src/main/java/com/archon/system/combat/CombatEngine.java`, `StrikeVerb.java`, `ShootVerb.java`**
  - `CombatEngine`: include `EVA_CHILLED` (`-15`) and `EVA_FROZEN` (`-20`) in `effectiveEvasion`; apply `DAZED_HIT_PENALTY` (`-20`) on attacker; apply `NUMB_ARM_DMG_MULT` (`0.5`) when attacking with a weapon in the numbed arm (`numbArmPart`); call `PhysicsReactions.onCreatureDamaged` when Thrall takes melee/ranged damage.
  - `StrikeVerb` & `ShootVerb`: enforce `BLIND_MAX_RANGE = 1` when `BLIND`.
- **`src/main/java/com/archon/system/ai/AiSystem.java`**
  - Respect `FROZEN`/`STUNNED` skip, `PRONE` stand-up (`AP_STAND_UP`), `CHILLED_AP_STEP` + `CRIPPLED_AP_STEP` movement costs, `BLIND_MAX_RANGE`, and `actor.has(Tag.CRIPPLED)`.
  - Route `stepActor` through `PhysicsReactions.onEnterTile(..., true, log)`.
  - Update hazard awareness (`isHazard`) for `BURNING`, `CAUSTIC_GAS`, `OIL_MIST`, `ACID`.

### Verification (Phase 5)
- Run `mvn test`.
- Add `SimulationAndAiTest.java`: verify 4-step round-end ordering, `BLIND_LINGER_TURNS` 1-round linger after exiting gas, `thermalMass` thaw pacing, `Burning (grease)` 3-turn hold + 2-turn cool, `Oil Mist` precipitation, `NUMB_ARM`/`DAZED` melee/ranged penalties, and AI hazard escape.

---

## Phase 6 — UI Views, Inspection, Manual & Scenario

**Goal:** Surface all new states, stats, and payloads cleanly in console/graphical views and documentation without breaking existing UI/Scenario tests.

### Files
- **`src/main/java/com/archon/ui/Scene.java`**
  - Add snapshot arrays/fields for `water`, `ice`, `acid`, `toxicTar`, `steam`, `causticGas`, `oilMist`, Thrall `magic`, `concentration`, `channel`, `temp`, `shortConds`, `thrallStunned`, `thrallFrozen`.
- **`src/main/java/com/archon/ui/ConsoleView.java`**
  - **Bugfix:** clamp `"o ".repeat(Math.max(0, round.rules().roundAp() - round.ap()))` (line 164) so `channel -b` (`ap > roundAp`) never throws `IllegalArgumentException`.
  - Render new tile glyphs and show `Magic`, `Temp`, `Channel`, and condition tags in `hud(...)`.
- **`src/main/java/com/archon/ui/GdxView.java`**
  - **Bugfix:** draw AP pips up to `Math.max(roundAp, ap)` so Blood Surge AP renders cleanly.
  - Render water/ice/acid/tar puddles, steam/caustic/oil-mist clouds, `FROZEN`/`STUNNED`/`BLEEDING` overlays, and `MAGIC` / `CONC` / `CHANNEL` / `TEMP` / `CONDS` HUD lines.
- **`src/main/java/com/archon/verb/InspectVerb.java`, `StatusVerb.java`, `ScanVerb.java`**
  - Display `temp`, `thermalMass`, `magic`, active status durations (`wetTurns`, `oilTurns`, `bleedTurns`, etc.), `channel` buffer, and tile `temp`/`gasTemp`/gases/puddles.
- **`src/main/java/com/archon/verb/CommandManual.java` & `src/main/java/com/archon/app/Scenario.java`**
  - Update manual topics (`economy`, `flags`, `operators`, `address`) for pipeline AP summing, `-t`/`-b`/`-r`/`-f`, and new verbs.
  - Keep `Scenario.testRoom` baseline compatible with existing tests and add a `magicArena` scenario helper if needed.

### Verification (Phase 6)
- Run full `mvn test` — all baseline tests + all new magic test suites pass.
- Smoke-test interactive commands in `ConsoleView` (`siphon`, `channel`, `bolt`, `blast`, `push`, `pull`, `pass -r`, `-b` Blood Surge AP display).
