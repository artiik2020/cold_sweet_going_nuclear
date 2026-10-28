package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.integration.ColdSweatBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * «Сердце» бриджа: применяет накопленное тепло зон к LivingEntity через ColdSweatBridge.
 * ИСПРАВЛЕНИЯ 1.21.1:
 *  - НЕТ аннотации @EventBusSubscriber — класс регистрируется вручную в конструкторе Csgn;
 *  - нет скана чанков и глобального AABB — перебираются только сущности уровня,
 *    а тепло считается по кэшу зон (HeatZoneManager);
 *  - таймер на level.getGameTime();
 *  - level.getEntities() без параметров возвращает LevelEntityGetter<Entity>,
 *    который НЕ является Iterable/array — для for-each не годится. Используем
 *    перегрузку ServerLevel.getEntities(EntityTypeTest, List) — она наполняет
 *    переданный список живыми сущностями и возвращает его же.
 */
public final class HeatTickHandler {

    /** Сколько °C/сек даёт 1 единица «накала» зоны. */
    private static final double DEG_PER_HEAT_PER_SECOND = 0.5;

    /** Фильтр сущностей: пропускаем только LivingEntity (игроки, мобы, животные). */
    private static final EntityTypeTest<Entity, LivingEntity> LIVING_FILTER =
            new EntityTypeTest<>() {
                @Override
                public LivingEntity tryCast(Entity entity) {
                    return entity instanceof LivingEntity living ? living : null;
                }

                @Override
                public EntityType<?> getBaseType() {
                    // Базовый тип не важен: tryCast фильтрует всех живых существ.
                    return EntityType.PLAYER;
                }
            };

    @SubscribeEvent
    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null || !zones.hasAnySource()) return;

        // ИСПРАВЛЕНО: gameTime -> level.getGameTime()
        long gameTime = level.getGameTime();
        if (gameTime % Config.heatApplyIntervalTicks != 0) return;

        double secondsPerApply = Config.heatApplyIntervalTicks / 20.0;

        // Собираем всех живых сущностей уровня в список (перегрузка getEntities(test, list))
        List<LivingEntity> targets = new ArrayList<>();
        level.getEntities(LIVING_FILTER, targets);

        for (LivingEntity living : targets) {
            double heat = zones.heatAt(living.blockPosition());
            if (heat <= 0.0) continue;

            double deltaC = heat * DEG_PER_HEAT_PER_SECOND * secondsPerApply;
            ColdSweatBridge.addTemperature(living, deltaC);
        }
    }
}
