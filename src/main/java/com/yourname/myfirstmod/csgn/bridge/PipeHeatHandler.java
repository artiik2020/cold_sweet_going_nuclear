package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates local heat from Create pipe candidates carrying detectable steam.
 * Temperature writes are centralised in HeatTickHandler to prevent each source
 * from independently adding to the player's temperature.
 */
public final class PipeHeatHandler {
    private PipeHeatHandler() {}

    public static double heatForPlayer(ServerLevel level, ServerPlayer player) {
        double radius = BridgeConfig.PIPE_RADIUS.get();
        double radiusSquared = radius * radius;
        double total = 0.0;
        List<BlockPos> stale = new ArrayList<>();

        for (BlockPos pipe : PositionCache.pipes(level)) {
            if (!level.isLoaded(pipe)) {
                continue;
            }

            if (!PositionCache.isCachedPipeState(level.getBlockState(pipe))) {
                stale.add(pipe);
                continue;
            }

            double distanceSquared = player.distanceToSqr(
                pipe.getX() + 0.5, pipe.getY() + 0.5, pipe.getZ() + 0.5);
            if (distanceSquared > radiusSquared) {
                continue;
            }

            if (!SteamFluidHelper.containsConfiguredSteam(level, pipe)) {
                continue;
            }

            double distance = Math.sqrt(distanceSquared);
            double falloff = Math.max(0.0, 1.0 - distance / radius);
            total += BridgeConfig.STEAM_HEAT_MULTIPLIER.get() * falloff;
        }

        for (BlockPos pos : stale) {
            PositionCache.remove(level, pos);
        }

        return total;
    }
}
