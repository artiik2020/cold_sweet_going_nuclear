package com.yourname.myfirstmod.csgn.bridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional Cold Sweat adapter. It only uses the documented Temperature get/set API,
 * resolved at runtime so the bridge can compile without bundling Cold Sweat classes.
 *
 * The value applied is an offset to each player's WORLD trait (Minecraft temperature
 * units), not a global mutation of the biome or every block in the level.
 */
public final class ColdSweatAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/ColdSweatBridge");
    private static final double EPSILON = 0.0001;

    private static final Map<UUID, AppliedState> APPLIED = new ConcurrentHashMap<>();
    private static volatile ApiBinding binding;
    private static volatile boolean resolutionAttempted;
    private static volatile boolean loggedMissingApi;
    private static volatile boolean loggedSuccess;

    private record AppliedState(double contribution, double lastSetValue) {}
    private record ApiBinding(Method get, Method set, Object worldTrait, String className) {}

    private static final String TEMPERATURE_CLASS =
        "com.momosoftworks.coldsweat.api.util.Temperature";

    private ColdSweatAdapter() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("cold_sweat");
    }

    /**
     * Updates this player's desired ambient heat contribution.
     *
     * Calling add(WORLD, amount) every tick would accumulate an unbounded offset.
     * Instead, this method changes the previously applied contribution to the new
     * target, and re-applies it if Cold Sweat has recalculated the WORLD trait since
     * our last tick. A zero target removes our tracked contribution.
     */
    public static boolean applyWorldHeat(ServerLevel level, ServerPlayer player, double desiredOffset) {
        if (level.isClientSide() || !isAvailable() || !Double.isFinite(desiredOffset)) {
            return false;
        }

        double desired = Math.max(0.0, desiredOffset);
        UUID playerId = player.getUUID();
        AppliedState previous = APPLIED.get(playerId);

        if (previous == null && desired <= EPSILON) {
            return true;
        }

        ApiBinding api = resolveApi();
        if (api == null) {
            return false;
        }

        try {
            Object currentObject = api.get().invoke(null, (LivingEntity) player, api.worldTrait());
            if (!(currentObject instanceof Number currentNumber)) {
                LOGGER.warn("Cold Sweat Temperature.get returned a non-numeric WORLD value");
                return false;
            }

            double current = currentNumber.doubleValue();
            double target;
            if (previous == null) {
                target = current + desired;
            } else if (Math.abs(current - previous.lastSetValue()) <= EPSILON) {
                // Our previous value is still present: replace only our own contribution.
                target = current - previous.contribution() + desired;
            } else {
                // Cold Sweat or another modifier changed WORLD after our previous tick.
                // Treat the current value as a fresh baseline and re-apply the desired offset.
                target = current + desired;
            }

            api.set().invoke(null, (LivingEntity) player, api.worldTrait(), target);
            if (desired <= EPSILON) {
                APPLIED.remove(playerId);
            } else {
                APPLIED.put(playerId, new AppliedState(desired, target));
            }

            if (!loggedSuccess) {
                LOGGER.info("Cold Sweat WORLD temperature bridge connected through {}.get/set",
                    api.className());
                loggedSuccess = true;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Failed to update Cold Sweat WORLD temperature for player {}", player.getGameProfile().getName(), exception);
            return false;
        }
    }

    private static ApiBinding resolveApi() {
        ApiBinding current = binding;
        if (current != null || resolutionAttempted) {
            return current;
        }

        synchronized (ColdSweatAdapter.class) {
            if (binding != null || resolutionAttempted) {
                return binding;
            }
            resolutionAttempted = true;

            try {
                Class<?> temperatureClass = Class.forName(TEMPERATURE_CLASS);
                Class<?> traitClass = findTraitClass(temperatureClass);
                if (traitClass == null || !traitClass.isEnum()) {
                    throw new ReflectiveOperationException("Temperature.Trait enum was not found");
                }

                Object worldTrait = enumConstant(traitClass, "WORLD");
                Method get = findMethod(temperatureClass, "get", 2);
                Method set = findMethod(temperatureClass, "set", 3);
                if (worldTrait == null || get == null || set == null) {
                    throw new ReflectiveOperationException(
                        "Expected static Temperature.get(LivingEntity, Trait) and set(LivingEntity, Trait, double)");
                }

                binding = new ApiBinding(get, set, worldTrait, temperatureClass.getName());
                return binding;
            } catch (ReflectiveOperationException | LinkageError exception) {
                if (!loggedMissingApi) {
                    LOGGER.error("Cold Sweat is installed, but its documented Temperature get/set API could not be resolved. " +
                        "Check the installed Cold Sweat version. Heat integration is disabled rather than using an unsafe fallback.",
                        exception);
                    loggedMissingApi = true;
                }
                return null;
            }
        }
    }

    private static Class<?> findTraitClass(Class<?> temperatureClass) throws ClassNotFoundException {
        for (Class<?> nested : temperatureClass.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("Trait") && nested.isEnum()) {
                return nested;
            }
        }
        return Class.forName(temperatureClass.getName() + "$Trait");
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
                || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (!parameters[0].isAssignableFrom(LivingEntity.class) || !parameters[1].isEnum()) {
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
}
