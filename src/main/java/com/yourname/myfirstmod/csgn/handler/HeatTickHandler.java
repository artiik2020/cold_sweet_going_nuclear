package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.integration.ColdSweatBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * «Сердце» бриджа: каждые Config.heatApplyIntervalTicks тиков проходит по всем
 * уровням (измерениям) сервера и передаёт накопленное тепло в Cold Sweat.
 *
 * Оптимизации (почему это не убьёт TPS):
 *  1) Ранний выход: если у уровня нет источников тепла — ничего не считаем;
 *  2) Интервал: применяем раз в N тиков, а не каждый тик;
 *  3) heatAt() сравнивает квадраты расстояний (без Math.sqrt до момента попадания в зону);
 *  4)delta делится на интервал, чтобы «скорость нагрева» °C/сек не зависела от конфига.
 */
@EventBusSubscriber(modid = "csgn") // GAME-шина по умолчанию; класс со статическими @SubscribeEvent
public final class HeatTickHandler {

    /** Сколько градусов добавляем за 1 секунду на 1 единицу «накала» зоны. Подкрути под себя! */
    private static final double DEG_PER_HEAT_PER_SECOND = 0.5;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % Config.heatApplyIntervalTicks != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            HeatZoneManager zones = HeatZoneManager.get(level);
            if (zones == null || !zones.hasAnySource()) continue; // ранний выход — самый частый случай

            double secondsPerApply = Config.heatApplyIntervalTicks / 20.0;

            // Игроки + мобы (LivingEntity). Для мега-серверов можно ограничить
            // перебор чанками вокруг игроков — см. TODO ниже.
            for (Entity entity : level.getEntities((Entity) null,
                    new net.minecraft.world.phys.AABB(level.getMinBuildHeight(), -64, level.getMinBuildHeight(),
                            level.getMaxBuildHeight() + 64, level.getMaxBuildHeight() + 64),
                    e -> e instanceof LivingEntity)) {
                // getEntities с таким AABB может быть дорого на огромных мирах:
                // =================================================================================
                // TODO: Artem's Task — опциональная оптимизация: вместо глобального поиска
                // перебирай только level.players() + nearby mobs через
                // level.getNearbyEntities(type, player, aabb). Включай, когда mob'ы не нужны.
                // =================================================================================
                double heat = zones.heatAt(entity.blockPosition());
                if (heat <= 0.0) continue;

                double deltaC = heat * DEG_PER_HEAT_PER_SECOND * secondsPerApply;
                ColdSweatBridge.addTemperature((LivingEntity) entity, deltaC);
            }
        }
    }

    private HeatTickHandler() {}
}
