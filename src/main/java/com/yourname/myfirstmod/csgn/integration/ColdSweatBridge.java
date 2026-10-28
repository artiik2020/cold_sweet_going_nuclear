package com.yourname.myfirstmod.csgn.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflection-мост к Cold Sweat (мы НЕ зависим от него на этапе компиляции).
 * ИСПРАВЛЕНО: ModLoader.get().isModLoaded() -> ModList.get().isLoaded("coldsweat").
 */
public final class ColdSweatBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ИСПРАВЛЕНО: проверка загрузки через ModList, а не ModLoader
    private static final boolean COLD_SWEAT_LOADED = ModList.get().isLoaded("coldsweat");

    private static Method methodAddChange;   // Temperature.addChange(LivingEntity, double/float)
    private static Method methodChange;      // Temperature.change(...)-фолбэк
    private static boolean resolvedAttempted = false;
    private static boolean warnedOnce = false;
    private static boolean loggedDevOnce = false;

    /** Возможные имена класса Temperature у разных версий/изданий Cold Sweat. */
    private static final String[] TEMPERATURE_CLASS_CANDIDATES = {
            "com.robbitstudios.coldsweat.api.util.Temperature",
            "com.mrcrayfish.coldsweat.api.util.Temperature",
    };

    private static synchronized void resolveIfNeeded() {
        if (resolvedAttempted || !COLD_SWEAT_LOADED) return;
        resolvedAttempted = true;
        for (String className : TEMPERATURE_CLASS_CANDIDATES) {
            try {
                Class<?> tempClass = Class.forName(className);
                for (Method m : tempClass.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && LivingEntity.class.isAssignableFrom(params[0])
                            && (params[1] == double.class || params[1] == float.class)) {
                        if (m.getName().equals("addChange")) methodAddChange = m;
                        else if (m.getName().equals("change") && methodChange == null) methodChange = m;
                    }
                }
                if (methodAddChange != null || methodChange != null) return;
            } catch (ClassNotFoundException ignored) {
                // пробуем следующее имя класса
            }
        }
    }

    /**
     * Добавить сущности delta °C. Вызывать ТОЛЬКО из серверного потока!
     */
    public static void addTemperature(LivingEntity entity, double deltaCelsius) {
        if (deltaCelsius == 0.0) return;
        resolveIfNeeded();

        if (methodAddChange != null) {
            tryInvoke(methodAddChange, entity, deltaCelsius);
        } else if (methodChange != null) {
            tryInvoke(methodChange, entity, deltaCelsius);
        } else if (COLD_SWEAT_LOADED) {
            if (!warnedOnce) {
                warnedOnce = true;
                LOGGER.warn("[CSGN] Cold Sweat найден, но Temperature API не резолбнулся — " +
                        "проверь имя пакета API в версии мода.");
            }
        } else if (!loggedDevOnce) {
            loggedDevOnce = true;
            LOGGER.info("[CSGN][dev] Cold Sweat не установлен — нагрев залогируем один раз: " +
                            "{} получил(а) бы {}°C",
                    entity.getName().getString(), String.format("%.2f", deltaCelsius));
        }
    }

    private static void tryInvoke(Method m, LivingEntity entity, double delta) {
        try {
            if (m.getParameterTypes()[1] == float.class) {
                m.invoke(null, entity, (float) delta);
            } else {
                m.invoke(null, entity, delta);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[CSGN] Reflection-вызов Cold Sweat API упал", e);
        }
    }

    private ColdSweatBridge() {}
}
