package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates local heat from cached reactor candidates.
 * The main tick event and final temperature write are handled by HeatTickHandler.
 */
public final class ReactorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/HeatTickHandler");

    private ReactorHandler() {}

    public static double heatForPlayer(ServerLevel level, ServerPlayer player) {
        double radius = BridgeConfig.REACTOR_RADIUS.get();
        double radiusSquared = radius * radius;
        double total = 0.0;
        List<BlockPos> stale = new ArrayList<>();

        for (BlockPos reactor : PositionCache.reactors(level)) {
            if (!level.isLoaded(reactor)) {
                continue;
            }

            if (!PositionCache.isCachedReactorState(level.getBlockState(reactor))) {
                stale.add(reactor);
                continue;
            }

            double distanceSquared = player.distanceToSqr(
                reactor.getX() + 0.5, reactor.getY() + 0.5, reactor.getZ() + 0.5);
            if (distanceSquared > radiusSquared) {
                continue;
            }

            if (!isOperationalReactor(level, reactor)) {
                continue;
            }

            double distance = Math.sqrt(distanceSquared);
            double falloff = Math.max(0.0, 1.0 - distance / radius);
            total += BridgeConfig.REACTOR_HEAT_MULTIPLIER.get() * falloff;
        }

        for (BlockPos pos : stale) {
            PositionCache.remove(level, pos);
        }

        return total;
    }

    /**
     * TODO: Artem's Task - Verify the actual active/hot state exposed by the installed
     * Create: Going Nuclear version and replace this permissive default with that check.
     *
     * Safe, working default: recognised reactor blocks emit heat. This avoids inventing
     * a field or method from Going Nuclear's internal API.
     */
    private static boolean isOperationalReactor(ServerLevel level, BlockPos pos) {
        return true;
    }
}
