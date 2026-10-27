package com.yourname.myfirstmod.csgn.block;

import net.minecraft.world.level.block.Block;

/**
 * Блок-радиатор (csgn:radiator): ставишь его вплотную к трубе Create с паром —
 * CondensationHandler начинает вытягивать из трубы пар и превращать его в воду,
 * а SteamPipeHeatHandler даёт такой трубе двойной множитель тепла.
 *
 * Вся логика уже живёт в handler/CondensationHandler.java и handler/SteamPipeHeatHandler.java —
 * этот класс пока просто «маркер», чтобы игра знала блок.
 */
public class RadiatorBlock extends Block {

    public RadiatorBlock(Properties properties) {
        super(properties);
    }

    // =================================================================================
    // TODO: Artem's Task — сделай радиатор красивым:
    //  1) Добавь ему анимацию частиц пара (как пример — см. ReactorCoreBlock.animateTick);
    //  2) Придумай и добавь крафт в data/csgn/recipe/radiator.json (железо + медь);
    //  3) Добавь название "block.csgn.radiator": "Steam Radiator" в lang-файлы.
    // =================================================================================
}
