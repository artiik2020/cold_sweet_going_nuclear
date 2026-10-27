package com.yourname.myfirstmod.csgn;

import com.mojang.logging.LogUtils;
import com.yourname.myfirstmod.csgn.init.ModAttachments;
import com.yourname.myfirstmod.csgn.init.ModBlocks;
import com.yourname.myfirstmod.csgn.init.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Главный класс мода-бриджа CSGN (Create Steam <-> Going Nuclear heat <-> Cold Sweat).
 *
 * Архитектура одного экрана:
 *   Csgn (bootstrap) ──► Config (радиусы/множители)
 *                     ──► ModBlocks/ModItems/ModAttachments (регистрация)
 *   HeatZoneManager  — хранилище зон тепла на каждый ServerLevel
 *   ReactorCoreBlock.onPlace/onRemove + ReactorEventHandler — быстрый учёт реакторов
 *   SteamPipeHeatHandler (FluidHandler.BLOCK capability)    — учёт труб с паром
 *   CondensationHandler (drain SIMULATE→EXECUTE)            — пар → вода
 *   HeatTickHandler ──► ColdSweatBridge (reflection)        — °C игрокам и мобам
 */
@Mod(Csgn.MODID)
public class Csgn {

    public static final String MODID = "csgn";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Csgn(IEventBus modEventBus, ModContainer modContainer) {
        // 1) Отложенные реестры — NeoForge зарегистрирует блоки/предметы/attachment'ы сам
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModAttachments.init(modEventBus);

        // 2) Конфигурация (run/config/csgn-common.toml)
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 3) Игровые события. Наши handler'-классы помечены @EventBusSubscriber —
        //    они регистрируются автоматически, поэтому здесь только commonSetup-хук.
        modEventBus.addListener(this::commonSetup);

        // 4) Мы сами больше не слушаем ничего напрямую, но регистрация класса безопасна:
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[CSGN] Bridge init. Create detected={}, Cold Sweat detected={}",
                net.neoforged.fml.ModLoader.get().isModLoaded("create"),
                net.neoforged.fml.ModLoader.get().isModLoaded("coldsweat"));
        LOGGER.info("[CSGN] Реакторы: whitelist={}, radius={}, mult={}",
                Config.reactorBlockWhitelist, Config.reactorHeatRadius, Config.reactorHeatMultiplier);
    }
}
