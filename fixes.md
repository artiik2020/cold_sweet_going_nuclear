# Fixes and Change Log

Project: **Cold Sweat + Create: Going Nuclear bridge**  
Repository: [Artem4ik493/cold_sweet_going_nuclear](https://github.com/Artem4ik493/cold_sweet_going_nuclear)  
Branch: `master`  
Minecraft: 1.21.1 · NeoForge: 21.1.256

This file records the fixes and implementation changes made during the runtime heat-integration review. It distinguishes code changes from behaviour that still needs to be verified in a real game.

## 2026-10-28 — Runtime heat integration review

### Main problem found

The mod could compile and launch while the heat integration still did nothing. The most important cause was that `ColdSweatAdapter.applyHeat(...)` was an empty TODO method. Reactor and pipe tick handlers could reach the adapter, but no Cold Sweat temperature value was changed.

There were also independent discovery/configuration problems: reactor and pipe ID lists were empty by default, cache entries could be rejected by a second strict ID check, and steam detection depended on an explicitly configured fluid ID.

### Changes by file

#### `src/main/java/com/yourname/myfirstmod/csgn/bridge/ColdSweatAdapter.java`

- Replaced the no-op heat method with a runtime adapter that attempts to call Cold Sweat's Temperature API.
- It searches known API class-name candidates, finds the `WORLD` temperature trait, and tries `Temperature.add(entity, trait, amount)`; a `get/set` fallback is attempted if the add method is unavailable.
- Temperature modification is performed for the target entity on the server side. The Cold Sweat API is expected to handle player synchronization.
- Added diagnostics for successful API calls and failures, so an incompatible API/version should be easier to identify.

**Effect intended:** nearby entities can receive a world-temperature adjustment from a detected heat source instead of the bridge silently doing nothing.

**Important:** this uses runtime reflection and has not yet been confirmed against the exact Cold Sweat jar in a live game. If the installed version exposes a different API shape, the logs should identify the failure and the adapter may need a version-specific adjustment.

#### `src/main/java/com/yourname/myfirstmod/csgn/bridge/PositionCache.java`

- Kept event-driven tracking for block placement/removal rather than scanning the entire world every tick.
- Added candidate detection for the bridge's own `csgn:reactor_core` block.
- Added heuristic recognition for likely Going Nuclear reactor blocks (mod namespace/path hints such as `goingnuclear`, `reactor`, `rbmk`, or `core`) and Create fluid pipes such as `create:fluid_pipe`.
- Added helpers to validate cached reactor/pipe states without relying solely on config lists that may be empty.
- Added debug diagnostics for cache additions/removals and candidate matching.

**Effect intended:** relevant blocks can enter the cache with default configuration, while explicit server-config IDs remain available for reliable, installation-specific matching.

**Limitation:** heuristic registry-name matching is a fallback, not proof of the actual IDs used by every Going Nuclear build. Confirm IDs from the installed mod jar and configure them explicitly if necessary.

#### `src/main/java/com/yourname/myfirstmod/csgn/bridge/SteamFluidHelper.java`

- Kept support for explicit configured steam-fluid IDs.
- When the configured list is empty, added a fallback that recognises fluid registry paths containing `steam`.
- Added debug logging for fluid matches and for blocks that do not expose a block Fluid Handler capability.

**Effect intended:** steam detection no longer automatically fails just because the optional config list is empty.

**Limitation:** Create's fluid pipes may not expose a Forge/NeoForge Fluid Handler capability at the pipe block itself. If the helper logs that the capability is missing, a Create-specific fluid-network adapter is still needed; this change does not claim to read every connected pipe network.

#### `src/main/java/com/yourname/myfirstmod/csgn/bridge/ReactorHandler.java`

- Uses the updated position-cache validation helpers rather than rejecting blocks only because they are absent from the configured ID list.
- Routes detected reactor heat through `ColdSweatAdapter.applyHeat(...)` for nearby players.
- Added periodic debug output to help determine whether the handler sees candidate sources and applies its logic.

**Effect intended:** a detected reactor can actually reach the Cold Sweat temperature adapter.

**Limitation:** the current matching does not verify the reactor's internal active/hot state. A matching block may therefore affect nearby players even if the reactor is inactive; the exact Going Nuclear state API still needs verification.

#### `src/main/java/com/yourname/myfirstmod/csgn/bridge/PipeHeatHandler.java`

- Uses the updated pipe-cache validation helpers.
- Checks steam fluid through the updated helper and routes detected heat to nearby players.
- Added diagnostics for pipe candidates and steam matching.

**Effect intended:** the pipe heat path can operate when steam is identified, rather than failing immediately due to an empty fluid-ID list.

**Limitation:** actual fluid discovery depends on whether the pipe exposes a capability at its position. Connected Create fluid networks may require a dedicated integration.

#### `src/main/java/com/yourname/myfirstmod/csgn/BridgeConfig.java`

- Lowered the default reactor heat multiplier from `1.0` to `0.05`.
- Lowered the default steam heat multiplier from `0.5` to `0.025`.

**Reason:** these are conservative starting values for repeated server-tick temperature changes. They are intended to reduce the risk of an excessively fast temperature swing while the exact Cold Sweat API units and in-game response are being validated. Adjust them only after testing.

#### `RUNTIME_HEAT_REVIEW.md`

- Added a focused review of the root causes, current runtime behaviour, test procedure, and remaining version-specific integration work.

### Event registration review

The reactor and pipe handlers are registered on `NeoForge.EVENT_BUS` and use `ServerTickEvent.Post`. This is the appropriate gameplay event bus for those server tick handlers; moving these listeners to the mod event bus would not fix the no-op temperature adapter.

### Expected behaviour after these changes

1. Relevant reactor/pipe candidates are tracked when placed or removed.
2. Reactor handling can call the Cold Sweat adapter for nearby players.
3. Pipe handling attempts to detect steam using configured IDs or a registry-name fallback.
4. Diagnostics should help separate a cache/handler problem, a Cold Sweat API mismatch, and a missing Create fluid capability.

The above is the intended implementation path, **not a claim that all of these paths have already been proven in-game**.

## Verification status

At the time this log was updated:

- [x] Code review found and documented the no-op adapter and empty-config discovery issues.
- [x] Changes were committed to the GitHub `master` branch.
- [ ] The newest changes have not been independently confirmed by a fresh `compileJava` run in this review.
- [ ] Runtime temperature change has not yet been confirmed in a live world.
- [ ] Actual Going Nuclear registry IDs and active-reactor state still need verification.
- [ ] Create pipe/tank capability behaviour and connected-network steam discovery still need verification.

Do not treat the integration as fully validated until compilation and the in-game checks below pass.

## Test checklist

1. Pull the latest branch:
   ```powershell
   git pull origin master
   .\gradlew.bat compileJava
   ```
2. Launch Minecraft 1.21.1 with the matching NeoForge, Cold Sweat, Create, and Create: Going Nuclear versions.
3. With cheats enabled, record the current values using `/temperature get @s world` and, if supported by the installed Cold Sweat version, `/temperature get @s core`.
4. Stand near a matching reactor and wait at least 20 server ticks. Query the world temperature again and compare the result.
5. If the temperature does not change, inspect the development log for messages from `CSGN/HeatTickHandler`, `CSGN/ColdSweatBridge`, and `CSGN/PositionCache`.
6. For pipe testing, inspect `CSGN/SteamFluidHelper` for `steamMatch=true` or a message indicating that the block has no Fluid Handler capability. If the capability is absent, record the Create version and the exact pipe/tank arrangement.
7. Report the full `compileJava` output and the relevant `CSGN` log lines before making further assumptions about the cause.

## Earlier and current scope

This document records the bridge changes already made in this review, including the current documentation update. It does not claim that the separate mod's own reactor internals, fluid-network APIs, or every Cold Sweat version have been fully reverse-engineered. Future fixes should be appended here with the date, affected files, cause, implementation, and verification result.
