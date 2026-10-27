package com.yourname.myfirstmod.csgn.init;

import com.yourname.myfirstmod.csgn.Csgn;
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
                            .strength(50.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresTool()));

    /** Радиатор: ставится рядом с трубой Create — пар гарантированно конденсируется в нём. */
    public static final DeferredBlock<RadiatorBlock> RADIATOR =
            BLOCKS.register("radiator",
                    () -> new RadiatorBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(4.0f)
                            .sound(SoundType.COPPER)));

    /** Помогает обработчикам определить «это наш радиатор?». */
    public static boolean isRadiator(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(RADIATOR.get());
    }

    // =====================================================================================
    // TODO: Artem's Task — Зарегистрируй здесь свой блок! Например «горячий корпус»:
    //
    // public static final DeferredBlock<Block> HOT_CASING =
    //         BLOCKS.register("hot_casing",
    //                 () -> new Block(BlockBehaviour.Properties.of()
    //                         .mapColor(MapColor.METAL).strength(3.0f)));
    //
    // Не забудь: BlockItem в ModItems, blockstate+model JSON, текстура, lang-запись.
    // А ещё можно добавить ID нового блока в reactor.blockWhitelist конфига —
    // и он сразу начнёт греться без единой строчки кода!
    // =====================================================================================
}
