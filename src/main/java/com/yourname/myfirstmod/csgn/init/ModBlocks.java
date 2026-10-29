package com.yourname.myfirstmod.csgn.init;

import com.yourname.myfirstmod.csgn.Csgn;
import com.yourname.myfirstmod.csgn.block.CondenserBlock;
import com.yourname.myfirstmod.csgn.block.RadiatorBlock;
import com.yourname.myfirstmod.csgn.block.ReactorCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация ВСЕХ блоков мода через DeferredRegister.
 * NeoForge сам вызовет RegisterEvent и зарегистрирует всё в игру.
 */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Csgn.MODID);

    /** Наш тестовый «реактор»: ID csgn:reactor_core уже в whitelist конфига. */
    public static final DeferredBlock<ReactorCoreBlock> REACTOR_CORE =
            BLOCKS.register("reactor_core",
                    () -> new ReactorCoreBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(5000000.0f, 1200.0f)
                            .sound(SoundType.METAL)));

    /** Радиатор: ставится рядом с трубой Create — пар гарантированно конденсируется в нём. */
    public static final DeferredBlock<RadiatorBlock> RADIATOR =
            BLOCKS.register("radiator",
                    () -> new RadiatorBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(4.0f)
                            .sound(SoundType.COPPER)));

    /** МЕХАНИКА №1: Конденсатор — сбрасывает счётчик длины паровой сети (ставь каждые <=63 трубы). */
    public static final DeferredBlock<CondenserBlock> CONDENSER =
            BLOCKS.register("condenser",
                    () -> new CondenserBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.COPPER)));

    /** Помогает обработчикам определить «это наш радиатор?». */
    public static boolean isRadiator(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(RADIATOR.get());
    }

    /** «Это наш конденсатор?» — спрашивает PipeNetworkWalker во время BFS. */
    public static boolean isCondenser(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(CONDENSER.get());
    }

    // =====================================================================================
    // TODO: Artem's Optional Task - [Медный радиатор] (задача #5 в SAM.md)
    // Раскомментируй блок ниже, когда создашь класс CopperRadiatorBlock (он лежит заготовкой
    // в block/CopperRadiatorBlock.java), добавь registerSimpleBlockItem() в ModItems и
    // JSON-и (blockstates/models/lang). Всё компилируется и без этого — код ниже ЗАКОММЕНТИРОВАН.
    //
    // public static final DeferredBlock<CopperRadiatorBlock> COPPER_RADIATOR =
    //         BLOCKS.register("copper_radiator",
    //                 () -> new CopperRadiatorBlock(BlockBehaviour.Properties.of()
    //                         .mapColor(MapColor.COLOR_ORANGE)
    //                         .strength(4.0f)
    //                         .sound(SoundType.COPPER)));
    //
    // public static boolean isCopperRadiator(net.minecraft.world.level.block.state.BlockState state) {
    //     return state.is(COPPER_RADIATOR.get());
    // }
    // =====================================================================================

    // =====================================================================================
    // TODO: Artem's Optional Task - [Свой декоративный блок] (задача #10 в SAM.md)
    // Попробуй повторить регистрацию КОНДЕНСАТОРА сам, «на глаз», не подглядывая:
    //   1) создай класс (например block/HotCasingBlock extends Block с конструктором Properties);
    //   2) скопируй блок CONDENSER выше, поменяй имя/свойства;
    //   3) добавь предмет в ModItems одной строкой registerSimpleBlockItem(...);
    //   4) lang-запись + model JSON (шаблон — en_us.json и data/csgn/recipe/*).
    // Это самый частый рабочий процесс мододела — набей руку на четвёртом блоке!
    // =====================================================================================
}
