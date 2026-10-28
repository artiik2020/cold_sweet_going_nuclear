package com.yourname.myfirstmod.csgn.bridge;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Runtime configuration for heat and the optional, explicitly configured condensation outlet.
 * Every handler reads these values via ConfigValue#get during execution; no startup snapshot
 * is used for radius or intensity.
 */
public final class BridgeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("CSGN/BridgeConfig");
    private BridgeConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue REACTOR_RADIUS;
    public static final ModConfigSpec.DoubleValue PIPE_RADIUS;
    public static final ModConfigSpec.DoubleValue REACTOR_HEAT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue STEAM_HEAT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_TOTAL_HEAT;
    public static final ModConfigSpec.IntValue UPDATE_INTERVAL;
    public static final ModConfigSpec.IntValue CONDENSATION_AMOUNT;
    public static final ModConfigSpec.BooleanValue ENABLE_CONDENSATION;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REACTOR_BLOCK_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIPE_BLOCK_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STEAM_FLUID_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CONDENSATION_OUTPUT_BLOCK_IDS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("heat");
        REACTOR_RADIUS = BUILDER.comment("Maximum reactor heat radius in blocks. Nearby discovery is capped at 32 blocks.")
            .defineInRange("reactorRadius", 16.0, 1.0, 32.0);
        PIPE_RADIUS = BUILDER.comment("Maximum steam pipe heat radius in blocks.")
            .defineInRange("pipeRadius", 8.0, 1.0, 32.0);
        REACTOR_HEAT_MULTIPLIER = BUILDER.comment(
                "Peak contribution from one reactor, in Cold Sweat Minecraft temperature units. 1.0 is a strong effect; 1 MC unit is about 23.3 C.")
            .defineInRange("reactorHeatMultiplier", 4.0, 0.0, 10.0);
        STEAM_HEAT_MULTIPLIER = BUILDER.comment(
                "Peak contribution from one detected steam pipe, in Cold Sweat Minecraft temperature units.")
            .defineInRange("steamHeatMultiplier", 1.5, 0.0, 10.0);
        MAX_TOTAL_HEAT = BUILDER.comment(
                "Hard cap for the combined reactor and steam contribution, in Cold Sweat Minecraft temperature units.")
            .defineInRange("maxTotalHeat", 10.0, 0.0, 20.0);
        UPDATE_INTERVAL = BUILDER.comment(
                "Interval in ticks for block discovery and fluid-capability refresh. Temperature contribution is recalculated every level tick.")
            .defineInRange("updateInterval", 20, 1, 1200);
        BUILDER.pop();

        BUILDER.push("fluids");
        CONDENSATION_AMOUNT = BUILDER.comment("Fluid amount required for one water source block (NeoForge fluid units, normally 1000 mB).")
            .defineInRange("condensationAmount", 1000, 1000, 1000);
        STEAM_FLUID_IDS = BUILDER.comment(
                "Exact fluid registry IDs accepted as steam, e.g. goingnuclear:steam. If empty, only fluids whose namespace looks like Going Nuclear and whose path contains 'steam' are accepted.")
            .defineListAllowEmpty("steamFluidIds", List.of(), value -> value instanceof String);
        ENABLE_CONDENSATION = BUILDER.comment(
                "Enable experimental condensation only at explicitly configured output blocks. Disabled by default to avoid draining steam from normal Create processing lines.")
            .define("enableCondensation", false);
        CONDENSATION_OUTPUT_BLOCK_IDS = BUILDER.comment(
                "Exact block registry IDs that mark a condensation outlet. Leave empty until the actual Going Nuclear radiator/vent ID and output direction are verified.")
            .defineListAllowEmpty("condensationOutputBlockIds", List.of(), value -> value instanceof String);
        BUILDER.pop();

        BUILDER.push("blocks");
        REACTOR_BLOCK_IDS = BUILDER.comment("Exact registry IDs of reactor blocks to detect. Empty enables namespace/path heuristics.")
            .defineListAllowEmpty("reactorBlockIds", List.of(), value -> value instanceof String);
        PIPE_BLOCK_IDS = BUILDER.comment("Exact registry IDs of pipe blocks to detect. Empty enables Create fluid-pipe heuristics.")
            .defineListAllowEmpty("pipeBlockIds", List.of(), value -> value instanceof String);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /**
     * Config events can fire for unrelated files, so only report this mod's bridge spec.
     * Values are read from ModConfigSpec at the time of the event and by the tick handlers.
     */
    public static void onConfigReload(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() != SPEC) {
            return;
        }

        LOGGER.info("Bridge config {}: reactor radius={}, reactor heat={}, pipe radius={}, steam heat={}, max total heat={}, update interval={}, condensation={}",
            config.getType(),
            REACTOR_RADIUS.get(),
            REACTOR_HEAT_MULTIPLIER.get(),
            PIPE_RADIUS.get(),
            STEAM_HEAT_MULTIPLIER.get(),
            MAX_TOTAL_HEAT.get(),
            UPDATE_INTERVAL.get(),
            ENABLE_CONDENSATION.get());
        LOGGER.info("Bridge config IDs: reactors={}, pipes={}, steam fluids={}, condensation outlets={}",
            REACTOR_BLOCK_IDS.get(), PIPE_BLOCK_IDS.get(), STEAM_FLUID_IDS.get(), CONDENSATION_OUTPUT_BLOCK_IDS.get());

        // Config changes may affect a previously cached false steam check or a one-time log.
        // Drop those caches immediately so the new settings take effect on the next tick.
        SteamFluidHelper.clearCache();
        ReactorHandler.resetDiagnostics();
        PipeHeatHandler.resetDiagnostics();

        if (ENABLE_CONDENSATION.get() && CONDENSATION_OUTPUT_BLOCK_IDS.get().isEmpty()) {
            LOGGER.warn("Конденсация включена, но condensationOutputBlockIds пуст. Укажите проверенный registry ID выходного блока; автоматический drain не будет происходить.");
        }
    }
}
