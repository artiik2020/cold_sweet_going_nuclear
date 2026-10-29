package com.yourname.myfirstmod.csgn.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * МЕХАНИКА №2: Reflection-мост к ВНУТРЕННЕМУ состоянию реакторов Going Nuclear.
 *
 * ЧТО МЫ ИЩЕМ: у реакторов Going Nuclear (modid "goingnuclear") BlockEntity хранит
 * внутреннюю температуру/активность. Мы НЕ знаем точных имён полей приватного BE,
 * поэтому используем «универсальный» способ — перебор ПУБЛИЧНЫХ методов get*-параметров:
 *   - метод без аргументов, имя содержит "temp"/"heat"/"energy", возврат double/float/int;
 *   - вызываем его на серверном потоке и нормализуем результат в «нагрев» 0..1.
 * Если мода нет / метод не нашёлся — возвращаем -1, и HeatZoneManager использует
 * статический множитель из конфига (старое поведение). НИКАКИХ крашей, всё безопасно.
 */
public final class GoingNuclearBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** РЕАЛЬНЫЙ modid Going Nuclear. Проверяется так же, как Cold Sweat: ModList.get().isLoaded(...). */
    public static final String MODID = "goingnuclear";

    public static final boolean GOING_NUCLEAR_LOADED = ModList.get().isLoaded(MODID);

    /** Кэш: класс BE -> найденный getter. Если для класса ничего не нашли — помечаем Optional.empty-подобно. */
    private static Class<?> probedClass;
    private static Method probedGetter;
    private static boolean probeFailedForCurrentClass = false;

    /**
     * Пытается прочитать «горячесть» реактора из его BlockEntity.
     * @return [0.0 .. 1.0] — нормализованная активность; -1.0 — данные недоступны.
     * Вызывать ТОЛЬКО с серверного потока (tick-хендлеры).
     */
    public static double readReactorActivity(BlockEntity be) {
        if (!GOING_NUCLEAR_LOADED || be == null) return -1.0;

        Class<?> clazz = be.getClass();
        if (probedClass != clazz) { // сменили тип BE — перерезолвливаем
            probedClass = clazz;
            probedGetter = findTemperatureGetter(clazz);
            probeFailedForCurrentClass = (probedGetter == null);
            if (probeFailedForCurrentClass) {
                LOGGER.debug("[CSGN] У '{}' не найдено публичного temperature/heat getter'а — " +
                        "реактор будет греть по статическому множителю конфига.", clazz.getSimpleName());
            } else {
                LOGGER.info("[CSGN] Dynamic heat: найден getter {}.{}()",
                        clazz.getSimpleName(), probedGetter.getName());
            }
        }
        if (probeFailedForCurrentClass || probedGetter == null) return -1.0;

        try {
            Object raw = probedGetter.invoke(be);
            double value;
            if (raw instanceof Double d) value = d;
            else if (raw instanceof Float f) value = f;
            else if (raw instanceof Integer i) value = i;
            else if (raw instanceof Long l) value = l;
            else return -1.0;

            // Нормализация: предполагаем шкалу Цельсия ~20..1500 внутри реактора.
            // TODO: Artem's Optional Task - [Точная калибровка шкалы Going Nuclear] (задача #8 в SAM.md)
            // Подкрути нижнюю/верхнюю границу под реальную API-шкалу мода, когда изучишь его jar.
            double min = 20.0, max = 1500.0;
            double normalized = (value - min) / (max - min);
            return clamp01(normalized);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1.0; // чужой BE повёл себя странно — тихо деградируем к статике
        }
    }

    /** Перебираем публичные методы вида getXxx()/xxx() без параметров, возвращающие число. */
    private static Method findTemperatureGetter(Class<?> clazz) {
        Method best = null;
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            Class<?> ret = m.getReturnType();
            if (ret != double.class && ret != float.class && ret != int.class && ret != long.class
                    && ret != Double.class && ret != Float.class) continue;
            String name = m.getName().toLowerCase(Locale.ROOT);
            if (name.contains("temperature") || name.contains("temp")) {
                return m; // лучший кандидат — сразу выходим
            }
            if (best == null && (name.contains("heat") || name.contains("energy"))) {
                best = m;
            }
        }
        return best;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** ID позиции для логов. */
    public static String posToString(BlockPos pos) {
        return pos.toShortString();
    }

    private GoingNuclearBridge() {}
}
