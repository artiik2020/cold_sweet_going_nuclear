package com.yourname.myfirstmod.csgn.init;

import com.yourname.myfirstmod.csgn.Csgn;
import com.yourname.myfirstmod.csgn.handler.HeatZoneManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Регистрация Data Attachments. Attachment — способ NeoForge «пришить» свои данные
 * к чужому объекту (в нашем случае — к Level), не трогая NBT вручную.
 *
 * ИСПРАВЛЕНО (критично): объект AttachmentType создаётся ВНУТРИ лямбды register(...),
 * а НЕ в статическом поле класса. Статическая инициализация до старта реестра —
 * частая причина крашей/рассинхронизации ID.
 */
public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Csgn.MODID);

    /** Менеджер тепловых зон — один экземпляр на каждый ServerLevel. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HeatZoneManager>>
            HEAT_ZONE_MANAGER = ATTACHMENT_TYPES.register("heat_zones",
                    () -> AttachmentType.builder(HeatZoneManager::create).build());

    /** Факультативный хелпер (в Csgn реестр регистрируется напрямую). */
    public static void init(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    private ModAttachments() {}
}
