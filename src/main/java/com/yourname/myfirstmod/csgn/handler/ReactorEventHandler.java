package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Быстрый путь регистрации реакторов: при установке/ломке блока сверяем его ID
 * с whitelist из конфига (Config.REACTOR_BLOCK_WHITELIST). Так мы «видим»
 * реакторы Going Nuclear без их API — достаточно знать ID блока.
 *
 * ИСПРАВЛЕНО: НЕТ аннотации @EventBusSubscriber — класс регистрируется вручную
 * через NeoForge.EVENT_BUS.register(new ReactorEventHandler()) в конструкторе Csgn.
 * Методы теперь НЕ static (экземплярные слушатели).
 */
public final class ReactorEventHandler {

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null) return;

        BlockState placed = event.getPlacedBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(placed.getBlock());
        if (Config.isReactorBlock(id)) {
            zones.addReactorZone(event.getPos());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        HeatZoneManager zones = HeatZoneManager.get((ServerLevel) event.getLevel());
        if (zones == null) return;

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (Config.isReactorBlock(id)) {
            zones.removeReactorZone(event.getPos());
        }
    }

    /** Взрыв снёс реактор — снимаем зоны всех реакторов, попавших в affectedBlocks. */
    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null) return;

        for (BlockPos pos : event.getAffectedBlocks()) {
            // ИСПРАВЛЕНО: точечная проверка level.isLoaded(pos) вместо обращения к чанкам
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (Config.isReactorBlock(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                zones.removeReactorZone(pos);
            }
        }
    }
}
