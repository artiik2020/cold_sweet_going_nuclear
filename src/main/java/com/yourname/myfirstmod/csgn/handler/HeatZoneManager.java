package com.yourname.myfirstmod.csgn.handler;

import com.yourname.myfirstmod.csgn.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ядро бриджа: хранилище всех тепловых ЗОН конкретного уровня (мира).
 *
 * Архитектура:
 *  - Один экземпляр на Level (ServerLevel): привязан через NeoForge Data Attachment,
 *    поэтому несколько измерений работают независимо.
 *  - Реакторы добавляются БЫСТРЫМ путём: onPlace/onRemove блока -> add/remove — O(1).
 *  - Трубы добавляются МЕДЛЕННЫМ путём: SteamPipeHeatHandler раз в N тиков
 *    пересобирает карту зон труб сканированием загруженных чанков.
 *
 * Всё происходит на игровом потоке (server tick), поэтому обычный HashMap безопасен.
 */
public class HeatZoneManager {

    /** Тип attachment'а. Регистрация — в ModAttachments.init(). */
    public static final AttachmentType<HeatZoneManager> ATTACHMENT =
            AttachmentType.builder(HeatZoneManager::new).build();

    /** pos реактора -> множитель интенсивности (1.0 обычно) */
    private final Map<BlockPos, Double> reactorZones = new HashMap<>();

    /** pos трубы с паром -> множитель интенсивности (1.0; радиатор греет сильнее) */
    private final Map<BlockPos, Double> steamPipeZones = new HashMap<>();

    /** Быстрый доступ к менеджеру уровня. На клиенте вернёт null — расчёты только на сервере. */
    @Nullable
    public static HeatZoneManager get(@Nullable Level level) {
        if (level == null || level.isClientSide()) return null;
        return level.getData(ATTACHMENT);
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

    /** Снимок ключей зон труб (для безопасного обхода в CondensationHandler). */
    public Set<BlockPos> pipeSnapshotKeys() { return Set.copyOf(steamPipeZones.keySet()); }

    /** Полный сброс зон труб — вызывается перед пересканированием. */
    public void clearSteamPipeZones() { steamPipeZones.clear(); }

    public void clearAll() { reactorZones.clear(); steamPipeZones.clear(); }

    private int totalSize() { return reactorZones.size() + steamPipeZones.size(); }

    public int reactorCount() { return reactorZones.size(); }
    public int pipeCount()    { return steamPipeZones.size(); }

    // ============================ ВЫЧИСЛЕНИЕ ТЕПЛА ============================

    /**
     * Суммарный «нагрев» в точке pos — главная формула мода.
     * Модель: линейное затухание от центра зоны к её краю:
     *   heat(pos) = Σ  multiplier_i * M_i * (1 - dist_i / radius_i)
     * Именно это число ColdSweatBridge превращает в градусы для игроков/мобов.
     */
    public double heatAt(BlockPos pos) {
        double heat = 0.0;

        if (Config.reactorEffectsEnabled) {
            double rr = Config.reactorHeatRadius;
            for (Map.Entry<BlockPos, Double> e : reactorZones.entrySet()) {
                double dSq = e.getKey().distSqr(pos);
                if (dSq <= rr * rr) {                       // сравнение квадратов — без Math.sqrt!
                    heat += e.getValue() * Config.reactorHeatMultiplier * (1.0 - Math.sqrt(dSq) / rr);
                }
            }
        }

        if (Config.steamPipeEffectsEnabled) {
            double pr = Config.steamPipeHeatRadius;
            for (Map.Entry<BlockPos, Double> e : steamPipeZones.entrySet()) {
                double dSq = e.getKey().distSqr(pos);
                if (dSq <= pr * pr) {
                    heat += e.getValue() * Config.steamPipeHeatMultiplier * (1.0 - Math.sqrt(dSq) / pr);
                }
            }
        }
        return heat;
    }

    /** Есть ли вообще активные источники (для раннего выхода в тикере). */
    public boolean hasAnySource() {
        return (!reactorZones.isEmpty() && Config.reactorEffectsEnabled)
                || (!steamPipeZones.isEmpty() && Config.steamPipeEffectsEnabled);
    }
}
