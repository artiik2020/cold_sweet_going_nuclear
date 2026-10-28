package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Cold Sweat compatibility without a hard compile dependency.
 *
 * Cold Sweat's public Temperature API is discovered at runtime so this bridge
 * can still load when Cold Sweat is absent. Temperature changes are applied
 * on the logical server through the API, which is responsible for syncing
 * player temperature to the client.
 */
public final class ColdSweatAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/ColdSweatBridge");
    private static boolean loggedMissingApi = false;
    private static boolean loggedSuccess = false;

    private static final String[] TEMPERATURE_CLASSES = {
        "com.momosoftworks.coldsweat.api.util.Temperature",
        "com.momosoftworks.coldsweat.api.temperature.Temperature",
        "com.momosoftworks.coldsweat.api.temperature.TempHelper"
    };

    private ColdSweatAdapter() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("cold_sweat");
    }

    /**
     * Adds heat to the player's Cold Sweat world-temperature trait.
     * Strength is a relative Cold Sweat temperature amount, not Celsius.
     */
    public static boolean applyHeat(ServerLevel level, BlockPos source, ServerPlayer player, double strength) {
        if (level.isClientSide() || strength <= 0.0 || !isAvailable()) {
            return false;
        }

        for (String className : TEMPERATURE_CLASSES) {
            try {
                Class<?> temperatureClass = Class.forName(className);
                Class<?> traitClass = findTraitClass(temperatureClass);
                if (traitClass == null || !traitClass.isEnum()) {
                    continue;
                }

                Object worldTrait = enumConstant(traitClass, "WORLD");
                if (worldTrait == null) {
                    continue;
                }

                // Preferred documented API: Temperature.add(entity, Trait.WORLD, amount).
                Method add = findMethod(temperatureClass, "add", 3);
                if (add != null) {
                    add.invoke(null, (LivingEntity) player, worldTrait, strength);
                    logSuccess(className, "add");
                    return true;
                }

                // Compatibility fallback for API builds that expose get/set but not add.
                Method get = findMethod(temperatureClass, "get", 2);
                Method set = findMethod(temperatureClass, "set", 3);
                if (get != null && set != null) {
                    Object current = get.invoke(null, (LivingEntity) player, worldTrait);
                    if (current instanceof Number number) {
                        set.invoke(null, (LivingEntity) player, worldTrait, number.doubleValue() + strength);
                        logSuccess(className, "get/set");
                        return true;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Try the next documented/legacy package name.
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.warn("Cold Sweat temperature API call failed using {}", className, exception);
            }
        }

        if (!loggedMissingApi) {
            LOGGER.error("Cold Sweat is loaded, but its public Temperature API could not be resolved. " +
                "Check the installed Cold Sweat version and its API package.");
            loggedMissingApi = true;
        }
        return false;
    }

    private static Class<?> findTraitClass(Class<?> temperatureClass) {
        for (Class<?> nested : temperatureClass.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("Trait") && nested.isEnum()) {
                return nested;
            }
        }

        String packageName = temperatureClass.getPackageName();
        for (String suffix : new String[] {".Temperature$Trait", ".util.Temperature$Trait"}) {
            try {
                return Class.forName(packageName + suffix);
            } catch (ClassNotFoundException ignored) {
                // Continue searching.
            }
        }
        return null;
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String name, int parameterCount) {
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name)
                || method.getParameterCount() != parameterCount
                || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (!LivingEntity.class.isAssignableFrom(parameters[0])) {
                continue;
            }
            if (!parameters[1].isEnum()) {
                continue;
            }
            if (parameterCount == 3
                && parameters[2] != double.class
                && parameters[2] != Double.class) {
                continue;
            }
            return method;
        }
        return null;
    }

    private static void logSuccess(String className, String method) {
        if (!loggedSuccess) {
            LOGGER.info("Cold Sweat bridge connected using {}.{}()", className, method);
            loggedSuccess = true;
        }
    }
}
