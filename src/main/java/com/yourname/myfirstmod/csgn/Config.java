package com.yourname.myfirstmod.csgn;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Конфигурация мода-бриджа (ModConfigSpec).
 * ВАЖНО: аннотация @EventBusSubscriber(bus = Bus.MOD) УДАЛЕНА — слушатели конфиг-событий
 * регистрируются вручную через modEventBus.addListener(...) в конструкторе Csgn.
 */
public class Config {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ============================ РЕАКТОРЫ (Going Nuclear) ============================
    public static final ModConfigSpec.BooleanValue REACTOR_EFFECTS_ENABLED =
            B.comment("Включено ли излучение тепла от блоков-реакторов").define("reactor.enabled", true);

    public static final ModConfigSpec.IntValue REACTOR_HEAT_RADIUS =
            B.comment("Радиус тепловой зоны реактора, в блоках").defineInRange("reactor.heatRadius", 100, 1, 512);

    public static final ModConfigSpec.DoubleValue REACTOR_HEAT_MULTIPLIER =
            B.comment("Множитель тепла в центре зоны реактора (убывает линейно к краю)").defineInRange("reactor.heatMultiplier", 4.0, 0.0, 100.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> REACTOR_BLOCK_WHITELIST =
            B.comment("ID блоков, считаемых реакторами.")
                    .defineListAllowEmpty("reactor.blockWhitelist",
                            List.of("csgn:reactor_core"),
                            Config::isValidId);

    // ====================== ТРУБЫ СО ПАРОМ (Create FluidHandler Capability) ======================
    public static final ModConfigSpec.BooleanValue STEAM_PIPE_EFFECTS_ENABLED =
            B.comment("Включено ли тепло от труб Create, по которым течёт пар").define("pipes.enabled", true);

    public static final ModConfigSpec.IntValue STEAM_PIPE_HEAT_RADIUS =
            B.comment("Радиус тепловой зоны вокруг трубы с паром, в блоках").defineInRange("pipes.steamHeatRadius", 5, 1, 64);

    public static final ModConfigSpec.DoubleValue STEAM_PIPE_HEAT_MULTIPLIER =
            B.comment("Множитель тепла от одной трубы с паром").defineInRange("pipes.steamHeatMultiplier", 1.5, 0.0, 100.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> STEAM_FLUID_IDS =
            B.comment("ID жидкостей, считаемых «горячим паром» (сравнение с НАЧАЛА строки).")
                    .defineListAllowEmpty("pipes.steamFluidIds", List.of("create:steam"),
                            Config::isValidId);

    // ================================ КОНДЕНСАЦИЯ ПАРА ================================
    public static final ModConfigSpec.BooleanValue CONDENSATION_ENABLED =
            B.comment("Включена ли конденсация пара в воду").define("condensation.enabled", true);

    public static final ModConfigSpec.BooleanValue CONDENSE_TURNS_TO_WATER =
            B.comment("true: вместо пара в трубу загоняется вода; false: пар просто drain'ится")
                    .define("condensation.turnIntoWater", true);

    public static final ModConfigSpec.IntValue CONDENSE_AMOUNT_MB =
            B.comment("mB пара за одну операцию конденсации (1000 mB = 1 ведро)").defineInRange("condensation.amountPerTick", 1000, 1, 16_000);

    public static final ModConfigSpec.BooleanValue RADIATOR_DOUBLES_HEAT =
            B.comment("Даёт ли блок-радиатор двойной множитель тепла").define("condensation.radiatorDoublesHeat", true);

    // =============================== ПРОИЗВОДИТЕЛЬНОСТЬ ===============================
    public static final ModConfigSpec.IntValue HEAT_APPLY_INTERVAL_TICKS =
            B.comment("Как часто (в тиках) применять тепло к игрокам/мебам").defineInRange("perf.applyIntervalTicks", 10, 1, 200);

    public static final ModConfigSpec.IntValue CONDENSE_INTERVAL_TICKS =
            B.comment("Как часто (в тиках) пытаться конденсировать пар у радиаторов").defineInRange("perf.condenseIntervalTicks", 40, 5, 1000);

    public static final ModConfigSpec.IntValue MAX_TRACKED_SOURCES =
            B.comment("Максимум отслеживаемых источников тепла (защита от TPS-убийц)").defineInRange("perf.maxTrackedSources", 4096, 16, 65536);

    public static final ModConfigSpec SPEC = B.build();

    // ---- Кэшированные значения (читаются обработчиками каждый тик без обращения к SPEC) ----
    public static boolean reactorEffectsEnabled = true;
    public static int reactorHeatRadius = 100;
    public static double reactorHeatMultiplier = 4.0;
    public static Set<ResourceLocation> reactorBlockWhitelist = Set.of(ResourceLocation.parse("csgn:reactor_core"));

    public static boolean steamPipeEffectsEnabled = true;
    public static int steamPipeHeatRadius = 5;
    public static double steamPipeHeatMultiplier = 1.5;
    public static List<String> steamFluidPrefixes = List.of("create:steam");

    public static boolean condensationEnabled = true;
    public static boolean condenseTurnsToWater = true;
    public static int condenseAmountMb = 1000;
    public static boolean radiatorDoublesHeat = true;

    public static int heatApplyIntervalTicks = 10;
    public static int condenseIntervalTicks = 40;
    public static int maxTrackedSources = 4096;

    /** Валидатор строк конфига: пробуем распарсить как ResourceLocation. */
    private static boolean isValidId(Object obj) {
        if (!(obj instanceof String s)) return false;
        try {
            ResourceLocation.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Является ли блок с таким ID реактором по whitelist. */
    public static boolean isReactorBlock(ResourceLocation blockId) {
        return reactorBlockWhitelist.contains(blockId);
    }

    /** Считается ли жидкость с таким ID «паром» по префиксам конфига. */
    public static boolean isSteamFluid(ResourceLocation fluidId) {
        String id = fluidId.toString();
        for (String prefix : steamFluidPrefixes) {
            if (id.startsWith(prefix)) return true;
        }
        return false;
    }

    // ==== Обработчики конфиг-событий. РЕГИСТРИРУЮТСЯ ВРУЧНУЮ в конструкторе Csgn ====
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
    }

    private static void refresh() {
        reactorEffectsEnabled     = REACTOR_EFFECTS_ENABLED.get();
        reactorHeatRadius         = REACTOR_HEAT_RADIUS.get();
        reactorHeatMultiplier     = REACTOR_HEAT_MULTIPLIER.get();
        reactorBlockWhitelist     = REACTOR_BLOCK_WHITELIST.get().stream()
                .map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet());

        steamPipeEffectsEnabled   = STEAM_PIPE_EFFECTS_ENABLED.get();
        steamPipeHeatRadius       = STEAM_PIPE_HEAT_RADIUS.get();
        steamPipeHeatMultiplier   = STEAM_PIPE_HEAT_MULTIPLIER.get();
        steamFluidPrefixes        = List.copyOf(STEAM_FLUID_IDS.get());

        condensationEnabled       = CONDENSATION_ENABLED.get();
        condenseTurnsToWater      = CONDENSE_TURNS_TO_WATER.get();
        condenseAmountMb          = CONDENSE_AMOUNT_MB.get();
        radiatorDoublesHeat       = RADIATOR_DOUBLES_HEAT.get();

        heatApplyIntervalTicks    = HEAT_APPLY_INTERVAL_TICKS.get();
        condenseIntervalTicks     = CONDENSE_INTERVAL_TICKS.get();
        maxTrackedSources         = MAX_TRACKED_SOURCES.get();
    }

    private Config() {}
}
