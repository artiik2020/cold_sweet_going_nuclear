package com.yourname.myfirstmod.csgn.integration;

import com.yourname.myfirstmod.csgn.Config;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jspecify.annotations.Nullable;

/**
 * Работа с Create-трубами ЧИСТО через стандартную NeoForge Capability
 * (net.neoforged.neoforge.capabilities.BlockCapability + IFluidHandler) —
 * без импорта классов Create. Create сам экспортирует FluidHandler.BLOCK
 * для своих труб/танков/котлов, поэтому нам хватает интерфейса.
 */
public final class SteamFluidHelper {

    /** Сколько mB пара должно быть во «вторичном» баке трубы, чтобы считать её «горячей». */
    private static final int MIN_STEAM_MB = 1;

    /**
     * Есть ли в обработчике жидкость-пар (create:steam по whitelist конфига)?
     * @param handler capability трубы, может быть null — обрабатываем безопасно
     */
    public static boolean containsSteam(@Nullable IFluidHandler handler) {
        if (handler == null) return false;
        try {
            // У Fluid Pipe Create бак обычно двухслотовый: PRIMARY (содержимое сети)
            // и SECONDARY (то, что видно снаружи). Проверяем оба.
            for (int tank = 0; tank < Math.min(handler.getTanks(), 2); tank++) {
                var stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty()
                        && stack.getAmount() >= MIN_STEAM_MB
                        && Config.isSteamFluid(stack.getFluidType().getRegistryName())) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            // Страховка: чужой handler повёлся странно — не крашим тик мира.
            return false;
        }
        return false;
    }

    /**
     * Пытается вытянуть (drain) до amountMb пара из трубы.
     * @return реально извлечённый FluidStack или EMPTY, если пара нет/не дали.
     */
    public static net.neoforged.neoforge.fluids.FluidStack drainSteam(
            @Nullable IFluidHandler handler, int amountMb, boolean execute) {
        if (handler == null) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        try {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                var content = handler.getFluidInTank(tank);
                if (content.isEmpty()) continue;
                FluidType type = content.getFluidType();
                var id = type.getRegistryName();
                if (id == null || !Config.isSteamFluid(id)) continue;

                // Сначала симуляция (execute=false) — узнаём, сколько реально отдадут:
                var simulated = handler.drain(new net.neoforged.neoforge.fluids.FluidStack(type, amountMb),
                        IFluidHandler.FluidAction.SIMULATE);
                if (simulated.isEmpty()) continue;

                if (execute) {
                    return handler.drain(new net.neoforged.neoforge.fluids.FluidStack(type, simulated.getAmount()),
                            IFluidHandler.FluidAction.EXECUTE);
                }
                return simulated;
            }
        } catch (RuntimeException ignored) {
            // безопасный отказ
        }
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    private SteamFluidHelper() {}
}
