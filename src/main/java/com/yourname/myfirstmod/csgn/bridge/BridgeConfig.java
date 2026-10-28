package com.yourname.myfirstmod.csgn.bridge;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Configuration for optional cross-mod integration.
 * Empty registry ID lists are intentional until the exact mod versions are verified.
 */
public final class BridgeConfig {
    private BridgeConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue REACTOR_RADIUS;
    public static final ModConfigSpec.DoubleValue PIPE_RADIUS;
    public static final ModConfigSpec.DoubleValue REACTOR_HEAT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue STEAM_HEAT_MULTIPLIER;
    public static final ModConfigSpec.IntValue UPDATE_INTERVAL;
    public static final ModConfigSpec.IntValue CONDENSATION_AMOUNT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REACTOR_BLOCK_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIPE_BLOCK_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STEAM_FLUID_IDS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("heat");
        REACTOR_RADIUS = BUILDER.comment("Maximum reactor heat radius in blocks. Nearby discovery is limited to a safe scan radius of 32 blocks.")
            .defineInRange("reactorRadius", 16.0, 1.0, 32.0);
        PIPE_RADIUS = BUILDER.comment("Maximum steam pipe heat radius in blocks.")
            .defineInRange("pipeRadius", 5.0, 1.0, 32.0);
        REACTOR_HEAT_MULTIPLIER = BUILDER.comment("Peak reactor heat contribution in Cold Sweat Minecraft temperature units. Contribution fades with distance.")
            .defineInRange("reactorHeatMultiplier", 0.20, 0.0, 2.0);
        STEAM_HEAT_MULTIPLIER = BUILDER.comment("Peak steam heat contribution in Cold Sweat Minecraft temperature units. Contribution fades with distance.")
            .defineInRange("steamHeatMultiplier", 0.10, 0.0, 2.0);
        UPDATE_INTERVAL = BUILDER.comment("Nearby block discovery and steam-capability refresh interval in ticks. Player heat offset is refreshed every server level tick.")
            .defineInRange("updateInterval", 20, 1, 1200);
        BUILDER.pop();

        BUILDER.push("fluids");
        CONDENSATION_AMOUNT = BUILDER.comment("Maximum amount to request for a future condensation operation.")
            .defineInRange("condensationAmount", 250, 1, 10000);
        STEAM_FLUID_IDS = BUILDER.comment("Registry IDs of steam fluids, e.g. modid:steam. Leave empty until verified.")
            .defineListAllowEmpty("steamFluidIds", List.of(), value -> value instanceof String);
        BUILDER.pop();

        BUILDER.push("blocks");
        REACTOR_BLOCK_IDS = BUILDER.comment("Registry IDs of reactor blocks to cache.")
            .defineListAllowEmpty("reactorBlockIds", List.of(), value -> value instanceof String);
        PIPE_BLOCK_IDS = BUILDER.comment("Registry IDs of pipe blocks to cache.")
            .defineListAllowEmpty("pipeBlockIds", List.of(), value -> value instanceof String);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
