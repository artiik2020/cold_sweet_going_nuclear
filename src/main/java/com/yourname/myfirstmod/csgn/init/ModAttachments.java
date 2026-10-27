package com.yourname.myfirstmod.csgn.init;

import com.yourname.myfirstmod.csgn.Csgn;
import com.yourname.myfirstmod.csgn.handler.HeatZoneManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Регистрация Data Attachments. Attachment — способ NeoForge «пришить» свои данные
 * к чужому объекту (в нашем случае — к Level), не ломая его и не трогая NBT вручную.
 */
public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Csgn.MODID);

    // Тот самый менеджер тепловых зон — один на каждый ServerLevel:
    public static final net.neoforged.neoforge.registries.DeferredHolder<AttachmentType<?>, AttachmentType<HeatZoneManager>>
            HEAT_ZONE_MANAGER = ATTACHMENT_TYPES.register("heat_zones", () -> HeatZoneManager.ATTACHMENT);

    /** Вызывается из конструктора Csgn до register всех остального. */
    public static void init(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
