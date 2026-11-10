# Project Philosophy: Gamedev / C++ Mindset in Java

We are building a game with a C++ / data-oriented mindset, NOT enterprise software (No Java Enterprise / Spring abstractions). Complexity and code bloat are the enemies.

### 1. No Enterprise Patterns & Over-Engineering
- **No unnecessary abstractions**: Do NOT create single-implementation interfaces (`PlayerService` -> `PlayerServiceImpl`), factories, builders, providers, or deep inheritance trees.
- **Flat, simple code**: Treat classes like C++ structs/components. Public fields or simple direct access are preferred over heavy encapsulation boilerplate.
- **KISS (Keep It Simple, Stupid)**: The simplest working solution inline is always superior to an over-architected "future-proof" design.

### 2. Fail-Fast Over Defensive Programming
- **Zero defensive coding**: Do NOT scatter paranoid `if (x == null)` checks throughout inner game logic.
- **Never swallow errors**: No silent `try/catch` blocks or fallback null-returns.
- **Crash early at runtime**: If game state is invalid, let it crash immediately (e.g., fast `NullPointerException` or unhandled runtime exceptions). This exposes bugs instantly via clear stack traces during development rather than masking corrupted state.

### 3. Refactoring & Code Volume (LOC) Rules
- **Refactoring must reduce or maintain LOC**: A refactor should simplify the code. It must NEVER inflate line count unless explicitly justified.
- Do NOT break a clean 20-line method into 3 new classes, Command patterns, or visitor handlers.
- Any change that increases overall complexity or line count requires explicit technical justification (e.g., critical GC / CPU optimization).

### 4. Game Loop Performance (Update / Tick)
- Avoid heap allocations (`new Object()`) inside frame/tick routines (`update`, `render`).
- Prefer simple arrays, flat contiguous collections, and classic index-based loops (`for (int i = 0; ...)`) over heavy Java Streams (`Stream.map().filter()`) to prevent Garbage Collection pressure.

For gaining info about dependencies use rg ... commandline tool 