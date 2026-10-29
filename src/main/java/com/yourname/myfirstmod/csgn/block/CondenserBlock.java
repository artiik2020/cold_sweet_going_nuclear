package com.yourname.myfirstmod.csgn.block;

import net.minecraft.world.level.block.Block;

/**
 * МЕХАНИКА №1: блок «Конденсатор» (csgn:condenser) — зарегистрирован и работает.
 *
 * Игрок ставит его каждые <=63 трубы паровой сети: PipeNetworkWalker при обходе
 * видит этот блок и СБРАСЫВАЕТ счётчик длины цепи (см. handler/PipeNetworkWalker.java).
 * Сам класс — «маркер»: вся логика разрыва живёт в обработчиках, поэтому блоку
 * достаточно быть обычным Block с корректными свойствами.
 */
public class CondenserBlock extends Block {

    public CondenserBlock(Properties properties) {
        super(properties);
    }

    // =================================================================================
    // TODO: Artem's Optional Task - [Ливень конденсата из Конденсатора] (задача #9 в SAM.md)
    // Идея: каждые N тиков конденсатор «выдаивает» немного пара из соседних труб
    // (SteamFluidHelper.drainSteam у capability соседа) и превращает его в каплю воды
    // частицами SPLASH + звук. Заготовка-хук уже есть в CondensationHandler:
    // метод condenseAroundCondensers(...) вызывается автоматически, тело — пустое.
    // Пока ты его не наполнил — мод работает как сейчас, ничего не ломается.
    // =================================================================================
}
