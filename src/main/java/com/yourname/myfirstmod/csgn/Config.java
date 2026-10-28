package com.yourname.myfirstmod.csgn;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Конфигурация мода-бриджа (NeoForge ModConfigSpec).
 * Файл создаётся автоматически после первого запуска: run/config/csgn-common.toml
 *
 * Как это работает:
 *  - ModConfigSpec.Builder описывает «схему» конфига (ключ, тип, значение по умолчанию);
 *  - SPEC передаётся в modContainer.registerConfig(...) в главном классе мода;
 *  - при загрузке/перезагрузке конфига (ModConfigEvent) значения копируются
 *    в простые статические поля — читать их каждый тик через .get() медленно.
 */
@EventBusSubscriber(modid = Csgn.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ============================ РЕАКТОРЫ (Going Nuclear) ============================
    public static final ModConfigSpec.BooleanValue REACTOR_EFFECTS_ENABLED =
            B.comment("Включено ли излучение тепла от блоков-реакторов").define("reactor.enabled", true);

    public static final ModConfigSpec.IntValue REACTOR_HEAT_RADIUS =
            B.comment("Радиус тепловой зоны реактора, в блоках").defineInRange("reactor.heatRadius", 100, 1, 512);

    public static final ModConfigSpec.DoubleValue REACTOR_HEAT_MULTIPLIER =
            B.comment("Множитель тепла: сколько «единиц нагрева» получает зона в центре реактора.",
                      "Значение плавно убывает до нуля на краю радиуса.").defineInRange("reactor.heatMultiplier", 4.0, 0.0, 100.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> REACTOR_BLOCK_WHITELIST =
            B.comment("Список ID блоков, которые считаются реакторами.",
                      "Так бридж определяет реакторы Going Nuclear БЕЗ компиляции против его API:",
                      "просто добавь сюда ID блока из мода (например 'goingnuclear:fission_reactor_core').",
                      "Наш тестовый блок csgn:reactor_core уже включён.")
                    .defineListAllowEmpty("reactor.blockWhitelist",
                            List.of("csgn:reactor_core"),
                            obj -> obj instanceof String s && ResourceLocation.isValidPath(s));

    // ====================== ТРУБЫ СО ПАРОМ (Create FluidHandler Capability) ======================
    public static final ModConfigSpec.BooleanValue STEAM_PIPE_EFFECTS_ENABLED =
            B.comment("Включено ли тепло от труб Create, по которым течёт пар").define("pipes.enabled", true);

    public static final ModConfigSpec.IntValue STEAM_PIPE_HEAT_RADIUS =
            B.comment("Радиус тепловой зоны вокруг трубы с паром, в блоках").defineInRange("pipes.steamHeatRadius", 5, 1, 64);

    public static final ModConfigSpec.DoubleValue STEAM_PIPE_HEAT_MULTIPLIER =
            B.comment("Множитель тепла от одной трубы с паром").defineInRange("pipes.steamHeatMultiplier", 1.5, 0.0, 100.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> STEAM_FLUID_IDS =
            B.comment("ID жидкостей, считаемых «горячим паром». Совпадение с НАЧАЛОМ строки тоже подходит,",
                      "так что 'create:steam' поймает и 'create:steam', и будущие варианты типа 'create:steam_hot'.")
                    .defineListAllowEmpty("pipes.steamFluidIds", List.of("create:steam"),
                            obj -> obj instanceof String s && ResourceLocation.isValidPath(s));

    // ================================ КОНДЕНСАЦИЯ ПАРА ================================
    public static final ModConfigSpec.BooleanValue CONDENSATION_ENABLED =
            B.comment("Включена ли конденсация пара в воду").define("condensation.enabled", true);

    public static final ModConfigSpec.BooleanValue CONDENSE_TURNS_TO_WATER =
            B.comment("true:  слитый пар заменяется водой в мире (setBlockAndUpdate WATER)",
                      "false: пар просто сливается (drain) из трубы и исчезает — тепловая зона снимается")
                    .define("condensation.turnIntoWater", true);

    public static final ModConfigSpec.IntValue CONDENSE_AMOUNT_MB =
            B.comment("Сколько миллибукетов (mB) пара конденсируется за одну операцию",
                      "1000 mB = 1 ведро = целый блок воды").defineInRange("condensation.amountPerTick", 1000, 1, 16_000);

    public static final ModConfigSpec.BooleanValue RADIATOR_DOUBLES_HEAT =
            B.comment("Даёт ли наш блок-радиатор двойной множитель тепла во время конденсации").define("condensation.radiatorDoublesHeat", true);

    // =============================== ПРОИЗВОДИТЕЛЬНОСТЬ ===============================
    public static final ModConfigSpec.IntValue HEAT_SCAN_INTERVAL_TICKS =
            B.comment("Как часто (в тиках) сканировать загруженные чанки на трубы с паром. 20 тиков = 1 секунда.",
                      "Увеличь при лагах на больших серверах.").defineInRange("perf.scanIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue HEAT_APPLY_INTERVAL_TICKS =
            B.comment("Как часто применять тепло к игрокам/мебам (в тиках)")
                    .defineInRange("perf.applyIntervalTicks", 10, 1, 200);

    public static final ModConfigSpec.IntValue MAX_TRACKED_SOURCES =
            B.comment("Максимум одновременно отслеживаемых источников тепла (защита от TPS-убийц)")
                    .defineInRange("perf.maxTrackedSources", 4096, 16, 65536);

    static final ModConfigSpec SPEC = B.build();

    // ---- Кэшированные значения (читаются обработчиками каждый тик) ----
    public static boolean reactorEffectsEnabled;
    public static int reactorHeatRadius;
    public static double reactorHeatMultiplier;
    public static Set<ResourceLocation> reactorBlockWhitelist = Set.of();

    public static boolean steamPipeEffectsEnabled;
    public static int steamPipeHeatRadius;
    public static double steamPipeHeatMultiplier;
    public static List<String> steamFluidPrefixes = List.of();

    public static boolean condensationEnabled;
    public static boolean condenseTurnsToWater;
    public static int condenseAmountMb;
    public static boolean radiatorDoublesHeat;

    public static int heatScanIntervalTicks;
    public static int heatApplyIntervalTicks;
    public static int maxTrackedSources;

    /** Является ли блок с таким ID реактором по whitelist из конфига. */
    public static boolean isReactorBlock(ResourceLocation blockId) {
        return reactorBlockWhitelist.contains(blockId);
    }

    /** Считается ли данная жидкость «паром» по префиксам из конфига. */
    public static boolean isSteamFluid(ResourceLocation fluidId) {
        String id = fluidId.toString();
        for (String prefix : steamFluidPrefixes) {
            if (id.startsWith(prefix)) return true;
        }
        return false;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) { refresh(); }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) { refresh(); }

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

        heatScanIntervalTicks     = HEAT_SCAN_INTERVAL_TICKS.get();
        heatApplyIntervalTicks    = HEAT_APPLY_INTERVAL_TICKS.get();
        maxTrackedSources         = MAX_TRACKED_SOURCES.get();
    }
}
