package com.yourname.myfirstmod.csgn.init;

import com.yourname.myfirstmod.csgn.Csgn;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Здесь регистрируются все предметы мода.
 *
 * Важно: чтобы блок можно было поставить из руки, у него должен быть BlockItem —
 * «предмет-блок». registerSimpleBlockItem привязывает предмет к уже зарегистрированному блоку.
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Csgn.MODID);

    // Предмет для блока-сердцевины реактора (ID: csgn:reactor_core)
    public static final DeferredItem<BlockItem> REACTOR_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("reactor_core", ModBlocks.REACTOR_CORE);

    // =====================================================================================
    // TODO: Artem's Task — Создай свой первый предмет!
    // Пример: предмет «стержень с ураном» (пока просто обычный предмет без логики):
    //
    // public static final DeferredItem<Item> URANIUM_ROD =
    //         ITEMS.registerSimpleItem("uranium_rod", new Item.Properties().stacksTo(16));
    //
    // Тебя ждут:
    //  1) модель  assets/csgn/items/uranium_rod.json  (см. пример у existing items)
    //  2) картинка assets/csgn/textures/item/uranium_rod.png (16x16 PNG)
    //  3) название в lang-файлах: "item.csgn.uranium_rod": "Uranium Rod"
    // =====================================================================================
}
