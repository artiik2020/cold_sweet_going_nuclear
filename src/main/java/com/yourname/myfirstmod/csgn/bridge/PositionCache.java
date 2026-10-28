package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven cache. It does not scan loaded chunks every tick.
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
        String namespace = id.getNamespace().toLowerCase(java.util.Locale.ROOT);
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        return (namespace.contains("goingnuclear")
                && (path.contains("reactor") || path.contains("rbmk") || path.contains("core")))
            || (namespace.equals("csgn") && path.equals("reactor_core"));
    }

    private static boolean isPipeCandidate(ResourceLocation id, BlockState state) {
        if (matches(state, BridgeConfig.PIPE_BLOCK_IDS.get())) {
            return true;
        }
        String namespace = id.getNamespace().toLowerCase(java.util.Locale.ROOT);
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        return namespace.equals("create")
            && (path.contains("fluid_pipe") || path.equals("fluid_pipe"));
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
