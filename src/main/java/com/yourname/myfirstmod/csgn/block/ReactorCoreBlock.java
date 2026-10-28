package com.yourname.myfirstmod.csgn.block;

import com.yourname.myfirstmod.csgn.handler.HeatZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Тестовый блок-«реактор» (csgn:reactor_core).
 * При установке добавляет тепловую зону в кэш, при разрушении — снимает.
 */
public class ReactorCoreBlock extends Block {

    public ReactorCoreBlock(Properties properties) {
        super(properties);
    }

    /** Блок поставили — регистрируем тепловую зону. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            HeatZoneManager zones = HeatZoneManager.get(level);
            if (zones != null) zones.addReactorZone(pos);
        }
    }

    /** Блок убрали из мира — снимаем тепловую зону. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            HeatZoneManager zones = HeatZoneManager.get(level);
            if (zones != null) zones.removeReactorZone(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Клиентский декор: огненные частицы над реактором.
     * ИСПРАВЛЕНО: level.getGameTime() вместо gameTime; ParticleTypes.FLAME вместо ParticleType.FLAME.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (level.isClientSide() && level.getGameTime() % 8 == 0) {
            level.addParticle(ParticleTypes.FLAME,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    pos.getY() + 1.1,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    0.0, 0.05, 0.0);

            // TODO: Artem's Optional Task - [Мираж над горячим реактором] (задача #3 в SAM.md)
            // Добавь КЛИЕНТСКИЕ частицы жара, например:
            //   level.addParticle(ParticleTypes.SMALL_FLAME, ...координаты чуть ниже FLAME...);
            // или свой ParticleOptions. ПОДВОДНЫЙ КАМЕНЬ: анимация вызывается на клиенте —
            // не смей трогать серверные структуры данных (HeatZoneManager.get вернёт тут null).
        }
    }
}
