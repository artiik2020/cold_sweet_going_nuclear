package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates local heat from cached reactor candidates.
 * The main tick event and final temperature write are handled by HeatTickHandler.
 */
public final class ReactorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/HeatTickHandler");
    private static final Set<String> LOGGED_REACTORS = ConcurrentHashMap.newKeySet();

    private ReactorHandler() {}

    public static void resetDiagnostics() {
        LOGGED_REACTORS.clear();
    }

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
            double multiplier = BridgeConfig.REACTOR_HEAT_MULTIPLIER.get();
            total += multiplier * falloff;

            String logKey = level.dimension().location() + "|" + reactor;
            if (LOGGED_REACTORS.add(logKey)) {
                LOGGER.info("Реактор найден, читаю конфиг: radius={}, heatMultiplier={}, pos={}, distance={}, player={}",
                    BridgeConfig.REACTOR_RADIUS.get(), multiplier, reactor, distance, player.getGameProfile().getName());
            }
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
