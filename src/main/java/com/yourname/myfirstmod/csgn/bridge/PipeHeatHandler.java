package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

public final class PipeHeatHandler {
    private PipeHeatHandler() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) {
            return;
        }

        int interval = BridgeConfig.UPDATE_INTERVAL.get();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % interval != 0L) {
                continue;
            }

            List<BlockPos> stale = new ArrayList<>();
            for (BlockPos pipe : PositionCache.pipes(level)) {
                if (!level.isLoaded(pipe)) {
                    continue;
                }

                if (!PositionCache.matches(level.getBlockState(pipe), BridgeConfig.PIPE_BLOCK_IDS.get())) {
                    stale.add(pipe);
                    continue;
                }

                if (!SteamFluidHelper.containsConfiguredSteam(level, pipe)) {
                    continue;
                }

                applyHeatToNearbyPlayers(level, pipe, BridgeConfig.PIPE_RADIUS.get(),
                    BridgeConfig.STEAM_HEAT_MULTIPLIER.get());
            }
            PositionCache.pipes(level).removeAll(stale);
        }
    }

    private static void applyHeatToNearbyPlayers(
        ServerLevel level, BlockPos source, double radius, double strength
    ) {
        double radiusSquared = radius * radius;
        double centerX = source.getX() + 0.5;
        double centerY = source.getY() + 0.5;
        double centerZ = source.getZ() + 0.5;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(centerX, centerY, centerZ) > radiusSquared) {
                continue;
            }
            ColdSweatAdapter.applyHeat(level, source, player.blockPosition(), strength);
        }
    }
}
