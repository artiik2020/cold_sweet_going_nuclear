package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates local heat from Create pipe candidates carrying detectable steam.
 * Temperature writes are centralised in HeatTickHandler to prevent each source
 * from independently adding to the player's temperature.
 */
public final class PipeHeatHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/SteamPipeHeatHandler");
    private static final Set<String> LOGGED_HOT_PIPES = ConcurrentHashMap.newKeySet();

    private PipeHeatHandler() {}

    public static void resetDiagnostics() {
        LOGGED_HOT_PIPES.clear();
    }

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
            double multiplier = BridgeConfig.STEAM_HEAT_MULTIPLIER.get();
            total += multiplier * falloff;

            String logKey = level.dimension().location() + "|" + pipe;
            if (LOGGED_HOT_PIPES.add(logKey)) {
                LOGGER.info("Труба найдена, пар есть, применяю тепло: pipe={}, radius={}, steamHeatMultiplier={}, distance={}, player={}",
                    pipe, radius, multiplier, distance, player.getGameProfile().getName());
            }
        }

        for (BlockPos pos : stale) {
            PositionCache.remove(level, pos);
        }

        return total;
    }
}
