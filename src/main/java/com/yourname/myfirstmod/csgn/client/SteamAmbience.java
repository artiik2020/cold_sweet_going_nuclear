package com.yourname.myfirstmod.csgn.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * ЗАГОТОВКА для опциональных визуальных/звуковых фич (задачи #1, #2 в SAM.md).
 *
 * Класс намеренно ПУСТО: он компилируется, нигде не вызывается и НИКАК
 * не влияет на работу мода. Твоя задача (если захочешь) — наполнить методы ниже
 * и вызвать applyTick(...) из клиентского тик-события (подсказка внутри).
 */
public final class SteamAmbience {

    /**
     * Вызывать на КЛИЕНТЕ раз в несколько тиков для позиции трубы с паром.
     * Сейчас — пустой метод-заглушка (safe stub): ничего не делает, краша нет.
     *
     * TODO: Artem's Optional Task - [Визуал: частицы пара над горячими трубами] (задача #1 в SAM.md)
     * Что делать:
     *   1) level.addParticle(ParticleTypes.CLOUD, x+0.5+rnd, y+1.0, z+0.5+rnd, 0, 0.03, 0);
     *      НЕ ParticleType.CLOUD — константы живут в net.minecraft.core.particles.ParticleTypes!
     *   2) частоту частиц привяжи к level.getGameTime() (не к gameTime-переменной!).
     *   3) регистрируй вызов из клиентского события LevelTickEvent.Client (1.21.1),
     *      подписавшись через NeoForge.EVENT_BUS.register(new SteamAmbience())... но
     *      ДЕЛАЙ ЭТО ТОЛЬКО когда метод будет готов — иначе лишний код в Csgn не нужен.
     */
    public static void applyTick(Level level, BlockPos steamPos) {
        // TODO: Artem's Optional Task - [Визуал: частицы пара над горячими трубами] — тело метода.
        // ПОДВОДНЫЕ КАМНИ JAVA:
        //  - level здесь может быть и клиентским, и серверным — первым делом проверь
        //    if (!level.isClientSide()) return;  (частицы живут только на клиенте);
        //  - addParticle(Options, double x, double y, double z, double vx, vy, vz) — ровно
        //    ШЕСТЬ double после options, как у AABB; опечатка в количестве = ошибка компиляции.
    }

    /**
     * Заглушка для звука шипения пара (задача #2 в SAM.md). Тоже пуста и безопасна.
     *
     * TODO: Artem's Optional Task - [Звук: шипение пара рядом с горячей трубой] (задача #2 в SAM.md)
     * Что делать:
     *   1) Ванильный кандидат: SoundEvents.AMBIENT_CAVE ... или свой JSON-звук +
     *      SoundEvent.createVariableRangeEvent(ResourceLocation).
     *   2) Громкость считай от дистанции игрока до трубы: volume = clamp(1 - dist/radius, 0..1).
     *   3) Играй НА КЛИЕНТЕ: level.playLocalSound(pos, sound, SoundSource.BLOCKS, vol, pitch, false)
     *      либо отправляй пакет — см. подсказки в SAM.md.
     */
    public static void playHiss(Level level, BlockPos steamPos, double playerDist) {
        // TODO: Artem's Optional Task - [Звук: шипение пара рядом с горячей трубой] — тело метода.
        // ПОДВОДНЫЙ КАМЕНЬ: Java статически типизирована — level нельзя передать null «просто так»,
        // если сигнатура этого не предусматривает; проверяй level.isLoaded(steamPos) перед звуком.
    }

    private SteamAmbience() {}
}
