# Runtime heat integration review (NeoForge 1.21.1)

## Root causes found

1. `ColdSweatAdapter.applyHeat` was a no-op TODO. The tick handlers could run without ever changing a Cold Sweat temperature trait.
2. Reactor/pipe caches were populated only for configured IDs, but the config lists defaulted to empty. A custom reactor block could be registered by its block class and then rejected by the handler because its ID was not in the configured list.
3. The previous steam check required an exact configured fluid ID and therefore always returned false while the list was empty.
4. Create pipe blocks may not expose a Fluid Handler capability at the pipe position. The helper now logs this at DEBUG level; if capability is absent, actual Create fluid-network integration still needs a Create-specific adapter rather than assuming every pipe exposes a Forge fluid handler.
5. Server tick handlers are registered on `NeoForge.EVENT_BUS`, which is the correct bus for gameplay tick events. A separate mod-bus listener is not needed for `ServerTickEvent.Post`.

## Current behaviour

- The Cold Sweat adapter attempts the public Temperature API at runtime using `Temperature.add(entity, Trait.WORLD, amount)`, with a `get/set` fallback for API builds that expose those methods. This is server-side and the API is responsible for syncing player temperature.
- Reactor and Create fluid-pipe placement events are heuristically cached; explicit IDs in the server config are also supported.
- Steam detection accepts configured fluid IDs or, when none are configured, fluid registry paths containing `steam`.
- Heat is applied to nearby players, not every block in a radius. This is intentional: Cold Sweat's documented Temperature API modifies entity temperature, not a generic ambient block-temperature field.
- The helper does not automatically drain steam. Condensation requires a dedicated radiator/output implementation and should not destroy fluid during passive detection.

## Test procedure

1. Run `./gradlew.bat compileJava` and launch the client with Cold Sweat and Create: Going Nuclear installed.
2. In a world with cheats enabled, run `/temperature get @s world` and `/temperature get @s core` before the test.
3. Place the reactor near the player and wait at least 20 ticks (the default handler interval). Compare `/temperature get @s world` again.
4. For diagnostics, use `/temperature debug @s world` if supported by the installed Cold Sweat version. The loggers `CSGN/HeatTickHandler`, `CSGN/SteamPipeHeatHandler`, `CSGN/PositionCache`, and `CSGN/SteamFluidHelper` emit DEBUG messages in the development run configuration.
5. For pipe testing, inspect the log for `No block FluidHandler capability` and `Fluid at ... steamMatch=...`. If no capability exists on Create's pipe block, report the Create version and the exact pipe/tank setup; this confirms the remaining work is Create's fluid-network adapter, not Cold Sweat temperature mutation.

## Limitations / version-specific follow-up

The Going Nuclear block IDs and its internal active-reactor state were not guessed. The current cache candidate matching uses the mod namespace and names containing `reactor`, `rbmk`, or `core`; verify actual IDs against the installed jar and add them to the server config for reliable behaviour. The current implementation does not inspect whether a reactor is active/hot, so it can apply heat near a matching reactor block even when inactive. Likewise, Create pipe capability availability must be confirmed in-game.
