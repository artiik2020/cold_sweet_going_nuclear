package com.yourname.myfirstmod.csgn.block;

import com.yourname.myfirstmod.csgn.bridge.PositionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Existing reactor-core block. Its position is tracked by the bridge cache.
 */
public class ReactorCoreBlock extends Block {
    public ReactorCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            PositionCache.registerReactor(serverLevel, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            PositionCache.unregisterReactor(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        // TODO: Artem's Task — add client-only reactor visual effects.
        if (level.isClientSide() && level.getGameTime() % 8L == 0L) {
            level.addParticle(
                ParticleTypes.FLAME,
                pos.getX() + 0.5,
                pos.getY() + 1.1,
                pos.getZ() + 0.5,
                0.0,
                0.05,
                0.0
            );
        }
    }
}
