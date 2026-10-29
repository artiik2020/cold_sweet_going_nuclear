package com.yourname.myfirstmod.csgn.init;

import com.yourname.myfirstmod.csgn.Csgn;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация предметов. Чтобы блок можно было держать в руке и ставить,
 * у него должен быть BlockItem — «предмет-блок».
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Csgn.MODID);

    // Предмет для csgn:reactor_core
    public static final DeferredItem<BlockItem> REACTOR_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("reactor_core", ModBlocks.REACTOR_CORE);

    // Предмет для csgn:radiator
    public static final DeferredItem<BlockItem> RADIATOR_ITEM =
            ITEMS.registerSimpleBlockItem("radiator", ModBlocks.RADIATOR);

    // МЕХАНИКА №1: предмет для csgn:condenser (блок сброса счётчика паровой сети)
    public static final DeferredItem<BlockItem> CONDENSER_ITEM =
            ITEMS.registerSimpleBlockItem("condenser", ModBlocks.CONDENSER);

    // =====================================================================================
    // TODO: Artem's Optional Task - [Свой первый блок с нуля] (задача #10 в SAM.md)
    // Когда зарегистрируешь свой класс блока в ModBlocks — добавь ему предмет ОДНОЙ строкой:
    //
    // public static final DeferredItem<BlockItem> HOT_CASING_ITEM =
    //         ITEMS.registerSimpleBlockItem("hot_casing", ModBlocks.HOT_CASING);
    //
    // И ещё «простой предмет» без блока (уровень «разминка»):
    //
    // public static final DeferredItem<Item> URANIUM_ROD =
    //         ITEMS.registerSimpleItem("uranium_rod", new Item.Properties().stacksTo(16));
    //
    // Тебя ждут: текстура 16x16 в textures/item|block, model JSON, lang-запись.
    // Шаблон крафта уже лежит в data/csgn/recipe/reactor_core.json.
    // =====================================================================================
}
