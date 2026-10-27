package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.init.ModBlocks;
import com.yourname.myfirstmod.csgn.integration.SteamFluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Конденсация пара: раз в Config.heatScanIntervalTicks тиков проверяем зоны труб
 * и «вытягиваем» (drain) пар у тех блоков, которые стоят рядом с нашим радиатором
 * (или под открытым небом — см. TODO). Выжатый пар превращается в воду согласно
 * конфигу CONDENSE_TURNS_TO_WATER.
 *
 * Ключевой принцип NeoForge capabilities: сначала SIMULATE (безопасная проверка),
 * потом EXECUTE (реальное изменение). Никогда не мутируем чужой контейнер вслепую.
 */
@EventBusSubscriber(modid = "csgn")
public final class CondensationHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.condensationEnabled) return;
        int tick = event.getServer().getTickCount();
        if (tick % Config.heatScanIntervalTicks != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            HeatZoneManager zones = HeatZoneManager.get(level);
            if (zones == null || zones.pipeCount() == 0) continue;

            // Копируем ключи, чтобы безопасно удалять из карты во время обхода
            var pipePositions = new java.util.ArrayList<>(java.util.List.copyOf(zones.pipeSnapshotKeys()));
            for (BlockPos pipePos : pipePositions) {
                BlockEntityPipeContext ctx = inspect(level, pipePos);
                if (ctx == null) continue;

                // Пар конденсируем только если труба стыкуется с нашим РАДИАТОРОМ
                // (детерминированное поведение — игрок сам выбирает место).
                BlockPos radiatorPos = findAdjacentRadiator(level, pipePos);
                if (radiatorPos == null) continue;

                condenseInto(level, pipePos, radiatorPos);
            }
        }
    }

    /** Запрос capability трубы ещё раз — уже для дренажа. Возвращает handler или null. */
    private static net.neoforged.neoforge.fluids.capability.IFluidHandler fluidHandlerAt(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var be = level.getBlockEntity(pos);
        // getCapability возвращает null, если блок не экспортирует жидкости — это НЕ ошибка!
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
    }

    private record BlockEntityPipeContext(net.neoforged.neoforge.fluids.capability.IFluidHandler handler) {}

    private static BlockEntityPipeContext inspect(ServerLevel level, BlockPos pos) {
        var h = fluidHandlerAt(level, pos);
        return h == null ? null : new BlockEntityPipeContext(h);
    }

    /** Ищет блок-радиатор csgn прямо над/под/сбоку от трубы (6 сторон). */
    private static BlockPos findAdjacentRadiator(ServerLevel level, BlockPos pipePos) {
        for (var dir : net.minecraft.core.Direction.values()) {
            BlockPos neighbor = pipePos.relative(dir);
            if (ModBlocks.isRadiator(level.getBlockState(neighbor))) {
                return neighbor;
            }
        }
        return null;
    }

    /**
     * Собственно конвертация: drainSteam(SIMULATE) -> drainSteam(EXECUTE) -> спавн воды.
     */
    private static void condenseInto(ServerLevel level, BlockPos pipePos, BlockPos radiatorPos) {
        var handler = fluidHandlerAt(level, pipePos);
        if (handler == null) return;

        // 1) Симуляция: сколько реально пара труба готова отдать?
        var simulated = SteamFluidHelper.drainSteam(handler, Config.condenseAmountMb, false);
        if (simulated.isEmpty()) return;

        // =================================================================================
        // TODO: Artem's Task — условие «открытого конца»: добавь сюда проверку, что труба
        // смотрит открытым соединением в воздух (level.isEmptyBlock на стороне коннектора),
        // и тогда тоже вызывай condenseInto. Подсказка: ищи Direction через
        // be.getBlockState().getValue(...) — но у Create-труб это приватно, поэтому проще
        // начать с варианта «рядом с радиатором», который уже работает.
        // =================================================================================

        // 2) Execute: забираем пар из трубы НАВСЕГДА
        var drained = SteamFluidHelper.drainSteam(handler, simulated.getAmount(), true);
        if (drained.isEmpty()) return;

        // 3) Куда деть воду?
        if (Config.condenseTurnsToWater) {
            // Блок воздуха над радиатором -> вода. Если там что-то стоит — не ломаем мир.
            BlockPos waterPos = radiatorPos.above();
            if (level.isEmptyBlock(waterPos)) {
                level.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
            } else {
                // Альтернатива: наполнитьtank радиатора... но у нас его нет — просто испаряем.
                level.playSound(null, radiatorPos, net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.4f);
            }
        }
        // В обоих случаях снимаем тепловую зону этой трубы до следующего скана:
        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones != null) zones.removeSteamPipeZone(pipePos);
    }
}
