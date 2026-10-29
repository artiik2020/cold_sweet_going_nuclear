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
            B.comment("ID блоков, считаемых реакторами. ВАЖНО: сюда нужно ВРУЧНУЮ добавить " +
                            "реальные ID блоков из Going Nuclear (узнай их командой /dump registry | grep goingnuclear), " +
                            "иначе reactors=0 и нагрева от чужих реакторов НЕ БУДЕТ.")
                    .defineListAllowEmpty("reactor.blockWhitelist",
                            List.of("csgn:reactor_core"),   // наш тестовый блок — работает сразу
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

    // ================= МЕХАНИКА №1: ЛОПАЮЩИЕСЯ ТРУБЫ (Pipe Stress & Bursting) =================
    public static final ModConfigSpec.BooleanValue PIPE_BURST_ENABLED =
            B.comment("Включено ли перенапряжение паровых сетей (трубы лопаются после лимита длины цепи)")
                    .define("pipes.burst.enabled", true);

    public static final ModConfigSpec.IntValue PIPE_CHAIN_LIMIT =
            B.comment("Максимальная длина НЕПРЕРЫВНОЙ цепи труб с паром до разрыва. " +
                            "Конденсатор (csgn:condenser) сбрасывает счётчик.")
                    .defineInRange("pipes.burst.chainLimit", 64, 8, 1024);

    public static final ModConfigSpec.IntValue PIPE_SCAN_INTERVAL_TICKS =
            B.comment("Как часто (в тиках) запускать BFS-обход помеченных паровых сетей")
                    .defineInRange("pipes.burst.scanIntervalTicks", 100, 20, 1200);

    public static final ModConfigSpec.IntValue PIPE_MAX_VISITED_PER_SCAN =
            B.comment("Жёсткий потолок узлов, посещаемых одним BFS-проходом (страховка от TPS-убийц)")
                    .defineInRange("pipes.burst.maxVisitedPerScan", 4096, 64, 65536);

    // ================= МЕХАНИКА №2: ДИНАМИЧЕСКОЕ ТЕПЛО РЕАКТОРА (Going Nuclear) =================
    public static final ModConfigSpec.BooleanValue REACTOR_DYNAMIC_HEAT_ENABLED =
            B.comment("true: мощность зоны реактора зависит от его внутренней температуры (чтение BlockEntity через reflection); " +
                            "false: всегда статический множитель из конфига")
                    .define("reactor.dynamicHeatEnabled", true);

    public static final ModConfigSpec.DoubleValue REACTOR_MIN_ACTIVITY_SCALE =
            B.comment("Множитель силы зоны при полностью ХОЛОДНОМ/выключенном реакторе")
                    .defineInRange("reactor.minActivityScale", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue REACTOR_MAX_ACTIVITY_SCALE =
            B.comment("Множитель силы зоны при РЕАКТОРЕ НА МАКСИМУМЕ")
                    .defineInRange("reactor.maxActivityScale", 1.5, 0.0, 10.0);

    public static final ModConfigSpec.IntValue REACTOR_DYNAMIC_SCAN_INTERVAL_TICKS =
            B.comment("Как часто (в тиках) опрашивать BlockEntity реакторов о их внутреннем состоянии")
                    .defineInRange("reactor.dynamicScanIntervalTicks", 40, 10, 600);

    // ===================== ОПЦИОНАЛЬНЫЕ ФИЧИ (заготовки для задач Артема) =====================
    // ВНИМАНИЕ: эти значения по умолчанию ВЫКЛЮЧЕНЫ. Пока код в обработчиках не дописан,
    // конфиг можно трогать смело — ничего не сломается. См. SAM.md, задачи #2 и #4.
    public static final ModConfigSpec.DoubleValue HOT_PIPE_BURN_CHANCE =
            B.comment("[Task #4] Шанс получить урон от ожога при касании трубы с паром, 0.0..1.0.",
                      "По умолчанию 0.0 = фича выключена.")
                    .defineInRange("gameplay.hotPipeBurnChance", 0.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue STEAM_VISUALS_ENABLED =
            B.comment("[Task #1/#2] Показывать ли частицы/звук пара вокруг горячих труб.")
                    .define("visuals.steamEffectsEnabled", false);

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

    // ---- МЕХАНИКА №1: лопающиеся трубы (дефолты совпадают с SPEC) ----
    public static boolean pipeBurstEnabled = true;
    public static int pipeChainLimit = 64;
    public static int pipeScanIntervalTicks = 100;
    public static int pipeMaxVisitedPerScan = 4096;

    // ---- МЕХАНИКА №2: динамическое тепло реактора ----
    public static boolean reactorDynamicHeatEnabled = true;
    public static double reactorMinActivityScale = 0.15;
    public static double reactorMaxActivityScale = 1.5;
    public static int reactorDynamicScanIntervalTicks = 40;

    // ---- Опциональные фичи: дефолты ВЫКЛЮЧЕНЫ, чтобы мода можно было не трогать ----
    public static double hotPipeBurnChance = 0.0;   // Task #4 (0.0 = урона нет)
    public static boolean steamVisualsEnabled = false; // Task #1/#2

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
        // ИСПРАВЛЕНИЕ: у Create 6 для 1.21.1 пар зарегистрирован как "create:steam",
        // но в некоторых сборках/версиях id может отличаться, поэтому дополнительно
        // ловим ЛЮБОЙ id, содержащий "steam" (защита от «pipes=0» при неверном конфиге).
        return id.contains("steam");
    }

    // ==== Обработчики конфиг-событий. РЕГИСТРИРУЮТСЯ ВРУЧНУЮ в конструкторе Csgn ====
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
    }

    /** Публичный вызов refresh() из конструктора Csgn (первичная инициализация полей). */
    public static void forceRefreshFromLoadedSpec() {
        refresh();
    }

    private static void refresh() {
        // Защита от 0: %0 в тик-хендлерах кидает ArithmeticException и молча убивает нагрев.
        heatApplyIntervalTicks    = Math.max(1, HEAT_APPLY_INTERVAL_TICKS.get());
        condenseIntervalTicks     = Math.max(1, CONDENSE_INTERVAL_TICKS.get());
        maxTrackedSources         = MAX_TRACKED_SOURCES.get();

        // Механика №1: лопающиеся трубы
        pipeBurstEnabled          = PIPE_BURST_ENABLED.get();
        pipeChainLimit            = Math.max(8, PIPE_CHAIN_LIMIT.get());
        pipeScanIntervalTicks     = Math.max(20, PIPE_SCAN_INTERVAL_TICKS.get());
        pipeMaxVisitedPerScan     = Math.max(64, PIPE_MAX_VISITED_PER_SCAN.get());

        // Механика №2: динамическое тепло реактора
        reactorDynamicHeatEnabled = REACTOR_DYNAMIC_HEAT_ENABLED.get();
        reactorMinActivityScale   = REACTOR_MIN_ACTIVITY_SCALE.get();
        reactorMaxActivityScale   = Math.max(REACTOR_MIN_ACTIVITY_SCALE.get(), REACTOR_MAX_ACTIVITY_SCALE.get());
        reactorDynamicScanIntervalTicks = Math.max(10, REACTOR_DYNAMIC_SCAN_INTERVAL_TICKS.get());

        reactorEffectsEnabled     = REACTOR_EFFECTS_ENABLED.get();
        reactorHeatRadius         = Math.max(1, REACTOR_HEAT_RADIUS.get());
        reactorHeatMultiplier     = REACTOR_HEAT_MULTIPLIER.get();
        reactorBlockWhitelist     = REACTOR_BLOCK_WHITELIST.get().stream()
                .map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet());

        steamPipeEffectsEnabled   = STEAM_PIPE_EFFECTS_ENABLED.get();
        steamPipeHeatRadius       = Math.max(1, STEAM_PIPE_HEAT_RADIUS.get());
        steamPipeHeatMultiplier   = STEAM_PIPE_HEAT_MULTIPLIER.get();
        steamFluidPrefixes        = List.copyOf(STEAM_FLUID_IDS.get());

        condensationEnabled       = CONDENSATION_ENABLED.get();
        condenseTurnsToWater      = CONDENSE_TURNS_TO_WATER.get();
        condenseAmountMb          = CONDENSE_AMOUNT_MB.get();
        radiatorDoublesHeat       = RADIATOR_DOUBLES_HEAT.get();

        // Опциональные фичи (см. SAM.md). Дефолты в TOML выключены — ничего не меняешь, если не делаешь задачи.
        hotPipeBurnChance         = HOT_PIPE_BURN_CHANCE.get();
        steamVisualsEnabled       = STEAM_VISUALS_ENABLED.get();
    }

    private Config() {}
}
