# CSGN Bridge — Create × Going Nuclear × Cold Sweat (NeoForge 1.21.1)

Каркас мода-интегратора: НЕ создаём реакторы/трубы/температуру с нуля — связываем API существующих модов.

## Структура

```
src/main/java/com/yourname/myfirstmod/csgn/
├── Csgn.java                         # @Mod bootstrap, регистрация конфигов и реестров
├── Config.java                       # ModConfigSpec: радиусы, множители, whitelist ID блоков
├── block/
│   ├── ReactorCoreBlock.java         # тестовый блок-«реактор» (зона тепла через onPlace/onRemove)
│   └── RadiatorBlock.java            # маркер-блок радиатора (TODO задачи для новичка)
├── handler/
│   ├── HeatZoneManager.java          # хранилище тепловых зон на каждый ServerLevel (Data Attachment)
│   ├── HeatTickHandler.java          # ServerTickEvent.Post -> расчёт heat(pos) -> ColdSweatBridge
│   ├── SteamPipeHeatHandler.java     # скан загруженных чанков + FluidHandler.BLOCK capability
│   ├── CondensationHandler.java      # drain(SIMULATE)->drain(EXECUTE)->вода в мир
│   └── ReactorEventHandler.java      # BlockEvent.Place/Break -> whitelist реакторов из конфига
├── init/
│   ├── ModBlocks.java  ModItems.java # DeferredRegister блоков/предметов
│   └── ModAttachments.java           # регистрация AttachmentType для HeatZoneManager
└── integration/
    ├── ColdSweatBridge.java          # reflection-мост к Temperature API Cold Sweat (soft dependency)
    └── SteamFluidHelper.java         # containsSteam()/drainSteam() через IFluidHandler без классов Create
```

## Как подключить зависимости (build.gradle)

Репозитории уже добавлены: CurseMaven (`https://api.curse.tools/v1/curse/`) и Modrinth Maven (`https://api.modrinth.com/maven`).

1. **Create** (Modrinth): `runtimeOnly "maven.modrinth:create:6.0.9"` — версию актуализируй на modrinth.com/projects/create.
2. **Cold Sweat** (CurseMaven): на странице файлов CurseForge возьми числовые PROJECT_ID и FILE_ID и раскомментируй строку `runtimeOnly "curse.maven:cold-sweat-XXXXXX:YYYYYYY"`.
3. **Going Nuclear**: если jar под NeoForge 1.21 недоступен на maven — положи его в `./libs/` (gradle подхватывает `fileTree(dir:'libs')`).
4. После изменений: `./gradlew --refresh-dependencies runClient`.

Все три зависимости объявлены в `neoforge.mods.toml` как `type="optional"` — бридж работает даже без них (пишет нагрев в лог).

## Связка API → Capability → Event

1. **API (идентификаторы вместо классов).** Мы не компилируемся против Create/GN/ColdSweat. Реакторы GN определяются по `ResourceLocation` блока через whitelist конфига; пар Create — по ID жидкости `create:steam`.
2. **Capability.** `level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, context)` возвращает `IFluidHandler` для любой трубы Create (Create сам экспортирует его на `RegisterCapabilitiesEvent`). `null` = блок не отдаёт жидкости — это нормальный ответ, а не ошибка, поэтому крашей нет. Проверка содержимого (`getFluidInTank`) и дренаж идут только через интерфейс.
3. **Events.** `@EventBusSubscriber`-классы слушают `ServerTickEvent.Post` (тик-машина: скан труб раз в N тиков, применение тепла раз в M тиков), `BlockEvent.EntityPlaceEvent/BreakEvent` (мгновенный учёт реакторов) и `ExplosionEvent.Detonate` (хук для TODO). Тепло уходит в мир через `ColdSweatBridge.addTemperature(entity, deltaC)` — reflection-вызов `Temperature.addChange/change`, с graceful fallback на лог.

Поток данных: `блок поставлен/чанк просканирован → HeatZoneManager (зоны+множители) → heatAt(playerPos) линейно затухает от центра зоны → °C за интервал → Cold Sweat`.

## Конфиг (run/config/csgn-common.toml)

| Ключ | По умолчанию | Смысл |
|---|---|---|
| reactor.heatRadius | 100 | радиус зоны реактора |
| reactor.heatMultiplier | 4.0 | накал в центре зоны |
| reactor.blockWhitelist | ["csgn:reactor_core"] | ID блоков-реакторов (сюда добавляй блоки Going Nuclear!) |
| pipes.steamHeatRadius | 5 | радиус зоны трубы с паром |
| pipes.steamHeatMultiplier | 1.5 | накал трубы |
| pipes.steamFluidIds | ["create:steam"] | что считать паром (префиксное совпадение) |
| condensation.enabled / turnIntoWater / amountPerTick | true / true / 1000 | поведение конденсации |
| perf.scanIntervalTicks / applyIntervalTicks / maxTrackedSources | 20 / 10 / 4096 | TPS-защита |

## TODO: Artem's Task (зоны для самостоятельной работы)

Помечены в коде, ищи `grep -rn "TODO: Artem" src`:
1. **ReactorCoreBlock.animateTick** — частицы пламени над реактором (клиент).
2. **RadiatorBlock** — рецепт уже есть; добавь название в lang, анимацию, при желании — внутренний танк воды.
3. **CondensationHandler** — конденсация пара из «открытого конца» трубы (не только у радиатора).
4. **ReactorEventHandler.onExplosion** — чистка зон реакторов при взрыве.
5. **ModBlocks/ModItems** — зарегистрируй свой новый блок и предмет по шаблонам в комментариях.
6. **HeatTickHandler** — оптимизация перебора мобов (только рядом с игроками).
7. Персистентность: сериализуй `reactorZones` в SavedData уровня, чтобы зоны выживали после рестарта сервера.
