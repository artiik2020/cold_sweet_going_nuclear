package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.init.ModBlocks;
import com.yourname.myfirstmod.csgn.integration.SteamFluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Трекер труб Create с паром ЧЕРЕЗ КЭШ позиций (Set/Map в HeatZoneManager).
 * ИСПРАВЛЕНИЯ 1.21.1:
 *  - НЕТ getChunkSource().getLoadedChunks() / getChunkIfLoaded() — запрещён скан всех чанков;
 *    трубы попадают в кэш через события установки/ломки, перепроверка идёт ТОЛЬКО
 *    по закэшированным позициям с точечной проверкой level.isLoaded(pos);
 *  - НЕТ аннотации @EventBusSubscriber — регистрация вручную в Csgn;
 *  - Capability FluidHandler запрашивается точечно для одной позиции.
 */
public final class SteamPipeHeatHandler {

    /** Трубу/FLUID PIPE поставили (или сосед изменился) — проверяем наличие пара. */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        updatePipeZone(level, pos);
        // Соседи могли получить/потерять соединение с сетью — обновляем их тоже.
        for (Direction dir : Direction.values()) {
            updatePipeZone(level, pos.relative(dir));
        }
    }

    /** Трубу сломали — сразу снимаем её зону из кэша. */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        HeatZoneManager zones = HeatZoneManager.get((ServerLevel) event.getLevel());
        if (zones != null) zones.removeSteamPipeZone(event.getPos());
    }

    /** Раз в интервал перепроверяем ТОЛЬКО закэшированные позиции (без скана мира!). */
    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!Config.steamPipeEffectsEnabled) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % Math.max(1, Config.condenseIntervalTicks) != 0) return;

        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null) return;

        for (BlockPos pos : zones.pipeSnapshotKeys()) {
            // ИСПРАВЛЕНО: level.isLoaded(pos) вместо обращения к незагруженным чанкам
            if (!level.isLoaded(pos)) continue;
            if (!checkHasSteam(level, pos)) {
                zones.removeSteamPipeZone(pos); // пар ушёл из трубы — снимаем зону
            }
        }
    }

    /** Capability-проверка ОДНОЙ позиции + добавление/снятие зоны в кэше. */
    public void updatePipeZone(ServerLevel level, BlockPos pos) {
        if (!Config.steamPipeEffectsEnabled) return;
        if (!level.isLoaded(pos)) return;
        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null) return;

        if (checkHasSteam(level, pos)) {
            BlockState state = level.getBlockState(pos);
            double mult = (ModBlocks.isRadiator(state) && Config.radiatorDoublesHeat) ? 2.0 : 1.0;
            zones.addSteamPipeZone(pos, mult);

            // TODO: Artem's Optional Task - [Медный радиатор конденсирует быстрее] (задача #5 в SAM.md)
            // Подсказка: здесь ты сможешь поставить множитель для нового блока COPPER_RADIATOR,
            // например: if (ModBlocks.isCopperRadiator(state)) mult *= 1.5;
            // Пока метода isCopperRadiator() не существует — оставь как есть, всё работает.
        } else {
            zones.removeSteamPipeZone(pos);
        }
    }

    /**
     * Дёргаем FluidHandler capability ТОЛЬКО для конкретной позиции.
     * ВАЖНОЕ ИСПРАВЛЕНИЕ: у FluidTank/FluidPipe Create естьcapability и на CLIENT'е,
     * но содержимое танков синхронизируется на клиент НЕ полностью — труба могла
     * «не видеть» пар. Поэтому проверяем наличие пара ТОЛЬКО на серверном уровне,
     * а Direction передаём как null (у Create FluidHandler это «любая сторона»).
     */
    private boolean checkHasSteam(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Исправление №1: не требовать BlockEntity вручную — getCapability сам разберётся;
        // но BE всё равно нужен для сигнатуры — берём его без проверки hasBlockEntity(),
        // т.к. уровень вернёт null для блоков без BE и Create это устраивает.
        BlockEntity be = level.getBlockEntity(pos);
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
        return SteamFluidHelper.containsSteam(handler);
    }
}
