package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One server-side tick handler for both heat sources.
 * LevelTickEvent.Post is per level, so every dimension is processed separately.
 */
public final class HeatTickHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/HeatTickHandler");
    private static final double MAX_HEAT_OFFSET = 0.75;

    private HeatTickHandler() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            // LevelTickEvent also fires on the logical client; never mutate temperature there.
            return;
        }

        int interval = Math.max(1, BridgeConfig.UPDATE_INTERVAL.get());
        if (level.getGameTime() % interval == 0L) {
            int scanRadius = (int) Math.ceil(Math.max(
                BridgeConfig.REACTOR_RADIUS.get(), BridgeConfig.PIPE_RADIUS.get()));
            scanRadius = Math.max(1, Math.min(32, scanRadius));

            // Discover existing blocks too; placement events alone miss sources that predate
            // the mod startup or were already present when a chunk loaded.
            for (ServerPlayer player : level.players()) {
                PositionCache.scanNear(level, player.blockPosition(), scanRadius);
            }
        }

        // Re-apply a bounded contribution after Cold Sweat's per-tick temperature updates.
        // Both source types are aggregated into a single offset so they cannot compound
        // by repeatedly adding their full configured values to Trait.WORLD.
        for (ServerPlayer player : level.players()) {
            double reactorHeat = ReactorHandler.heatForPlayer(level, player);
            double steamHeat = PipeHeatHandler.heatForPlayer(level, player);
            double desiredHeat = Math.min(MAX_HEAT_OFFSET, Math.max(0.0, reactorHeat + steamHeat));
            ColdSweatAdapter.applyWorldHeat(level, player, desiredHeat);
        }

        if (level.getGameTime() % 1200L == 0L) {
            LOGGER.debug("Heat tick active in {}; reactorCandidates={}, pipeCandidates={}, players={}",
                level.dimension().location(),
                PositionCache.reactors(level).size(),
                PositionCache.pipes(level).size(),
                level.players().size());
        }
    }
}
