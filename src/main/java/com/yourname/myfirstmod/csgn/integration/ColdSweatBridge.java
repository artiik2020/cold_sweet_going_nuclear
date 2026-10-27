package com.yourname.myfirstmod.csgn.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModLoader;
import org.slf4j.Logger;

/**
 * Безопасный «мост» к Cold Sweat.
 *
 * ПОЧЕМУ РЕФЛЕКСИЯ? Мы НЕ компилируемся против классов Cold Sweat (их может не
 * быть в dev-окружении, и они могут менять API между версиями). Поэтому:
 *   1) при первом обращении пробуем загрузить класс
 *      com.robbitstudios.coldsweat.api.util.Temperature (актуальное имя пакета —
 *      проверь в jar мода: right-click -> Open Source / Class Search);
 *   2) находим метод addChange(LivingEntity, double) или change(...);
 *   3) если что-то не найдено — молча деградируем до console-log (режим отладки),
 *      НИКАКИХ крашей игры.
 *
 * Это типичная техника bridge-модов («soft dependency»).
 */
public final class ColdSweatBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean coldSweatLoaded = false;

    // Кандидатные имена классов Temperature-API разных версий Cold Sweat:
    private static final String[] TEMPERATURE_CLASS_CANDIDATES = {
            "com.robbitstudios.coldsweat.api.util.Temperature",
            "com.mrcrayfish.coldsweat.api.util.Temperature", // старые сборки
    };

    private static java.lang.reflect.Method methodAddChange; // Temperature.addChange(entity, delta)
    private static java.lang.reflect.Method methodChange;    // Temperature.change(entity, delta)

    static {
        coldSweatLoaded = ModLoader.get().isModLoaded("coldsweat");
        if (coldSweatLoaded) tryResolve();
        LOGGER.info("[CSGN] Cold Sweat detected: {}. Bridge resolved: {}",
                coldSweatLoaded, methodAddChange != null || methodChange != null);
    }

    private static void tryResolve() {
        for (String className : TEMPERATURE_CLASS_CANDIDATES) {
            try {
                Class<?> tempClass = Class.forName(className);
                // Ищем статический метод вида (LivingEntity, double/float): void
                for (java.lang.reflect.Method m : tempClass.getMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    var params = m.getParameterTypes();
                    if (params.length == 2 && LivingEntity.class.isAssignableFrom(params[0])
                            && (params[1] == double.class || params[1] == float.class)) {
                        if (m.getName().equals("addChange")) methodAddChange = m;
                        else if (m.getName().equals("change") && methodChange == null) methodChange = m;
                    }
                }
                if (methodAddChange != null || methodChange != null) return;
            } catch (ClassNotFoundException ignored) {
                // пробуем следующее имя пакета
            }
        }
    }

    /** Добавляет сущности delta °C (положительный = нагрев). Вызывать только с серверного потока! */
    public static void addTemperature(LivingEntity entity, double deltaCelsius) {
        if (deltaCelsius == 0.0) return;

        if (methodAddChange != null) {
            tryInvoke(methodAddChange, entity, deltaCelsius);
        } else if (methodChange != null) {
            tryInvoke(methodChange, entity, deltaCelsius);
        } else if (coldSweatLoaded) {
            // Cold Sweat стоит, но API не резолбнулся — НЕ спамим каждый тик, логи раз в ~5 сек
            if (System.nanoTime() % 1_000_000_000L < 20_000_000L) {
                LOGGER.warn("[CSGN] Cold Sweat найден, но Temperature API не резолбнулся. " +
                            "Проверь пакет класса в твоей версии мода (TEMPERATURE_CLASS_CANDIDATES).");
            }
        } else {
            // Режим разработки без Cold Sweat: просто пишем в консоль, чтобы видеть механику
            if (System.nanoTime() % 1_000_000_000L < 20_000_000L) {
                LOGGER.info("[CSGN][dev] {} получил(а) бы +{}°C от тепловых зон",
                        entity.getName().getString(), String.format("%.2f", deltaCelsius));
            }
        }
    }

    private static void tryInvoke(java.lang.reflect.Method m, LivingEntity entity, double delta) {
        try {
            if (m.getParameterTypes()[1] == float.class) {
                m.invoke(null, entity, (float) delta);
            } else {
                m.invoke(null, entity, delta);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[CSGN] Отражённый вызов Cold Sweat API упал (игнорируем, игра живёт)", e);
        }
    }

    private ColdSweatBridge() {}
}
