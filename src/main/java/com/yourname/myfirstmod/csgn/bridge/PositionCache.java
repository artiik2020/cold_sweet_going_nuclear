package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Position cache populated by block-place/break events and optional targeted scans.
 * No loaded-chunk-wide scans are performed here.
 */
public final class PositionCache {
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

    public static void onChunkLoad(LevelEvent.Load event) {
        // TODO: Artem's Task
        // If persistent discovery is needed, inspect only positions known to this mod.
        // Do not scan every block in a chunk or every loaded chunk on every tick.
    }

    public static void registerReactor(ServerLevel level, BlockPos pos) {
        reactors(level).add(pos.immutable());
    }

    public static void unregisterReactor(ServerLevel level, BlockPos pos) {
        reactors(level).remove(pos.immutable());
    }

    public static void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        remove(level, key);
        if (matches(state, BridgeConfig.REACTOR_BLOCK_IDS.get())) {
            reactors(level).add(key);
        }
        if (matches(state, BridgeConfig.PIPE_BLOCK_IDS.get())) {
            pipes(level).add(key);
        }
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
