package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads fluids through NeoForge's FluidHandler capability. Create pipes may be
 * transport-only blocks, so the directly adjacent fluid inventories are inspected too.
 * This class deliberately does not use ItemHandler for fluid: ItemHandler represents
 * item slots, while fluids use Capabilities.FluidHandler.BLOCK.
 */
public final class SteamFluidHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/SteamFluidHelper");
    private static final Map<ResourceLocation, Map<BlockPos, CachedCheck>> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_STEAM_LINES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_FLUID_TYPES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_NO_HANDLER = ConcurrentHashMap.newKeySet();

    private record CachedCheck(long tick, boolean containsSteam) {}
    private record SteamSource(BlockPos pos, Direction side, IFluidHandler handler, FluidStack stack) {}

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

        boolean found = containsSteamInHandler(level, key, null);
        if (!found) {
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = key.relative(direction);
                if (!level.isLoaded(adjacent)) {
                    continue;
                }

                // direction points from pipe toward the adjacent block; query that block
                // from the face directed back toward the pipe.
                if (containsSteamInHandler(level, adjacent, direction.getOpposite())) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            found = inspectConnectedCreateNetwork(level, key);
        }

        dimensionCache.put(key, new CachedCheck(now, found));
        String logKey = dimension + "|" + key;
        if (!found && !hasFluidHandlerNear(level, key) && LOGGED_NO_HANDLER.add(logKey)) {
            LOGGER.info("У трубы {} не найден FluidHandler в самой трубе или соседних блоках. Create fluid-network traversal пока не реализован; проверьте FluidHandler capability у резервуара и точный ID жидкости.", key);
        }
        if (found && (previous == null || !previous.containsSteam()) && LOGGED_STEAM_LINES.add(logKey)) {
            LOGGER.info("Труба найдена, пар есть: pos={}, dimension={}", key, dimension);
        } else {
            LOGGER.debug("Steam check at {} in {}: {}", key, dimension, found);
        }
        if (!found && previous != null && previous.containsSteam()) {
            LOGGED_STEAM_LINES.remove(logKey);
            LOGGER.info("Пар больше не обнаружен у трубы: pos={}, dimension={}", key, dimension);
        }
        return found;
    }

    private static boolean containsSteamInHandler(ServerLevel level, BlockPos pos, Direction side) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null) {
            return false;
        }

        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
            if (fluidId == null) {
                continue;
            }

            boolean steam = isSteam(fluidId, BridgeConfig.STEAM_FLUID_IDS.get());
            String fluidLogKey = fluidId.toString();
            if (LOGGED_FLUID_TYPES.add(fluidLogKey)) {
                LOGGER.info("FluidHandler увидел жидкость: id={}, amount={}, распознана как Going Nuclear steam={}",
                    fluidId, stack.getAmount(), steam);
            } else {
                LOGGER.debug("FluidHandler at {}: fluid={}, amount={}, acceptedAsSteam={}",
                    pos, fluidId, stack.getAmount(), steam);
            }
            if (steam) {
                return true;
            }
        }
        return false;
    }

    /**
     * TODO: Artem's Task - Implement traversal of the real Create Fluid Network after
     * verifying the exact Create version and public API in that version's source/JAR.
     * The safe default is false: do not invent Create class names or guess at network
     * internals. The direct block and adjacent FluidHandler checks above remain active.
     */
    private static boolean inspectConnectedCreateNetwork(ServerLevel level, BlockPos pipePos) {
        return false;
    }

    private static boolean hasFluidHandlerNear(ServerLevel level, BlockPos pipePos) {
        if (level.getCapability(Capabilities.FluidHandler.BLOCK, pipePos, null) != null) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pipePos.relative(direction);
            if (level.isLoaded(adjacent)
                && level.getCapability(Capabilities.FluidHandler.BLOCK, adjacent, direction.getOpposite()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only fluids with explicitly configured IDs, or fluids in the Going Nuclear
     * namespace whose path contains "steam", are accepted. A generic fluid called
     * steam from an unrelated mod does not qualify by default.
     */
    public static boolean isSteam(ResourceLocation fluidId, List<? extends String> configuredIds) {
        if (!configuredIds.isEmpty()) {
            return configuredIds.contains(fluidId.toString());
        }

        String namespace = fluidId.getNamespace().toLowerCase(Locale.ROOT)
            .replace("_", "").replace("-", "");
        String path = fluidId.getPath().toLowerCase(Locale.ROOT);
        return namespace.contains("goingnuclear") && path.contains("steam");
    }

    /**
     * Experimental condensation path for explicitly configured output blocks.
     *
     * A configured output must be adjacent to the detected steam pipe. Water is placed
     * above the output block. Fluid is simulated first and only exactly 1000 mB can
     * become one water source block. If actual drain or placement fails, fluid is
     * returned to the handler where possible. This is intentionally opt-in because a
     * blind drain would steal steam from Create turbines or the built-in Going Nuclear
     * radiator.
     */
    public static boolean tryCondenseAtConfiguredOutput(ServerLevel level, BlockPos pipePos) {
        if (!BridgeConfig.ENABLE_CONDENSATION.get()
            || BridgeConfig.CONDENSATION_OUTPUT_BLOCK_IDS.get().isEmpty()
            || !level.isLoaded(pipePos)
            || !containsConfiguredSteam(level, pipePos)) {
            return false;
        }

        BlockPos outputPos = null;
        for (Direction direction : Direction.values()) {
            BlockPos candidate = pipePos.relative(direction);
            if (!level.isLoaded(candidate)) {
                continue;
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(candidate).getBlock()).toString();
            if (BridgeConfig.CONDENSATION_OUTPUT_BLOCK_IDS.get().contains(blockId)) {
                outputPos = candidate;
                break;
            }
        }

        if (outputPos == null) {
            return false;
        }

        // TODO: Artem's Task - Verify the real radiator/vent outlet direction from the
        // installed Going Nuclear source. Until then, the configured output is above
        // the marked block, and condensation remains disabled unless explicitly enabled.
        BlockPos waterPos = outputPos.above();
        if (!level.isLoaded(waterPos)
            || !level.getBlockState(waterPos).canBeReplaced()
            || !level.getFluidState(waterPos).isEmpty()) {
            // Do not replace existing water/other fluid and do not drain repeatedly into
            // a cell that already contains a fluid source or flowing fluid.
            return false;
        }

        SteamSource source = findAccessibleSteamSource(level, pipePos);
        if (source == null) {
            LOGGER.warn("Condensation outlet {} found, but no adjacent FluidHandler exposing Going Nuclear steam was found. Full Create network traversal is not implemented.", outputPos);
            return false;
        }

        int amount = BridgeConfig.CONDENSATION_AMOUNT.get();
        FluidStack request = new FluidStack(source.stack().getFluid(), amount);
        FluidStack simulated = source.handler().drain(request, IFluidHandler.FluidAction.SIMULATE);
        ResourceLocation simulatedFluidId = simulated.isEmpty()
            ? null : BuiltInRegistries.FLUID.getKey(simulated.getFluid());
        if (simulated.getAmount() < amount || simulatedFluidId == null
            || !isSteam(simulatedFluidId, BridgeConfig.STEAM_FLUID_IDS.get())) {
            LOGGER.debug("Condensation skipped at {}: simulated drain was only {} mB", outputPos, simulated.getAmount());
            return false;
        }

        FluidStack drained = source.handler().drain(request, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() < amount) {
            if (!drained.isEmpty()) {
                source.handler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
            }
            LOGGER.warn("Condensation aborted at {}: execute drain returned {} mB, expected {}", outputPos, drained.getAmount(), amount);
            return false;
        }

        if (level.getBlockState(waterPos).canBeReplaced()
            && level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3)) {
            LOGGER.info("Пар сконденсирован в воду: source={}, output={}, waterPos={}, drained={} mB",
                source.pos(), outputPos, waterPos, drained.getAmount());
            invalidate(level, pipePos);
            return true;
        }

        // Roll back the drain if water placement cannot be completed.
        int returned = source.handler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
        LOGGER.warn("Не удалось разместить воду в {}. Возврат жидкости в источник: {} из {} mB",
            waterPos, returned, drained.getAmount());
        return false;
    }

    private static SteamSource findAccessibleSteamSource(ServerLevel level, BlockPos pipePos) {
        SteamSource source = findSourceInBlock(level, pipePos, null);
        if (source != null) {
            return source;
        }

        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pipePos.relative(direction);
            if (!level.isLoaded(adjacent)) {
                continue;
            }
            source = findSourceInBlock(level, adjacent, direction.getOpposite());
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private static SteamSource findSourceInBlock(ServerLevel level, BlockPos pos, Direction side) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null) {
            return null;
        }

        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.getAmount() >= BridgeConfig.CONDENSATION_AMOUNT.get()) {
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
                if (id != null && isSteam(id, BridgeConfig.STEAM_FLUID_IDS.get())) {
                    return new SteamSource(pos.immutable(), side, handler, stack.copy());
                }
            }
        }
        return null;
    }

    /** Clear cached steam results after the live config is reloaded. */
    public static void clearCache() {
        CACHE.clear();
        LOGGED_STEAM_LINES.clear();
        LOGGED_FLUID_TYPES.clear();
        LOGGED_NO_HANDLER.clear();
    }

    public static void invalidate(ServerLevel level, BlockPos pos) {
        Map<BlockPos, CachedCheck> dimensionCache = CACHE.get(level.dimension().location());
        if (dimensionCache != null) {
            dimensionCache.remove(pos.immutable());
        }
    }

    public static void clearDimension(ResourceLocation dimension) {
        CACHE.remove(dimension);
        String prefix = dimension + "|";
        LOGGED_STEAM_LINES.removeIf(key -> key.startsWith(prefix));
        LOGGED_NO_HANDLER.removeIf(key -> key.startsWith(prefix));
    }
}
