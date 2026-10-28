package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
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

    /** pos реактора -> множитель интенсивности (1.0 обычно) */
    private final Map<BlockPos, Double> reactorZones = new HashMap<>();

    /** pos трубы с паром -> множитель интенсивности (1.0; радиатор греет сильнее) */
    private final Map<BlockPos, Double> steamPipeZones = new HashMap<>();

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

    public void removeReactorZone(BlockPos pos) { reactorZones.remove(pos); }

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

    public void clearAll() { reactorZones.clear(); steamPipeZones.clear(); }

    private int totalSize() { return reactorZones.size() + steamPipeZones.size(); }

    public int reactorCount() { return reactorZones.size(); }
    public int pipeCount()    { return steamPipeZones.size(); }

    // ============================ ВЫЧИСЛЕНИЕ ТЕПЛА ============================

    /**
     * Суммарный «нагрев» в точке: heat = Σ mult_i * M_i * (1 - dist_i / radius_i).
     * Тепло линейно убывает от центра зоны к её краю.
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
                    heat += e.getValue() * Config.reactorHeatMultiplier * (1.0 - Math.sqrt(dSq) / rr);
                }
            }
        }

        if (Config.steamPipeEffectsEnabled) {
            double pr = Config.steamPipeHeatRadius;
            double prSq = pr * pr;
            for (Map.Entry<BlockPos, Double> e : steamPipeZones.entrySet()) {
                double dSq = e.getKey().distSqr(pos);
                if (dSq <= prSq) {
                    heat += e.getValue() * Config.steamPipeHeatMultiplier * (1.0 - Math.sqrt(dSq) / pr);
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
