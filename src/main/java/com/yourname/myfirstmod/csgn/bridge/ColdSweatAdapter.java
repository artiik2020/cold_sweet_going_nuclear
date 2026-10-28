package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Boundary for Cold Sweat integration.
 *
 * TODO: Artem's Task
 * Implement this adapter against the public API of the exact Cold Sweat version
 * selected for the modpack. Do not assume a temperature API from another version.
 */
public final class ColdSweatAdapter {
    private ColdSweatAdapter() {}

    public static boolean isAvailable() {
        return net.neoforged.fml.ModList.get().isLoaded("cold_sweat");
    }

    /**
     * Requests a heat contribution at a position.
     * This safe scaffold intentionally does not mutate Cold Sweat internals.
     */
    public static void applyHeat(ServerLevel level, BlockPos source, BlockPos target, double strength) {
        if (strength <= 0.0 || !isAvailable()) {
            return;
        }

        // TODO: Artem's Task — route strength through a documented Cold Sweat API.
        // Avoid changing block states or spawning particles as a substitute for temperature.
    }
}
