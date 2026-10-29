package com.yourname.myfirstmod.csgn.handler;

import com.mojang.logging.LogUtils;
import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.integration.SteamFluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

/**
 * МЕХАНИКА №1 (исполнитель): «Лопающиеся трубы».
 * Раз в Config.pipeScanIntervalTicks тиков запускает BFS-обход PipeNetworkWalker
 * и для каждой трубы, чья цепь ПРЕВЫСИЛА лимит давления:
 *   1) вытягивает из неё весь пар (drainSteam EXECUTE);
 *   2) удаляет блок (setBlock AIR — труба «разрывается», дроп не возвращается —
 *      это цена перегруза сети);
 *   3) снимает тепловую зону из кэша;
 *   4) играет ванильный звук разрыва + выпускает остатки пара в мир частицами.
 */
public final class PipeBurstHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (!Config.pipeBurstEnabled) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        int interval = Math.max(1, Config.pipeScanIntervalTicks); // защита от %0
        if (level.getGameTime() % interval != 0) return;

        for (PipeNetworkWalker.BurstTarget target : PipeNetworkWalker.scanAndCollect(level)) {
            burstPipe(level, target);
        }
    }

    /** Собственно «разрыв» одной трубы. */
    private void burstPipe(ServerLevel level, PipeNetworkWalker.BurstTarget target) {
        BlockPos pos = target.pos();
        if (!level.isLoaded(pos)) {
            PipeNetworkWalker.unmarkDirty(pos);
            return;
        }

        // 1) Сбрасываем давление: вытягиваем ВЕСЬ пар из трубы (до 64 ведёр за раз).
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, pos,
                level.getBlockState(pos), level.getBlockEntity(pos), null);
        FluidStack released = SteamFluidHelper.drainSteam(handler, 64_000, true);

        // 2) Удаляем блок трубы (без возврата предмета — перенапряжение сети наказывает игрока).
        level.destroyBlock(pos, false, null);

        // 3) Убираем позицию из всех наших кэшей, чтобы обработчики не дёргали несуществующий блок.
        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones != null) zones.removeSteamPipeZone(pos);
        PipeNetworkWalker.unmarkDirty(pos);

        // 4) Звук + визуал разрыва.
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        spawnBurstParticles(level, pos, released.getAmount());

        // =================================================================================
        // TODO: Artem's Optional Task - [Кастомные эффекты взрыва трубы] (задача #7 в SAM.md)
        // Здесь ты можешь усилить «вау-эффект» разрыва:
        //   - вытолкнуть соседей: level.getEntities(...AABB...) вокруг pos и push();
        //   - поджечь madera рядом: pos.relative(Direction.UP) -> Blocks.FIRE;
        //   - выпустить ОБЛАКО пара в мир частицами (см. подсказку ниже).
        // Пока ничего не делаешь — взрыв работает базово (звук + FLAME/CLOUD), всё компилируется.
        // =================================================================================

        LOGGER.info("[CSGN] Труба лопнула на {} (цепь={} > лимит={}), сброшено пара: {} mB",
                pos.toShortString(), target.chainLength(), Config.pipeChainLimit, released.getAmount());
    }

    /**
     * Базовые частицы разрыва: FLAME + CLOUD. ВАЖНО: addParticle вызывается на СЕРВЕРЕ —
     * обычный Level.addParticle создаёт SPAWN-событие, которое доходит до клиентов сам.
     * ИСПОЛЬЗУЕМ ParticleTypes (константы), НЕ ParticleType!
     */
    private void spawnBurstParticles(ServerLevel level, BlockPos pos, int steamMb) {
        int clouds = Math.min(24, Math.max(6, steamMb / 500)); // чем больше пара было — тем гуще облако
        for (int i = 0; i < clouds; i++) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    pos.getX() + 0.5 + (level.random.nextDouble() - 0.5),
                    pos.getY() + 0.5 + (level.random.nextDouble() - 0.5),
                    pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5),
                    1, 0.0, 0.08, 0.0, 0.01);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.05);

        // Если над трубой пусто — оставим «лужу конденсата» как последствия аварии.
        BlockPos above = pos.above();
        if (level.isLoaded(above) && level.isEmptyBlock(above) && Config.condenseTurnsToWater) {
            level.setBlockAndUpdate(above, Blocks.WATER.defaultBlockState());
        }
    }
}
