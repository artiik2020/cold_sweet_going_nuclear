package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.init.ModBlocks;
import com.yourname.myfirstmod.csgn.integration.SteamFluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Конденсация пара: раз в Config.condenseIntervalTicks тиков проверяем ТОЛЬКО
 * закэшированные позиции труб и «вытягиваем» (drain) пар у тех блоков, которые
 * стыкуются с нашим радиатором (csgn:radiator). Выжатый пар превращается в воду
 * согласно конфигу CONDENSE_TURNS_TO_WATER.
 *
 * Ключевой принцип NeoForge capabilities: сначала SIMULATE (безопасная проверка),
 * потом EXECUTE (реальное изменение). Никогда не мутируем чужой контейнер вслепую.
 *
 * ИСПРАВЛЕНО:
 *  - НЕТ аннотации @EventBusSubscriber — регистрация вручную в конструкторе Csgn;
 *  - server.getTickCount() -> level.getGameTime();
 *  - событие привязано к уровню (LevelTickEvent.Post) — не перебираем все измерения сразу.
 */
public final class CondensationHandler {

    @SubscribeEvent
    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (!Config.condensationEnabled) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // ИСПРАВЛЕНО: getTickCount() нет в 1.21.1 — используем level.getGameTime()
        if (level.getGameTime() % Math.max(1, Config.condenseIntervalTicks) != 0) return;

        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones == null || zones.pipeCount() == 0) return;

        // Снимок ключей, чтобы безопасно удалять из карты во время обхода
        for (BlockPos pipePos : zones.pipeSnapshotKeys()) {
            // ИСПРАВЛЕНО: точечная проверка загрузки вместо скана чанков
            if (!level.isLoaded(pipePos)) continue;

            // Пар конденсируем только если труба стыкуется с нашим РАДИАТОРОМ
            // (детерминированное поведение — игрок сам выбирает место).
            BlockPos radiatorPos = findAdjacentRadiator(level, pipePos);
            if (radiatorPos == null) continue;

            condenseInto(level, pipePos, radiatorPos);
        }
    }

    /**
     * Заглушка для задачи #5 (см. SAM.md). Сейчас НЕ используется и всегда возвращает 1 —
     * поведение мода НЕ меняется, даже если ты не будешь трогать эту задачу.
     * Реализуй: проверяй через ModBlocks.isCopperRadiator(state) и возвращай 2.
     */
    @SuppressWarnings("unused")
    private int copperMultiplier(ServerLevel level, BlockPos radiatorPos) {
        // TODO: Artem's Optional Task - [Медный радиатор конденсирует быстрее] — наполни это тело,
        // а затем ПРИМЕНИ результат в condenseInto(): переделай drainSteam(..., Config.condenseAmountMb, ...)
        // на drainSteam(..., Config.condenseAmountMb * copperMultiplier(level, radiatorPos), ...).
        //
        // Логика по шагам:
        //   1) BlockState state = level.getBlockState(radiatorPos);
        //   2) if (ModBlocks.isCopperRadiator(state)) return 2;   // медь кондит в 2 раза жаднее
        //   3) return 1;                                           // обычный радиатор / прочее
        //
        // ПОДВОДНЫЕ КАМНИ JAVA:
        //  - метод обязан вернуть int на ВСЕХ ветках — компилятор не простит «провалившуюся»
        //    if-без-return (в Python тут был бы None, в Java — ошибка compile);
        //  - isCopperRadiator() появится только когда зарегистрируешь блок (задача #5, шаг 1).
        //    Чтобы код компилировался СЕЙЧАС, оставь заглушку или сравни с существующим блоком:
        //    if (state.is(ModBlocks.RADIATOR.get())) return 1;
        return 1;
    }

    /** Запрос capability FluidHandler для одной позиции. null — если блок не отдаёт жидкости. */
    private IFluidHandler fluidHandlerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
    }

    /** Ищет блок-радиатор csgn прямо над/под/сбоку от трубы (6 сторон). */
    private BlockPos findAdjacentRadiator(ServerLevel level, BlockPos pipePos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pipePos.relative(dir);
            if (!level.isLoaded(neighbor)) continue;
            if (ModBlocks.isRadiator(level.getBlockState(neighbor))) {
                return neighbor;
            }
        }
        return null;
    }

    /** Собственно конвертация: drainSteam(SIMULATE) -> drainSteam(EXECUTE) -> вода. */
    private void condenseInto(ServerLevel level, BlockPos pipePos, BlockPos radiatorPos) {
        IFluidHandler handler = fluidHandlerAt(level, pipePos);
        if (handler == null) return;

        // 1) Симуляция: сколько реально пара труба готова отдать?
        FluidStack simulated = SteamFluidHelper.drainSteam(handler, Config.condenseAmountMb, false);
        if (simulated.isEmpty()) return;

        // =================================================================================
        // TODO: Artem's Task — условие «открытого конца»: добавь сюда проверку, что труба
        // смотрит открытым соединением в воздух (level.isEmptyBlock на стороне коннектора),
        // и тогда тоже конденсируй пар. Подсказка: у Create-труб приватные коннекторы,
        // поэтому проще начать с работающего варианта «рядом с радиатором».
        // =================================================================================

        // 2) Execute: забираем пар из трубы НАВСЕГДА
        FluidStack drained = SteamFluidHelper.drainSteam(handler, simulated.getAmount(), true);
        if (drained.isEmpty()) return;

        // 3) Куда деть воду?
        if (Config.condenseTurnsToWater) {
            // Сначала пробуем залить воду обратно в тот же танк трубы (аккуратно, без луж):
            FluidStack water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER,
                    drained.getAmount());
            int filled = handler.fill(water, IFluidHandler.FluidAction.EXECUTE);
            if (filled == 0) {
                // Танк полон чем-то ещё или не принимает — ставим воду в воздух над радиатором,
                // если там пусто; иначе просто звуковой эффект «шипения».
                BlockPos waterPos = radiatorPos.above();
                if (level.isEmptyBlock(waterPos)) {
                    level.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
                } else {
                    level.playSound(null, radiatorPos, SoundEvents.LAVA_EXTINGUISH,
                            SoundSource.BLOCKS, 0.5f, 1.4f);
                }
            }
        }
        // В обоих случаях снимаем тепловую зону этой трубы до следующего скана:
        HeatZoneManager zones = HeatZoneManager.get(level);
        if (zones != null) zones.removeSteamPipeZone(pipePos);
    }
}
