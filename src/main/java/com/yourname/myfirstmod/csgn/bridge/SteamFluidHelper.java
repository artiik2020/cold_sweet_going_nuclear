package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads fluids exposed through NeoForge's FluidHandler capability.
 * Create fluid pipes are transport blocks, not necessarily fluid inventories,
 * so adjacent tanks/machines are checked as well.
 */
public final class SteamFluidHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/SteamFluidHelper");
    private static final Map<ResourceLocation, Map<BlockPos, CachedCheck>> CACHE = new ConcurrentHashMap<>();

    private record CachedCheck(long tick, boolean containsSteam) {}

    private SteamFluidHelper() {}

    public static boolean containsConfiguredSteam(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        ResourceLocation dimension = level.dimension().location();
        Map<BlockPos, CachedCheck> dimensionCache =
            CACHE.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>());
        BlockPos key = pos.immutable();
        long now = level.getGameTime();
        CachedCheck previous = dimensionCache.get(key);
        int interval = Math.max(1, BridgeConfig.UPDATE_INTERVAL.get());

        if (previous != null && now - previous.tick() < interval) {
            return previous.containsSteam();
        }

        boolean found = inspectBlock(level, key, null);
        if (!found) {
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = key.relative(direction);
                if (!level.isLoaded(adjacent)) {
                    continue;
                }

                // Query the adjacent inventory from the face looking toward the pipe.
                if (inspectBlock(level, adjacent, direction.getOpposite())) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            found = inspectConnectedCreateNetwork(level, key);
        }

        dimensionCache.put(key, new CachedCheck(now, found));
        LOGGER.debug("Steam check at {} in {}: {}", key, dimension, found);
        return found;
    }

    private static boolean inspectBlock(ServerLevel level, BlockPos pos, Direction side) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null) {
            return false;
        }

        List<? extends String> configured = BridgeConfig.STEAM_FLUID_IDS.get();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
            if (fluidId == null) {
                continue;
            }

            boolean steam = isSteam(fluidId, configured);
            LOGGER.debug("Fluid at {}: {} amount={} steamMatch={}",
                pos, fluidId, stack.getAmount(), steam);
            if (steam) {
                return true;
            }
        }
        return false;
    }

    /**
     * TODO: Artem's Task - Implement Create fluid-network traversal after verifying
     * the installed Create API and its connected-network semantics.
     *
     * Safe default: return false. Nearby FluidHandler inventories are still checked
     * above, so this unfinished extension does not prevent the bridge from starting.
     */
    private static boolean inspectConnectedCreateNetwork(ServerLevel level, BlockPos pipe) {
        return false;
    }

    private static boolean isSteam(ResourceLocation id, List<? extends String> configured) {
        return configured.contains(id.toString())
            || (configured.isEmpty() && id.getPath().toLowerCase(Locale.ROOT).contains("steam"));
    }

    public static void invalidate(ServerLevel level, BlockPos pos) {
        Map<BlockPos, CachedCheck> dimensionCache = CACHE.get(level.dimension().location());
        if (dimensionCache != null) {
            dimensionCache.remove(pos.immutable());
        }
    }

    public static void clearDimension(ResourceLocation dimension) {
        CACHE.remove(dimension);
    }

    /**
     * Draining is deliberately separate from detection. This method is not called
     * by the heat handler and will not consume fluid as a side effect of checking.
     */
    public static FluidStack drainConfiguredSteam(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0 || !level.isLoaded(pos)) {
            return FluidStack.EMPTY;
        }

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) {
            return FluidStack.EMPTY;
        }

        List<? extends String> configured = BridgeConfig.STEAM_FLUID_IDS.get();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack present = handler.getFluidInTank(tank);
            if (present.isEmpty()) {
                continue;
            }

            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(present.getFluid());
            if (fluidId == null || !isSteam(fluidId, configured)) {
                continue;
            }

            FluidStack request = new FluidStack(present.getFluid(), Math.min(amount, present.getAmount()));
            FluidStack simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return handler.drain(simulated, IFluidHandler.FluidAction.EXECUTE);
        }

        return FluidStack.EMPTY;
    }
}
