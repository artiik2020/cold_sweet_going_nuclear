package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * Capability-based fluid inspection. Draining is explicit and never happens
 * merely because a pipe contains fluid.
 */
public final class SteamFluidHelper {
    private SteamFluidHelper() {}

    public static boolean containsConfiguredSteam(ServerLevel level, BlockPos pos) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) {
            return false;
        }

        List<? extends String> configured = BridgeConfig.STEAM_FLUID_IDS.get();
        if (configured.isEmpty()) {
            return false;
        }

        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
            if (fluidId != null && configured.contains(fluidId.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drains only when an explicit condensation action requests it.
     * Caller must verify that the returned fluid can be converted to water
     * and inserted into an appropriate destination.
     */
    public static FluidStack drainConfiguredSteam(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0) {
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
            if (fluidId != null && configured.contains(fluidId.toString())) {
                FluidStack drained = handler.drain(
                    new FluidStack(present.getFluid(), Math.min(amount, present.getAmount())),
                    IFluidHandler.FluidAction.SIMULATE
                );
                if (drained.isEmpty()) {
                    return FluidStack.EMPTY;
                }
                return handler.drain(drained, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        return FluidStack.EMPTY;
    }
}
