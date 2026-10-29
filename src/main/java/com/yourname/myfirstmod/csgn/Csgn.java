package com.yourname.myfirstmod.csgn;

import com.mojang.logging.LogUtils;
import com.yourname.myfirstmod.csgn.init.ModAttachments;
import com.yourname.myfirstmod.csgn.init.ModBlocks;
import com.yourname.myfirstmod.csgn.init.ModItems;
import com.yourname.myfirstmod.csgn.integration.ColdSweatBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Главный класс мода-бриджа CSGN (Create Steam <-> Going Nuclear heat <-> Cold Sweat).
 * Мы НЕ создаём свои реакторы/трубы/систему температуры — читаем API чужих модов и связываем их.
 */
@Mod(Csgn.MODID)
public class Csgn {

    public static final String MODID = "csgn";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Csgn(IEventBus modEventBus, ModContainer modContainer) {
        // 1) Отложенные реестры
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // 2) Конфигурация
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 3) СОБЫТИЯ MOD-ШИНЫ — регистрируем ВРУЧНУЮ
        //    (аннотация @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD) устарела в 1.21.1)
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(Config::onConfigLoad);
        modEventBus.addListener(Config::onConfigReload);

        // 4) СОБЫТИЯ GAME-ШИНЫ — ручная регистрация экземпляров классов-обработчиков
        NeoForge.EVENT_BUS.register(new com.yourname.myfirstmod.csgn.handler.HeatTickHandler());
        NeoForge.EVENT_BUS.register(new com.yourname.myfirstmod.csgn.handler.ReactorEventHandler());
        NeoForge.EVENT_BUS.register(new com.yourname.myfirstmod.csgn.handler.SteamPipeHeatHandler());
        NeoForge.EVENT_BUS.register(new com.yourname.myfirstmod.csgn.handler.CondensationHandler());
        NeoForge.EVENT_BUS.register(new com.yourname.myfirstmod.csgn.handler.PipeBurstHandler());
        NeoForge.EVENT_BUS.register(new com.yourname.myfirstmod.csgn.debug.CsgnDebug());

        // 5) ПЕРВЫЙ refresh() конфига. Без него статические поля Config останутся
        //    дефолтами (radius=100 из TOML не применятся, whitelist будет пустым Set.of(),
        //    а heatApplyIntervalTicks=0 -> деление на ноль / %0 -> IllegalArgumentException,
        //    и тик-хендлеры молча упадут в лог). NeoForge обычно грузит конфиг до конструктора,
        //    но событие Loading могло пройти раньше подписки — поэтому принудительно рефрешимся.
        try {
            Config.SPEC.getValues(); // инициализация Toml
            Config.forceRefreshFromLoadedSpec();
        } catch (Throwable t) {
            LOGGER.warn("[CSGN] Ранний refresh конфига не удался, подождём событие Loading: {}", t.toString());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // ПРОВЕРКА ЗАГРУЖЕННОСТИ МОДОВ: ModLoader.get().isModLoaded() запрещён —
        // используем net.neoforged.fml.ModList.get().isLoaded(...)
        boolean createLoaded = ModList.get().isLoaded("create");
        boolean coldSweatLoaded = ModList.get().isLoaded(ColdSweatBridge.MODID); // РЕАЛЬНЫЙ modid Cold Sweat v2.8+ (mrgremalbaum)
        boolean goingNuclearLoaded = ModList.get().isLoaded("goingnuclear");

        LOGGER.info("[CSGN] Bridge init. create={}, coldsweat={}, goingnuclear={}",
                createLoaded, coldSweatLoaded, goingNuclearLoaded);
        LOGGER.info("[CSGN] Реакторы: enabled={}, radius={}, mult={}, whitelist={}",
                Config.reactorEffectsEnabled, Config.reactorHeatRadius,
                Config.reactorHeatMultiplier, Config.reactorBlockWhitelist);
        LOGGER.info("[CSGN] Пар: enabled={}, radius={}, fluidPrefixes={}",
                Config.steamPipeEffectsEnabled, Config.steamPipeHeatRadius, Config.steamFluidPrefixes);
        LOGGER.info("[CSGN] Лопающиеся трубы: enabled={}, chainLimit={}, scanInterval={}",
                Config.pipeBurstEnabled, Config.pipeChainLimit, Config.pipeScanIntervalTicks);
        LOGGER.info("[CSGN] Динамическое тепло реакторов: enabled={}, scale={}..{}, interval={}",
                Config.reactorDynamicHeatEnabled, Config.reactorMinActivityScale,
                Config.reactorMaxActivityScale, Config.reactorDynamicScanIntervalTicks);
    }

    /** Итоговая сводка по интеграциям — вызывается один раз на старте. */
    @SuppressWarnings("unused")
    private static String integrationSummary() {
        return "create=" + ModList.get().isLoaded("create")
                + ", cold_sweat=" + ModList.get().isLoaded(ColdSweatBridge.MODID)
                + ", goingnuclear=" + com.yourname.myfirstmod.csgn.integration.GoingNuclearBridge.GOING_NUCLEAR_LOADED;
    }
}
