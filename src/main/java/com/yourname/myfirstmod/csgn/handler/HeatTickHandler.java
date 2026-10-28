package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.integration.ColdSweatBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * «Сердце» бриджа: применяет накопленное тепло зон к LivingEntity через ColdSweatBridge.
 * ИСПРАВЛЕНИЯ 1.21.1:
 *  - НЕТ аннотации @EventBusSubscriber — класс регистрируется вручную в конструкторе Csgn;
 *  - нет скана чанков и глобального AABB — перебираются только сущности уровня,
 *    а тепло считается по кэшу зон (HeatZoneManager);
 *  - таймер на level.getGameTime();
 *  - level.getEntities() БЕЗ параметров возвращает LevelEntityGetter<Entity>,
 *    который НЕ является Iterable — для for-each не годится. Правильный способ
 *    получить ВСЕХ сущностей серверного уровня одной коллекцией —
 *    level.getEntities().getAll() (вернёт java.lang.Iterable<Entity>).
 *  - Никаких self-made EntityTypeTest-классов: EntityTypeTest ванильного MC —
 *    это interface с static-фабриками (forType/forClass), его нельзя создать
 *    как "new EntityTypeTest<>() {}" c методом getBaseType() — такого метода нет.
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
        int interval = Math.max(1, Config.heatApplyIntervalTicks); // защита от %0
        if (gameTime % interval != 0) return;

        double secondsPerApply = interval / 20.0;

        // getAll() — единственный Iterable-метод LevelEntityGetter в 1.21.1
        for (Entity entity : level.getEntities().getAll()) {
            if (!(entity instanceof LivingEntity living)) continue;

            double heat = zones.heatAt(living.blockPosition());
            if (heat <= 0.0) continue;

            double deltaC = heat * DEG_PER_HEAT_PER_SECOND * secondsPerApply;
            ColdSweatBridge.addTemperature(living, deltaC);

            // =================================================================================
            // TODO: Artem's Optional Task - [Ожог от горячих труб/реакторов] (задача #4 в SAM.md)
            // Раскомментируй строку ниже и реализуй метод applyBurnChance(...) в конце класса.
            // Код ЗАКОММЕНТИРОВАН намеренно: мод компилируется и работает как раньше,
            // пока ты сам не включишь фичу. Дефолт конфига hotPipeBurnChance = 0.0 — то есть
            // даже после раскомментирования ничего не произойдёт, пока не поднимешь шанс в TOML.
            // applyBurnChance(living, heat);
            // =================================================================================
        }
    }

    /**
     * Заглушка для опциональной задачи #4 (см. SAM.md).
     * Пустой метод-скелет: с ним проект КОМПИЛИРУЕТСЯ, но ничего не делает.
     * Твоя задача — наполнить тело (подсказки внутри).
     */
    private void applyBurnChance(LivingEntity entity, double heat) {
        // TODO: Artem's Optional Task - [Ожог от горячих труб/реакторов] — наполни это тело.
        //
        // Логика по шагам:
        //   1) if (Config.hotPipeBurnChance <= 0.0) return;          // фича выключена — выходим
        //   2) брось монетку: RandomSource random = entity.level().random;
        //      if (random.nextDouble() >= Config.hotPipeBurnChance) return;
        //   3) нанеси полсердца урона:
        //      entity.hurt(entity.level().damageSources().generic(), 1.0f);
        //   4) (бонус) подсвети ожог частицами: см. задачу #1 про ParticleTypes.FLAME.
        //
        // ПОДВОДНЫЕ КАМНИ JAVA:
        //  - entity.level() возвращает Level (общий класс), а damageSources()/hurt() есть у него —
        //    но убедись, что ты НЕ на клиенте (этот хендлер уже серверный, всё ок);
        //  - nextDouble() даёт [0.0, 1.0): сравнивай аккуратно (>= против >);
        //  - hurt(EntityDamageSource, float) — второй параметр float, пиши ровно 1.0f, а не 1.0!
    }
}
