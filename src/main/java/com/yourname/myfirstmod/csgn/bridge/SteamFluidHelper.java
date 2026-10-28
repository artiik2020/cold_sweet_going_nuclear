package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
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

public final class SteamFluidHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/SteamFluidHelper");
    private SteamFluidHelper() {}

    public static boolean containsConfiguredSteam(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) {
            LOGGER.debug("No block FluidHandler capability at {}", pos);
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

            boolean steam = configured.contains(fluidId.toString())
                || (configured.isEmpty() && fluidId.getPath().toLowerCase(Locale.ROOT).contains("steam"));
            LOGGER.debug("Fluid at {}: {} amount={} steamMatch={}",
                pos, fluidId, stack.getAmount(), steam);

            if (steam) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draining is intentionally explicit: this helper never drains a pipe automatically.
     * The returned stack is still steam; conversion to water and insertion into a destination
     * must be handled by a radiator/condensation block implementation.
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

            FluidStack request = new FluidStack(present.getFluid(),
                Math.min(amount, present.getAmount()));
            FluidStack simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return handler.drain(simulated, IFluidHandler.FluidAction.EXECUTE);
        }

        return FluidStack.EMPTY;
    }

    private static boolean isSteam(ResourceLocation id, List<? extends String> configured) {
        return configured.contains(id.toString())
            || (configured.isEmpty() && id.getPath().toLowerCase(Locale.ROOT).contains("steam"));
    }
}
