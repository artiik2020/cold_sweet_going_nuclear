package com.yourname.myfirstmod.csgn.integration;

import com.yourname.myfirstmod.csgn.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jspecify.annotations.Nullable;

/**
 * Помощник по работе с паром через IFluidHandler (Capability FluidHandler BLOCK).
 * ИСПРАВЛЕНИЯ Fluid API 1.21.1:
 *  - У FluidType НЕТ getRegistryName() — ID жидкости получаем через
 *    BuiltInRegistries.FLUID.getKey(stack.getFluid());
 *  - Конструктор FluidStack принимает Holder&lt;Fluid&gt; или Fluid, НО НЕ FluidType:
 *    new FluidStack(stack.getFluidHolder(), amount) / new FluidStack(fluid, amount).
 */
public final class SteamFluidHelper {

    private static final int MIN_STEAM_MB = 1;

    /** Есть ли в обработчике жидкость из whitelist «пара»? */
    public static boolean containsSteam(@Nullable IFluidHandler handler) {
        if (handler == null) return false;
        try {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (stack.isEmpty() || stack.getAmount() < MIN_STEAM_MB) continue;
                // ИСПРАВЛЕНО: registry name жидкости через BuiltInRegistries, а не FluidType
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
                if (Config.isSteamFluid(fluidId)) return true;
            }
        } catch (RuntimeException ignored) {
            // чужой handler мог кинуть исключение — считаем, что пара нет
        }
        return false;
    }

    /**
     * Пытается вытянуть до amountMb пара из первого подходящего танка.
     * Сначала SIMULATE (не меняем мир), при execute=true — EXECUTE.
     */
    public static FluidStack drainSteam(@Nullable IFluidHandler handler, int amountMb, boolean execute) {
        if (handler == null) return FluidStack.EMPTY;
        try {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack content = handler.getFluidInTank(tank);
                if (content.isEmpty()) continue;

                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(content.getFluid());
                if (!Config.isSteamFluid(fluidId)) continue;

                // ИСПРАВЛЕНО: FluidStack конструируется от Fluid (или Holder<Fluid>), НЕ от FluidType
                FluidStack request = new FluidStack(content.getFluidHolder(), amountMb);

                FluidStack simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
                if (simulated.isEmpty()) continue;

                if (execute) {
                    return handler.drain(
                            new FluidStack(simulated.getFluidHolder(), simulated.getAmount()),
                            IFluidHandler.FluidAction.EXECUTE);
                }
                return simulated;
            }
        } catch (RuntimeException ignored) {
        }
        return FluidStack.EMPTY;
    }

    /** Registry ID жидкости стека (для логов/сравнений). */
    public static ResourceLocation fluidId(FluidStack stack) {
        return BuiltInRegistries.FLUID.getKey(stack.getFluid());
    }

    private SteamFluidHelper() {}
}
