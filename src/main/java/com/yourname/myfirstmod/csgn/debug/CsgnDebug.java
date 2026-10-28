package com.yourname.myfirstmod.csgn.debug;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.handler.HeatZoneManager;
import com.yourname.myfirstmod.csgn.integration.ColdSweatBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * ОТЛАДОЧНЫЙ слушатель: клик ПУСТОЙ рукой по любому блоку на сервере печатает в чат
 * диагностику всей цепочки бриджа: зоны в кэше -> тепло в точке игрока -> температура Cold Sweat.
 *
 * Это позволяет за 5 секунд понять, ГДЕ именно обрывается цепочка:
 *   reactors=0        -> реактор не попал в whitelist конфига (правь csgn-common.toml!)
 *   pipes=0           -> труба не увидела пар (неверный steamFluidIds или нет FluidHandler)
 *   heatHere=0.0      -> зоны есть, но ты вне радиуса / множители в конфиге = 0
 *   CS temp=N/A(API)  -> Cold Sweat API не резолбнулся (смотри лог [CSGN])
 */
public final class CsgnDebug {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // только если рука пуста — иначе мешаем ломать блоки
        if (!event.getItemStack().isEmpty()) return; // LeftClickBlock всегда возвращает ItemStack.EMPTY

        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null) {
            player.displayClientMessage(Component.literal("[CSGN] HeatZoneManager = null (attachment не создан?)")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        double heatHere = zones.heatAt(player.blockPosition());
        double temp = ColdSweatBridge.getTemperature(player);

        String msg = String.format(
                "[CSGN] reactors=%d pipes=%d | heatHere=%.3f | radiusR=%d radiusP=%d multR=%.2f multP=%.2f | CS temp=%s",
                zones.reactorCount(), zones.pipeCount(), heatHere,
                Config.reactorHeatRadius, Config.steamPipeHeatRadius,
                Config.reactorHeatMultiplier, Config.steamPipeHeatMultiplier,
                Double.isNaN(temp) ? "N/A(API)" : String.format("%.2f°C", temp));

        player.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW), false);
        LOGGER.info("[CSGN][debug] {}", msg);

        // TODO: Artem's Optional Task - [Команда /csgn heat вместо клика по блоку] (задача #6 в SAM.md)
        // Клик по блоку неудобен, если не хочешь ломать чужие механизмы. Можно зарегистрировать
        // настоящую команду: /csgn heat — и печатать ровно этот же текст оттуда.
        // Где копирать: net.neoforged.neoforge.event.RegisterCommandsEvent + Commands.registerCommand().
    }
}
