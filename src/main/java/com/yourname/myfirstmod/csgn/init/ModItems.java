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

    // =====================================================================================
    // TODO: Artem's Task — Создай свой первый предмет! Например «стержень с ураном»:
    //
    // public static final DeferredItem<Item> URANIUM_ROD =
    //         ITEMS.registerSimpleItem("uranium_rod", new Item.Properties().stacksTo(16));
    //
    // Тебя ждут:
    //  1) картинка assets/csgn/textures/item/uranium_rod.png (16x16 PNG);
    //  2) название "item.csgn.uranium_rod" в lang-файлах;
    //  3) простой крафт data/csgn/recipe/uranium_rod.json — шаблон уже лежит
    //     в data/csgn/recipe/reactor_core.json, скопируй и поменяй items.
    // =====================================================================================
}
