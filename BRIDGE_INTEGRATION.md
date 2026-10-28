# Bridge integration status

This repository currently contains the Create: Going Nuclear mod scaffold. The bridge integration is being added without replacing the existing mod entry point or registry setup.

## Implemented scaffold
- Common configuration for reactor/pipe radii, heat multipliers, update interval, and configured registry IDs.
- Event-driven cache for configured reactor and pipe block positions.
- Server-side periodic processing using `ServerLevel#getGameTime()`.
- Loaded-position checks before block reads and heat target processing.
- Fluid inspection through the NeoForge block Fluid Handler capability.
- Explicit, opt-in fluid draining helper; no automatic draining occurs just because steam is detected.

## Required before gameplay integration is complete
- Confirm exact mod IDs and block/fluid registry IDs from the installed Going Nuclear/Create/Cold Sweat versions.
- Implement `ColdSweatAdapter.applyHeat` using a documented public API from the selected Cold Sweat version.
- Connect reactor detection to the real Going Nuclear reactor state/API.
- Add a dedicated radiator block and implement condensation output handling if desired.
- Add startup discovery for existing loaded reactor/pipe blocks without scanning all loaded chunks every tick.
- Build and run the Gradle client/server configurations against the exact dependency jars.

The configured block and fluid ID lists intentionally default to empty. This avoids accidentally treating unrelated blocks or fluids as reactors or steam. No claim is made that the cross-mod temperature effect is complete until the public API adapter is implemented and tested.
