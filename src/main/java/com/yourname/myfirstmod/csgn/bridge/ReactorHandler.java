package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

public final class ReactorHandler {
    private ReactorHandler() {}

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
            for (BlockPos reactor : PositionCache.reactors(level)) {
                if (!level.isLoaded(reactor)) {
                    continue;
                }

                if (!PositionCache.matches(level.getBlockState(reactor), BridgeConfig.REACTOR_BLOCK_IDS.get())) {
                    stale.add(reactor);
                    continue;
                }

                // TODO: Artem's Task — read the active/hot state from the verified Going Nuclear API.
                applyHeatToNearbyPlayers(level, reactor, BridgeConfig.REACTOR_RADIUS.get(),
                    BridgeConfig.REACTOR_HEAT_MULTIPLIER.get());
            }
            PositionCache.reactors(level).removeAll(stale);
        }
    }

    /**
     * Processes players rather than iterating every block in a large radius.
     * This avoids millions of block checks for a 100-block reactor radius.
     */
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
