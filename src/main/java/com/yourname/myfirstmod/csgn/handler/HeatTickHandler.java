package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.integration.ColdSweatBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * «Сердце» бриджа: применяет накопленное тепло зонам к LivingEntity через ColdSweatBridge.
 * ИСПРАВЛЕНИЯ 1.21.1:
 *  - НЕТ аннотации @EventBusSubscriber — класс регистрируется вручную в конструкторе Csgn;
 *  - нет скана чанков и глобального AABB — перебираются только сущности уровня,
 *    а тепло считается по кэшу зон (HeatZoneManager);
 *  - таймер на level.getGameTime().
 */
public final class HeatTickHandler {

    /** Сколько °C/сек даёт 1 единица «накала» зоны. */
    private static final double DEG_PER_HEAT_PER_SECOND = 0.5;

    @SubscribeEvent
    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null || !zones.hasAnySource()) return;

        // ИСПРАВЛЕНО: gameTime -> level.getGameTime()
        long gameTime = level.getGameTime();
        if (gameTime % Config.heatApplyIntervalTicks != 0) return;

        double secondsPerApply = Config.heatApplyIntervalTicks / 20.0;

        for (Entity entity : level.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;

            double heat = zones.heatAt(living.blockPosition());
            if (heat <= 0.0) continue;

            double deltaC = heat * DEG_PER_HEAT_PER_SECOND * secondsPerApply;
            ColdSweatBridge.addTemperature(living, deltaC);
        }
    }
}
