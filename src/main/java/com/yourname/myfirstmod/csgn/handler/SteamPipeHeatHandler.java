package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.init.ModBlocks;
import com.yourname.myfirstmod.csgn.integration.SteamFluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Медленный трекер труб Create: раз в Config.heatScanIntervalTicks тиков
 * сканирует ЗАГРУЖЕННЫЕ чанки каждого измерения, запрашивает у блоков
 * Capability FluidHandler.BLOCK и если внутри пар — регистрирует тепловую зону.
 *
 * Почему capability, а не instanceof FluidPipeBlock? Потому что мы НЕ компилируемся
 * против Create: NeoForge сам отдаст IFluidHandler для любой трубы/танка/котла,
 * экспортирующего жидкости (Create регистрирует экспорт на RegisterCapabilitiesEvent).
 * Это стабильный публичный API загрузчика.
 *
 * Сканирование дорогое, поэтому:
 *  - берём только реально загруженные чанки (getChunkIfLoaded — БЕЗ генерации мира!);
 *  - сначала дешёвая проверка: у блока есть BlockEntity? (тubes BE have) — иначе skip;
 *  - полная пересборка раз в N тиков проще и надёжнее инкрементального учёта.
 */
@EventBusSubscriber(modid = "csgn")
public final class SteamPipeHeatHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.steamPipeEffectsEnabled) return;
        int tick = event.getServer().getTickCount();
        if (tick % Config.heatScanIntervalTicks != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            HeatZoneManager zones = HeatZoneManager.get(level);
            if (zones == null) continue;

            zones.clearSteamPipeZones(); // полная пересборка карты зон труб

            // Публичный API: список координат загруженных чанков как long-множество
            // (upack: x в нижних 32 битах, z в верхних — распакует конструктор ChunkPos(long)).
            LongSet loaded = level.getChunkSource().getLoadedChunks();

            LongIterator it = loaded.longIterator();
            while (it.hasNext() && zones.pipeCount() < Config.maxTrackedSources) {
                ChunkPos chunkPos = new ChunkPos(it.nextLong());
                if (level.getChunkIfLoaded(chunkPos) == null) continue; // страховка
                scanChunk(level, chunkPos, zones);
            }
        }
    }

    /** Сканирование одного чанка: каждый блок -> попытка получить fluid capability. */
    private static void scanChunk(ServerLevel level, ChunkPos chunkPos, HeatZoneManager zones) {
        int minX = chunkPos.getMinBlockX(), minZ = chunkPos.getMinBlockZ();
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();

        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    // Дешёвый фильтр: трубы Create всегда имеют BlockEntity.
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be == null) continue;

                    var state = be.getBlockState();

                    // Аккуратный запрос capability: null означает «блок не отдаёт жидкости» —
                    // это НОРМАЛЬНАЯ ситуация, а не ошибка (никаких ClassCastException!).
                    var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
                    if (handler == null) continue;

                    if (SteamFluidHelper.containsSteam(handler)) {
                        // Наш радиатор греет сильнее обычной трубы:
                        double mult = ModBlocks.isRadiator(state) && Config.radiatorDoublesHeat ? 2.0 : 1.0;
                        zones.addSteamPipeZone(pos, mult);
                    }
                }
            }
        }
    }

    private SteamPipeHeatHandler() {}
}
