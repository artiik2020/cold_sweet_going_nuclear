package com.yourname.myfirstmod.csgn.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Reflection-мост к РЕАЛЬНОМУ API Cold Sweat (mrgremalbaum, modid "cold_sweat", v2.8+).
 *
 * ПОЧЕМУ ПРОШЛАЯ ВЕРСИЯ НЕ РАБОТАЛА (главная причина «нет нагрева»):
 *  - мы искали метод addChange(LivingEntity, double) — в реальном API такой сигнатуры НЕТ;
 *  - реальный метод: Temperature.addChange(LivingEntity, double, Temperature.Source);
 *  - имена пакетов были выдуманы (robbitstudios/mrcrayfish);
 *  - поиск шел по getMethods() без проверки параметров -> метод никогда не находился,
 *    и нагрев молча не применялся (только warn в логе, который легко пропустить).
 *
 * ТЕПЕРЬ: ищем методы ЧЕРЕЗ getMethod(имя, конкретные классы параметров) — если сигнатура
 * не совпала, ловим NoSuchMethodException и пишем ПОНЯТНЫЙ лог, а не молчим.
 */
public final class ColdSweatBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** РЕАЛЬНЫЙ modid Cold Sweat v2.8+ (mrgremalbaum) — "cold_sweat" С ПОДЧЁРКИВАНИЕМ!
     *  Старый код проверял "coldsweat" -> мод «не находился», и нагрев молча не применялся. */
    public static final String MODID = "cold_sweat";

    public static final boolean COLD_SWEAT_LOADED = ModList.get().isLoaded(MODID);

    /** Полные имена класса Temperature у разных изданий Cold Sweat. */
    private static final String[] TEMPERATURE_CLASS_CANDIDATES = {
            "com.mrgremalbaum.coldsweat.api.util.Temperature",   // основной Modern Benefit порт 1.21.1
            "com.mrgremalbaum.coldsweat.util.Temperature",           // вариант без .api
            "mrgremalbaum.coldsweat.api.util.Temperature",           // вариант без com.
            "com.coldsweat.api.util.Temperature",                // фолбэк для repack'нутых сборок
    };

    private static Object sourceFire;           // константа Temperature.Source.Fire
    private static Method methodAddChange;      // addChange(LivingEntity, double, Source)
    private static Method methodGet;            // get(LivingEntity) -> double
    private static boolean resolvedAttempted = false;

    private static synchronized void resolveIfNeeded() {
        if (resolvedAttempted || !COLD_SWEAT_LOADED) return;
        resolvedAttempted = true;

        for (String className : TEMPERATURE_CLASS_CANDIDATES) {
            try {
                Class<?> tempClass = Class.forName(className);

                // Сигнатуры проверяются ЯВНО через getMethod(...): не совпало — NoSuchMethodException и лог.
                Method addChange = tempClass.getMethod(
                        "addChange", LivingEntity.class, double.class, Class.forName(className + "$Source"));
                Method get = tempClass.getMethod("get", LivingEntity.class);

                // Enum Temperature.Source -> берём Fire (огонь/тепло), иначе первое значение.
                Class<?> sourceClass = Class.forName(className + "$Source");
                Object[] constants = sourceClass.getEnumConstants();
                if (constants == null || constants.length == 0) continue;
                Object fire = null;
                for (Object c : constants) {
                    if (((Enum<?>) c).name().equalsIgnoreCase("FIRE")) { fire = c; break; }
                }
                if (fire == null) fire = constants[0];

                sourceFire = fire;
                methodAddChange = addChange;
                methodGet = get;

                LOGGER.info("[CSGN] Cold Sweat API резолблен: {}#addChange(entity,double,{})",
                        className, sourceClass.getSimpleName());
                return;
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[CSGN] класс Cold Sweat '{}' не найден — пробуем следующий", className);
            } catch (NoSuchMethodException e) {
                LOGGER.warn("[CSGN] Класс '{}' найден, но сигнатура addChange/get НЕ совпала! " +
                        "Открой jar Cold Sweat и сверь API.", className);
            } catch (Throwable t) {
                LOGGER.warn("[CSGN] Ошибка резолва Cold Sweat API '{}': {}", className, t.toString());
            }
        }
        LOGGER.error("[CSGN] НЕ УДАЛОСЬ найти API Cold Sweat ни по одному кандидату — нагрев применяться НЕ БУДЕТ.");
    }

    /** Добавить сущности delta °C. Вызывать ТОЛЬКО из серверного потока! */
    public static void addTemperature(LivingEntity entity, double deltaCelsius) {
        if (deltaCelsius == 0.0 || !COLD_SWEAT_LOADED) return;
        resolveIfNeeded();
        if (methodAddChange == null) return;
        try {
            methodAddChange.invoke(null, entity, deltaCelsius, sourceFire);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[CSGN] Reflection-вызов Temperature.addChange упал", e);
        }
    }

    /** Текущая температура тела сущности из Cold Sweat; NaN если API недоступен. */
    public static double getTemperature(LivingEntity entity) {
        if (!COLD_SWEAT_LOADED) return Double.NaN;
        resolveIfNeeded();
        if (methodGet == null) return Double.NaN;
        try {
            Object result = methodGet.invoke(null, entity);
            return (result instanceof Number n) ? n.doubleValue() : Double.NaN;
        } catch (ReflectiveOperationException e) {
            return Double.NaN;
        }
    }

    private ColdSweatBridge() {}
}
