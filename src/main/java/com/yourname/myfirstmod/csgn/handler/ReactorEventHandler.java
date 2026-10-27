package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Быстрый путь регистрации реакторов: при установке/ломке блока сверяем его ID
 * с whitelist из конфига (Config.REACTOR_BLOCK_WHITELIST). Так мы «видим»
 * реакторы Going Nuclear без их API — достаточно знать ID блока.
 *
 * Дополнительно: при старте мира можно просканировать чанки на уже стоящие
 * реакторы (см. TODO) — иначе после перезапуска сервера зоны пропадут,
 * пока блоки не будут затронуты.
 */
@EventBusSubscriber(modid = "csgn")
public final class ReactorEventHandler {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var zones = HeatZoneManager.get(level);
        if (zones == null) return;

        BlockState placed = event.getPlacedBlock();
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(placed.getBlock());
        if (Config.isReactorBlock(id)) {
            zones.addReactorZone(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        var zones = HeatZoneManager.get((ServerLevel) event.getLevel());
        if (zones == null) return;

        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (Config.isReactorBlock(id)) {
            zones.removeReactorZone(event.getPos());
        }
    }

    /**
     * Отслеживаем поршни/эксплозивы: когда блок заменяется другим (например,
     * sticky piston увёл реактор), onRemove блока сам почистит зону — этот обработчик
     * нужен только для случаев, когда onRemove не вызывается (rare). Оставлен хуком.
     */
    @SubscribeEvent
    public static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var zones = HeatZoneManager.get(level);
        if (zones == null) return;
        // =================================================================================
        // TODO: Artem's Task — при взрыве удаляй зоны реакторов, чьи позиции попали в
        // event.getAffectedBlocks(). Метод уже получен — цикл for + removeReactorZone.
        // =================================================================================
    }

    private ReactorEventHandler() {}
}
