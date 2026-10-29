package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.init.ModBlocks;
import com.yourname.myfirstmod.csgn.integration.SteamFluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * МЕХАНИКА №1: «Лопающиеся трубы» — расчёт длины непрерывной цепи труб с паром.
 *
 * Алгоритм: BFS (поиск в ширину) по графу блоков, начиная от источника пара
 * (позиции из нашего кэша HeatZoneManager). Правила обхода:
 *   - узел сети = блок, у которого FluidHandler capability содержит пар;
 *   - КОНДЕНСАТОР (csgn:condenser) СБРАСЫВАЕТ счётчик: он сам НЕ считается трубой,
 *     но через него сеть продолжается с нулевой глубиной (как «разрыв» в цепи);
 *   - если глубина > pipeChainLimit — это и есть «последняя труба», она лопнет
 *     (обработает PipeBurstHandler).
 *
 * ОПТИМИЗАЦИЯ (главное правило мода): НИКАКОГО скана чанков. Обход стартует
 * ТОЛЬКО с закэшированных позиций, помеченных как dirty; visited-множество не
 * даёт обойти одну цепь дважды за проход.
 */
public final class PipeNetworkWalker {

    /** pos -> длина цепи ДО этой трубы (для сообщения о взрыве). */
    public record BurstTarget(BlockPos pos, int chainLength) {}

    /** Позиции, с которых нужно начать обход после ближайших изменений сети. */
    private static final Set<BlockPos> DIRTY_SOURCES = new HashSet<>();

    /** Помечать источник как «пересчитать» (вызывается из SteamPipeHeatHandler). */
    public static void markDirty(BlockPos sourcePos) {
        if (DIRTY_SOURCES.size() < 8192) { // защита от разрастания при спаме событий
            DIRTY_SOURCES.add(sourcePos.immutable());
        }
    }

    public static void unmarkDirty(BlockPos sourcePos) {
        DIRTY_SOURCES.remove(sourcePos);
    }

    /**
     * Главный проход: считает глубину каждой цепи и возвращает трубы, которые
     * ПРЕВЫСИЛИ лимит давления и должны лопнуть. Вызывать на серверном уровне,
     * не чаще раза в Config.pipeScanIntervalTicks тиков (см. PipeBurstHandler).
     */
    public static List<BurstTarget> scanAndCollect(ServerLevel level) {
        List<BurstTarget> overpressured = new ArrayList<>();
        if (!Config.pipeBurstEnabled || DIRTY_SOURCES.isEmpty()) return overpressured;

        int limit = Math.max(1, Config.pipeChainLimit);
        int maxVisited = Math.max(64, Config.pipeMaxVisitedPerScan);

        // Снимок источников: во время обхода события могут досыпать новые позиции
        List<BlockPos> sources = new ArrayList<>(DIRTY_SOURCES);
        // Самые дальние от «края» источники проверим первыми (эвристика: сортировка по X)
        sources.sort(Comparator.comparingLong(BlockPos::asLong));

        Set<BlockPos> visitedGlobal = new HashSet<>(); // одна цепь не обходится дважды за проход

        for (BlockPos start : sources) {
            if (visitedGlobal.contains(start)) continue;       // уже обработали в составе другой цепи
            if (!level.isLoaded(start)) { DIRTY_SOURCES.remove(start); continue; } // чанк выгрузился
            if (!isSteamNode(level, start)) { DIRTY_SOURCES.remove(start); continue; } // пар ушёл

            bfsFrom(level, start, limit, maxVisited, visitedGlobal, overpressured);
        }
        return overpressured;
    }

    /** Классический BFS с очередью (ArrayDeque вместо устаревшего Stack/LinkedList). */
    private static void bfsFrom(ServerLevel level, BlockPos start, int limit, int maxVisited,
                                Set<BlockPos> visitedGlobal, List<BurstTarget> out) {
        Map<BlockPos, Integer> depthOf = new HashMap<>(); // аналог dict в Python, но типизированный
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);
        depthOf.put(start, 0);
        visitedGlobal.add(start);
        int visitedCount = 0;

        while (!queue.isEmpty() && visitedCount < maxVisited) {
            BlockPos current = queue.poll();          // null никогда: цикл guarded !isEmpty()
            int depth = depthOf.get(current);
            visitedCount++;

            if (depth > limit) {
                // Это и есть «последняя труба» перенапряжённой сети — кандидат на разрыв!
                out.add(new BurstTarget(current, depth));
                continue; // дальше из лопнувшей трубы сеть не растёт
            }

            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (depthOf.containsKey(next)) continue;          // уже в очереди/обойдена
                if (!level.isLoaded(next)) continue;              // точечная проверка загрузки чанка

                BlockState nextState = level.getBlockState(next);

                // === КОНДЕНСАТОР сбрасывает счётчик ===
                if (ModBlocks.isCondenser(nextState)) {
                    // Сам конденсатор — НЕ труба: он никогда не «лопнет», но сеть через него
                    // продолжается с НУЛЕВОЙ глубиной. Его трубы-соседи кладём в очередь с depth 0.
                    // ВАЖНО: visitedGlobal НЕ расширяем этим обходом — иначе вторая start-точка
                    // той же сети была бы ошибочно пропущена (см. цикл for в scanAndCollect).
                    depthOf.putIfAbsent(next, -1);   // помечаем посещённым, чтобы не зациклиться
                    for (Direction sub : Direction.values()) {
                        BlockPos after = next.relative(sub);
                        if (depthOf.containsKey(after)) continue;
                        if (!level.isLoaded(after)) continue;
                        if (isSteamNode(level, after)) {
                            depthOf.put(after, 0);   // СБРОС: после конденсатора отсчёт начинается заново
                            queue.add(after);
                        }
                    }
                    continue;
                }

                // === Обычная труба/танк с паром? ===
                if (isSteamNode(level, next)) {
                    depthOf.put(next, depth + 1);
                    queue.add(next);
                }
            }
        }
    }

    /**
     * Является ли позиция «узлом паровой сети»: блок отдаёт FluidHandler capability,
     * и хотя бы в одном танке есть жидкость из steam-whitelist конфига.
     * Capability запрашивается ТОЧЕЧНО для одной позиции (никаких сканов!).
     */
    public static boolean isSteamNode(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos); // getCapability сам вернёт пустоту, если BE нет
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
        return SteamFluidHelper.containsSteam(handler);
    }

    /** Готово: источник обработан, снимаем пометку (труба цела — считать заново только при изменениях). */
    public static void clearSources() {
        DIRTY_SOURCES.clear();
    }

    public static int dirtyCount() {
        return DIRTY_SOURCES.size();
    }

    private PipeNetworkWalker() {}
}
