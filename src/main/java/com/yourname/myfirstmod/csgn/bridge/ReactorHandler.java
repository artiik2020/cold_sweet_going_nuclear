package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class ReactorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/HeatTickHandler");
    private static long tickCounter;
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

                if (!PositionCache.isCachedReactorState(level.getBlockState(reactor))) {
                    stale.add(reactor);
                    continue;
                }

                applyHeatToNearbyPlayers(level, reactor,
                    BridgeConfig.REACTOR_RADIUS.get(),
                    BridgeConfig.REACTOR_HEAT_MULTIPLIER.get());
            }
            PositionCache.reactors(level).removeAll(stale);
        }

        if (++tickCounter % 1200 == 0) {
            LOGGER.debug("Heat tick active; cached reactor positions={}",
                event.getServer().getAllLevels().stream()
                    .mapToInt(level -> PositionCache.reactors(level).size()).sum());
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
            ColdSweatAdapter.applyHeat(level, source, player, strength);
        }
    }
}
