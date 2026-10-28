package com.yourname.myfirstmod.csgn.block;

import com.yourname.myfirstmod.csgn.handler.HeatZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Тестовый блок-«реактор» (csgn:reactor_core).
 *
 * ВАЖНО ДЛЯ БРИДЖА: это НЕ наш собственный реактор — мы не делаем механику с нуля.
 * Этот блок нужен, чтобы ТЕСТИРОВАТЬ тепловую зону без установленного Going Nuclear:
 * его ID лежит в Config.REACTOR_BLOCK_WHITELIST, и ReactorEventHandler регистрирует
 * зону по событию установки. Реальные реакторы Going Nuclear работают по той же схеме —
 * просто добавь их ID в whitelist конфига.
 */
public class ReactorCoreBlock extends Block {

    public ReactorCoreBlock(Properties properties) {
        super(properties);
    }

    /** Блок поставили — дублируем регистрацию зоны (основная — в ReactorEventHandler). */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            var zones = HeatZoneManager.get(level);
            if (zones != null) zones.addReactorZone(pos);
        }
    }

    /** Блок убрали из мира — снимаем тепловую зону. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        // Проверка нужна: onRemove вызывается и при простой смене свойств блока.
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            var zones = HeatZoneManager.get(level);
            if (zones != null) zones.removeReactorZone(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Вызывается каждый тик для блоков рядом с игроком — место для частиц/звуков. */
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            net.minecraft.util.RandomSource randomSource) {
        super.animateTick(state, level, pos, randomSource);

        if  (!level.isClientSide() && gameTime % 8 == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleType.FLAME,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.0, 0.5, 0.0);
        }
    }
}
