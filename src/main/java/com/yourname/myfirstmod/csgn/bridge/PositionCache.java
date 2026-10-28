package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks candidate heat-source blocks. Block events keep the cache current;
 * scanNear also discovers blocks that were already present when the world loaded.
 */
public final class PositionCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/PositionCache");
    private PositionCache() {}

    private static final Map<ResourceLocation, Set<BlockPos>> REACTORS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Set<BlockPos>> PIPES = new ConcurrentHashMap<>();

    public static Set<BlockPos> reactors(ServerLevel level) {
        return REACTORS.computeIfAbsent(level.dimension().location(), key -> ConcurrentHashMap.newKeySet());
    }

    public static Set<BlockPos> pipes(ServerLevel level) {
        return PIPES.computeIfAbsent(level.dimension().location(), key -> ConcurrentHashMap.newKeySet());
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            update(level, event.getPos(), event.getPlacedBlock());
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            remove(level, event.getPos());
        }
    }

    public static void registerReactor(ServerLevel level, BlockPos pos) {
        BlockPos key = pos.immutable();
        if (reactors(level).add(key)) {
            LOGGER.debug("Cached reactor candidate at {} in {}", key, level.dimension().location());
        }
    }

    public static void unregisterReactor(ServerLevel level, BlockPos pos) {
        reactors(level).remove(pos.immutable());
    }

    /**
     * Discovers candidates near a player without forcing unloaded chunks to load.
     * The caller throttles this scan using UPDATE_INTERVAL.
     */
    public static void scanNear(ServerLevel level, BlockPos center, int requestedRadius) {
        int radius = Math.max(1, Math.min(32, requestedRadius));
        int radiusSquared = radius * radius;
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();
        int minY = Math.max(level.getMinBuildHeight(), centerY - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + radius);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            int dx = x - centerX;
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dz = z - centerZ;
                for (int y = minY; y <= maxY; y++) {
                    int dy = y - centerY;
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                        continue;
                    }

                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    BlockPos immutablePos = cursor.immutable();

                    if (isReactorCandidate(id, state) && reactors(level).add(immutablePos)) {
                        LOGGER.debug("Discovered nearby reactor candidate {} at {}", id, immutablePos);
                    }
                    if (isPipeCandidate(id, state) && pipes(level).add(immutablePos)) {
                        LOGGER.debug("Discovered nearby pipe candidate {} at {}", id, immutablePos);
                    }
                }
            }
        }
    }

    public static void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        remove(level, key);

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (isReactorCandidate(id, state)) {
            reactors(level).add(key);
            LOGGER.debug("Cached reactor candidate {} at {}", id, key);
        }
        if (isPipeCandidate(id, state)) {
            pipes(level).add(key);
            LOGGER.debug("Cached pipe candidate {} at {}", id, key);
        }
    }

    private static boolean isReactorCandidate(ResourceLocation id, BlockState state) {
        if (matches(state, BridgeConfig.REACTOR_BLOCK_IDS.get())) {
            return true;
        }
        String namespace = normalize(id.getNamespace());
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return (namespace.contains("goingnuclear")
                && (path.contains("reactor") || path.contains("rbmk") || path.contains("core")))
            || (namespace.equals("csgn") && path.equals("reactor_core"));
    }

    private static boolean isPipeCandidate(ResourceLocation id, BlockState state) {
        if (matches(state, BridgeConfig.PIPE_BLOCK_IDS.get())) {
            return true;
        }
        String namespace = normalize(id.getNamespace());
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return namespace.equals("create")
            && (path.contains("fluid_pipe") || path.contains("fluid_valve")
                || path.contains("smart_fluid_pipe"));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    public static boolean isCachedReactorState(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return isReactorCandidate(id, state);
    }

    public static boolean isCachedPipeState(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return isPipeCandidate(id, state);
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        BlockPos key = pos.immutable();
        reactors(level).remove(key);
        pipes(level).remove(key);
        SteamFluidHelper.invalidate(level, key);
    }

    public static void removeDimension(ResourceLocation dimension) {
        REACTORS.remove(dimension);
        PIPES.remove(dimension);
    }

    public static boolean matches(BlockState state, Iterable<? extends String> configuredIds) {
        ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String rawId : configuredIds) {
            ResourceLocation expected = ResourceLocation.tryParse(rawId);
            if (expected != null && expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }
}
