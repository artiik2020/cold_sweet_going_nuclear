package com.yourname.myfirstmod.csgn.init;

import com.yourname.myfirstmod.csgn.Csgn;
import com.yourname.myfirstmod.csgn.block.ReactorCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Здесь регистрируются ВСЕ блоки мода.
 *
 * Как это работает:
 *  - DeferredRegister — «отложенный» реестр: мы записываем сюда блоки, а NeoForge
 *    зарегистрирует их в игре автоматически в нужный момент загрузки (RegisterEvent).
 *  - register(...) принимает Supplier (ленивое создание): объект блока создастся
 *    только тогда, когда игра будет готова его принять.
 */
public class ModBlocks {

    // Реестр блоков для нашего мода (все блоки получат префикс "csgn:")
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Csgn.MODID);

    /**
     * Блок-сердцевина реактора. Именно он излучает тепло в радиусе из конфига.
     * ID в игре: csgn:reactor_core
     */
    public static final DeferredBlock<ReactorCoreBlock> REACTOR_CORE =
            BLOCKS.register("reactor_core",
                    () -> new ReactorCoreBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)   // цвет блока на карте
                            .strength(50.0f, 1200.0f)          // очень прочный (как якорь мира)
                            .sound(SoundType.METAL)            // звук при установке/ломке
                            .requiresCorrectToolForDrops()));                 // ломается только инструментом

    // =====================================================================================
    // TODO: Artem's Task — Зарегистрируй здесь свой первый блок!
    // Например, простой декоративный блок «горячий корпус»:
    //
    // public static final DeferredBlock<Block> HOT_CASING =
    //         BLOCKS.register("hot_casing",
    //                 () -> new Block(BlockBehaviour.Properties.of()
    //                         .mapColor(MapColor.METAL)
    //                         .strength(3.0f)));
    //
    // После этого не забудь:
    //  1) зарегистрировать BlockItem в ModItems.java
    //  2) добавить модель src/main/resources/assets/csgn/blockstates/hot_casing.json
    //  3) добавить текстуру assets/csgn/textures/block/hot_casing.png
    //  4) добавить название в assets/csgn/lang/en_us.json и ru_ru.json
    // =====================================================================================
}
