package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import com.yourname.myfirstmod.csgn.integration.GoingNuclearBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ядро бриджа: хранилище всех тепловых ЗОН конкретного ServerLevel.
 * Привязан к уровню через NeoForge Data Attachment (см. ModAttachments),
 * поэтому несколько измерений работают независимо и данные живут между тиками.
 *
 * ОПТИМИЗАЦИЯ (главное правило мода): НИКАКОГО сканирования загруженных чанков.
 *  - Реакторы добавляются БЫСТРЫМ путём: onPlace/onRemove блока -> add/remove — O(1).
 *  - Трубы попадают в кэш через события установки/ломки + редкую перепроверку
 *    ТОЛЬКО закэшированных позиций (SteamPipeHeatHandler).
 *
 * Всё происходит на игровом потоке (server tick), поэтому обычный HashMap безопасен.
 */
public class HeatZoneManager {

    /** Фабрика для AttachmentType.builder(...) — создаётся ВНУТРИ регистрации в ModAttachments. */
    public static HeatZoneManager create() {
        return new HeatZoneManager();
    }

    /** pos реактора -> базовый множитель интенсивности (1.0 обычно) */
    private final Map<BlockPos, Double> reactorZones = new HashMap<>();

    /** pos трубы с паром -> множитель интенсивности (1.0; радиатор греет сильнее) */
    private final Map<BlockPos, Double> steamPipeZones = new HashMap<>();

    /** МЕХАНИКА №2: динамический множитель реакторов, обновляется раз в интервал из GoingNuclearBridge. */
    private final Map<BlockPos, Double> dynamicReactorMult = new HashMap<>();

    /** Быстрый доступ к менеджеру уровня. На клиенте вернёт null — расчёты только на сервере. */
    @Nullable
    public static HeatZoneManager get(@Nullable Level level) {
        if (level == null || level.isClientSide()) return null;
        return level.getData(com.yourname.myfirstmod.csgn.init.ModAttachments.HEAT_ZONE_MANAGER.get());
    }

    // ============================ ИЗМЕНЕНИЕ ЗОН ============================

    public void addReactorZone(BlockPos pos) { addReactorZone(pos, 1.0); }

    public void addReactorZone(BlockPos pos, double multiplier) {
        if (totalSize() >= Config.maxTrackedSources) return; // защита от перегрузки
        reactorZones.put(pos.immutable(), multiplier);
    }

    public void removeReactorZone(BlockPos pos) {
        reactorZones.remove(pos);
        dynamicReactorMult.remove(pos); // при выносе реактора чистим и динамику
    }

    public void addSteamPipeZone(BlockPos pos) { addSteamPipeZone(pos, 1.0); }

    public void addSteamPipeZone(BlockPos pos, double multiplier) {
        if (totalSize() >= Config.maxTrackedSources) return;
        steamPipeZones.put(pos.immutable(), multiplier);
    }

    public void removeSteamPipeZone(BlockPos pos) { steamPipeZones.remove(pos); }

    /** Снимок ключей труб — безопасно обходить и удалять из карты во время цикла. */
    public Set<BlockPos> pipeSnapshotKeys() { return Set.copyOf(steamPipeZones.keySet()); }

    /** Снимок ключей реакторов. */
    public Set<BlockPos> reactorSnapshotKeys() { return Set.copyOf(reactorZones.keySet()); }

    public void clearAll() {
        reactorZones.clear();
        steamPipeZones.clear();
        dynamicReactorMult.clear();
    }

    private int totalSize() { return reactorZones.size() + steamPipeZones.size(); }

    public int reactorCount() { return reactorZones.size(); }
    public int pipeCount()    { return steamPipeZones.size(); }

    // ==================== МЕХАНИКА №2: ДИНАМИЧЕСКОЕ ТЕПЛО РЕАКТОРА ====================

    /**
     * Обновить «живые» множители реакторов, заглянув внутрь их BlockEntity через
     * GoingNuclearBridge (reflection). Вызывать НЕ каждый тик, а раз в
     * Config.reactorDynamicScanIntervalTicks (сделано в ReactorEventHandler).
     * Если Config.reactorDynamicHeatEnabled=false или мост ничего не нашёл —
     * используется статический множитель из конфига (старое поведение).
     */
    public void refreshDynamicReactorMultipliers(Level level) {
        for (Map.Entry<BlockPos, Double> e : reactorZones.entrySet()) {
            BlockPos pos = e.getKey();
            if (!level.isLoaded(pos)) continue; // точечная проверка загрузки чанка
            var be = level.getBlockEntity(pos);
            double activity = GoingNuclearBridge.readReactorActivity(be); // -1 если недоступно
            double base = e.getValue();
            if (activity < 0.0) {
                dynamicReactorMult.put(pos, base); // фолбэк: статический множитель
            } else {
                // Плавный градиент активности: heatMult = base * (min + (max-min)*activity)
                double scaled = base * (Config.reactorMinActivityScale
                        + (Config.reactorMaxActivityScale - Config.reactorMinActivityScale) * activity);
                dynamicReactorMult.put(pos, scaled);
            }
        }
    }

    /** Текущий эффективный множитель данного реактора (с учётом динамики). */
    private double effectiveReactorMult(BlockPos pos, double staticBase) {
        if (!Config.reactorDynamicHeatEnabled) return staticBase;
        Double dyn = dynamicReactorMult.get(pos);
        return (dyn != null) ? dyn : staticBase;
    }

    // ============================ ВЫЧИСЛЕНИЕ ТЕПЛА ============================

    /**
     * Суммарный «нагрев» в точке: heat = Σ mult_i * M_i * falloff(dist_i / radius_i).
     *
     * УЛУЧШЕННЫЙ ГРАДИЕНТ ЗАТУХАНИЯ (плавная кривая вместо линейной):
     *   t = dist / radius (0 в центре .. 1 на краю)
     *   falloff(t) = (1 - t)^2  — квадратичное затухание: у самого реактора жарит
     *   сильно, но к краю зоны спадает МЯГКО и без резкого «оброка» на границе.
     *   Линейная (1-t) версия давала заметный скачок ощущения температуры на подступах.
     * ИСПРАВЛЕНО: никаких AABB — сравнение по квадрату расстояния (distSqr).
     */
    public double heatAt(BlockPos pos) {
        double heat = 0.0;

        if (Config.reactorEffectsEnabled) {
            double rr = Config.reactorHeatRadius;
            double rrSq = rr * rr;
            for (Map.Entry<BlockPos, Double> e : reactorZones.entrySet()) {
                double dSq = e.getKey().distSqr(pos);
                if (dSq <= rrSq) {
                    double t = Math.sqrt(dSq) / rr;                 // нормированное расстояние 0..1
                    double falloff = (1.0 - t) * (1.0 - t);         // плавный квадратичный градиент
                    double mult = effectiveReactorMult(e.getKey(), e.getValue());
                    heat += mult * Config.reactorHeatMultiplier * falloff;
                }
            }
        }

        if (Config.steamPipeEffectsEnabled) {
            double pr = Config.steamPipeHeatRadius;
            double prSq = pr * pr;
            for (Map.Entry<BlockPos, Double> e : steamPipeZones.entrySet()) {
                double dSq = e.getKey().distSqr(pos);
                if (dSq <= prSq) {
                    double t = Math.sqrt(dSq) / pr;
                    double falloff = (1.0 - t) * (1.0 - t);         // тот же плавный градиент для труб
                    heat += e.getValue() * Config.steamPipeHeatMultiplier * falloff;
                }
            }
        }
        return heat;
    }

    /** Есть ли вообще активные источники тепла (для раннего выхода в тик-хендлере)? */
    public boolean hasAnySource() {
        return (!reactorZones.isEmpty() && Config.reactorEffectsEnabled)
                || (!steamPipeZones.isEmpty() && Config.steamPipeEffectsEnabled);
    }

    private HeatZoneManager() {}
}

