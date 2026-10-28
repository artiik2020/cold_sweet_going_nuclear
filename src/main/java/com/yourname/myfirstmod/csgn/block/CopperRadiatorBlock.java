package com.yourname.myfirstmod.csgn.block;

import net.minecraft.world.level.block.Block;

/**
 * ЗАГОТОВКА блока «Медный радиатор» (csgn:copper_radiator) — задача #5 в SAM.md.
 *
 * ВАЖНО: этот класс сейчас НИГДЕ не зарегистрирован и НЕ влияет на игру.
 * Мод работает ровно так же, даже если ты никогда его не тронешь.
 * Чтобы фича заработала, раскомментируй регистрацию в init/ModBlocks.java
 * (ищи "Artem's Optional Task - [Медный радиатор]") и добавь предмет + JSON-и.
 */
public class CopperRadiatorBlock extends Block {

    public CopperRadiatorBlock(Properties properties) {
        super(properties);
    }

    // =================================================================================
    // TODO: Artem's Optional Task - [Медный радиатор конденсирует быстрее] (задача #5 в SAM.md)
    // Идеи для тела класса (всё опционально):
    //  1) Переопредели animateTick(...) и сыпь частицы CLOUD — медь горячее «шипит»;
    //  2) Придумай крафт: copper_ingot + iron_ingot (data/csgn/recipe/copper_radiator.json);
    //  3) Добавь lang-запись "block.csgn.copper_radiator": "Copper Steam Radiator".
    //
    // ПОДВОДНЫЙ КАМЕНЬ JAVA: конструктор обязан вызвать super(properties) ПЕРВЫМ делом —
    // в Python __init__ можно делать что угодно, здесь до super() запрещено обращаться к this.
    // =================================================================================
}
