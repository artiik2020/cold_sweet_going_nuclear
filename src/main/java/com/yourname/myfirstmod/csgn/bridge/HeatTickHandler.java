package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single server-side entry point for calculating the combined local heat contribution.
 * Values are read from BridgeConfig at runtime, not copied into static startup fields.
 */
public final class HeatTickHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/HeatTickHandler");

    private HeatTickHandler() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        int interval = Math.max(1, BridgeConfig.UPDATE_INTERVAL.get());
        if (level.getGameTime() % interval == 0L) {
            int scanRadius = (int) Math.ceil(Math.max(
                BridgeConfig.REACTOR_RADIUS.get(), BridgeConfig.PIPE_RADIUS.get()));
            scanRadius = Math.max(1, Math.min(32, scanRadius));

            for (ServerPlayer player : level.players()) {
                PositionCache.scanNear(level, player.blockPosition(), scanRadius);
            }

            if (BridgeConfig.ENABLE_CONDENSATION.get()) {
                // Only explicitly configured output blocks can condense fluid. This is not
                // run by the per-player heat calculation, so multiple players won't multiply drains.
                for (net.minecraft.core.BlockPos pipe : PositionCache.pipes(level)) {
                    if (level.isLoaded(pipe)) {
                        SteamFluidHelper.tryCondenseAtConfiguredOutput(level, pipe);
                    }
                }
            }
        }

        for (ServerPlayer player : level.players()) {
            double reactorHeat = ReactorHandler.heatForPlayer(level, player);
            double steamHeat = PipeHeatHandler.heatForPlayer(level, player);
            double configuredCap = BridgeConfig.MAX_TOTAL_HEAT.get();
            double desiredHeat = Math.min(configuredCap, Math.max(0.0, reactorHeat + steamHeat));
            ColdSweatAdapter.applyWorldHeat(level, player, desiredHeat);
        }

        if (level.getGameTime() % 1200L == 0L) {
            LOGGER.info("Текущая конфигурация нагрева в {}: reactorRadius={}, reactorHeat={}, pipeRadius={}, steamHeat={}, maxTotalHeat={}, updateInterval={}, reactorCandidates={}, pipeCandidates={}, players={}",
                level.dimension().location(),
                BridgeConfig.REACTOR_RADIUS.get(),
                BridgeConfig.REACTOR_HEAT_MULTIPLIER.get(),
                BridgeConfig.PIPE_RADIUS.get(),
                BridgeConfig.STEAM_HEAT_MULTIPLIER.get(),
                BridgeConfig.MAX_TOTAL_HEAT.get(),
                interval,
                PositionCache.reactors(level).size(),
                PositionCache.pipes(level).size(),
                level.players().size());
        }
    }
}
