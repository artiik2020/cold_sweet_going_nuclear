# SAM.md — Artem's Optional Tasks (задачи на подумать)

Мод **CSGN** (мост Create ↔ Going Nuclear ↔ Cold Sweat) работает и компилируется.
Этот файл — список **СТРОГО ОПЦИОНАЛЬНЫХ** задач для обучения. Каждая задача:

- помечена в коде комментарием `// TODO: Artem's Optional Task - [Название]`;
- безопасна: если ты её **не делаешь вообще**, мод компилируется и ведёт себя ровно так же;
- сделана «лесенкой»: сначала заглушка/пустой метод, потом наполнение.

Порядок прохождения (от простого к сложному): **#6 → #3 → #1 → #2 → #4 → #5**.

---

## Задача #1 — Визуал: частицы пара над горячими трубами 🔥💨

**Где находится:**
`src/main/java/com/yourname/myfirstmod/csgn/client/SteamAmbience.java`, метод `applyTick(Level, BlockPos)` — сейчас это пустая заглушка.

**Что нужно сделать (логика):**
Игра выглядит «мертвой», если по трубам течёт невидимый пар. Идея: над каждой трубой из нашего кэша (`HeatZoneManager.pipeSnapshotKeys()`) периодически спавнить белые дымки-частицы, имитирующие клубы пара. Частота появления зависит от множителя тепла трубы.

**Подсказка по Java:**
1. Импорт **только** `net.minecraft.core.particles.ParticleTypes` (НЕ `ParticleType` — это тип-обёртка, константы живут в `ParticleTypes`):
   ```java
   level.addParticle(ParticleTypes.CLOUD,
           pos.getX() + 0.5 + rnd.nextDouble(-0.3, 0.3),
           pos.getY() + 1.05,
           pos.getZ() + 0.5 + rnd.nextDouble(-0.3, 0.3),
           0.0, 0.02, 0.0);   // скорость: чуть вверх
   ```
2. Частицы живут **ТОЛЬКО на клиенте**: первой строкой `if (!level.isClientSide()) return;`. В Python ты бы просто «не заметил» разницы сторон — здесь Minecraft упадёт с крашем при попытке вызвать клиентский код на сервере.
3. Таймер привязывай к `level.getGameTime() % 4 == 0`, а не создавай свою переменную `gameTime`.
4. Чтобы хелпер вообще начал вызываться, подпишись на событие `LevelTickEvent.Client` (в NeoForge 1.21.1 оно существует) и перебирай позиции из кэша клиента. Регистрация — как у других хендлеров: `NeoForge.EVENT_BUS.register(...)` в конструкторе `Csgn` (аннотации не используем!).

**Подводные камни Java vs Python/Lua:**
- `addParticle` требует ровно **6 double** координат/скоростей (как AABB). Пропущенный аргумент = ошибка компиляции, а не TypeError в рантайме.
- Строгая типизация: `pos.getX()` вернёт `int`, а метод ждёт `double` — Java сама расширит int→double, но `float` потребует явного `(double)` или суффикса careful с `0.0f` vs `0.0`.

**Зачем это круто (геймдизайн):**
Игрок начинает ВИДЕТЬ, где проходит тепловая сеть, и интуитивно понимает: «белые клубы = тут горячо, стой подальше». Без текста и туториалов механика становится читаемой.

---

## Задача #2 — Звук: шипение пара рядом с горячей трубой 🔊

**Где находится:**
Тот же `SteamAmbience.java`, метод `playHiss(Level, BlockPos, double playerDist)` — пустая заглушка.

**Что нужно сделать (логика):**
Дистанционно затухающий звук: чем ближе игрок к трубе с паром, тем громче шипение. На расстоянии больше радиуса тепла — тишина. Это «аудио-индикатор опасности», как гудение трансформатора.

**Подсказка по Java:**
```java
double volume = Math.max(0.0, 1.0 - playerDist / Config.steamPipeHeatRadius);
if (volume > 0.02) {
    level.playLocalSound(pos, SoundEvents.ANVIL_USE /* или свой звук */,
            SoundSource.BLOCKS, (float) volume, 1.5f, false);
}
```
- Громкость/питч в API звуков — `float`, поэтому `(float) volume` и `1.5f` (литерал `1.5` — это double, компилятор его в float сам не сожмёт).
- Не зацикливай трек каждый тик — звук «наложится» сотней слоёв и оглушит. Играй раз в ~30 тиков (`getGameTime() % 30`).
- Свой звук: положи `.ogg` в `assets/csgn/sounds/steam_hiss.ogg`, опиши `assets/csgn/sounds.json` и зарегистрируй через `SoundEvent.createVariableRangeEvent(ResourceLocation.parse("csgn:steam_hiss"))` (DeferredRegister для SoundEvent — реестр `BuiltinRegistries` не трогай).

**Подводные камни Java:**
- Все параметры метода фиксированы сигнатурой — нельзя, как в Lua, вызвать `playHiss(level, pos)` «без dist», компилятор не пропустит. Либо перегрузка метода, либо дефолт внутри.
- `Math.max(0.0, ...)` возвращает `double`; не забудь привести к `float` перед передачей.

**Зачем это круто:**
Звук — самый дешёвый способ добавить атмосферы и «страха machinery». Игрок закрывает глаза и слышит сеть. Плюс это первый шаг к кастомным ресурсам (sounds.json) — обязательный навык мододела.

---

## Задача #3 — Мираж/исказление воздуха над реактором 🌫️

**Где находится:**
`src/main/java/com/yourname/myfirstmod/csgn/block/ReactorCoreBlock.java`, метод `animateTick(...)` — там уже есть `TODO` прямо под штатными FLAME-частицами.

**Что нужно сделать (логика):**
Реактор должен выглядеть «живым»: добавить второй слой частиц (SMALL_FLAME / SOUL_FIRE_FLAME для «ядерного» оттенка), редкие искры, лёгкое мерцание. Настоящее искажение воздуха (heat haze шейдером) — уровень хардкор, начни с частиц.

**Подсказка по Java:**
- Всё в том же `animateTick`, который уже дергается только на клиенте (`level.isClientSide()` внутри условия).
- Варианты констант: `ParticleTypes.SMALL_FLAME`, `ParticleTypes.ELECTRIC_SPARK`, `ParticleTypes.SOUL_FIRE_FLAME`.
- Рандом бери из переданного `RandomSource random`, а не `new Random()` — движок специально даёт детерминированный генератор на чанк.

**Подводные камни Java:**
- Метод `animateTick` — override родительского метода `Block`. Аннотация `@Override` обязательна, опечатка в имени аргументов не ломает компиляцию, но тогда это ДРУГОЙ метод и он никогда не вызовется (в Python аналог — «тихий» shadow атрибута).
- `super.animateTick(...)` оставь первым вызовом.

**Зачем это круто:**
Визуальный язык мода: оранжевое пламя = обычный нагрев, сине-фиолетовое = ядерное излучение. Игроки учатся читать мир без GUI.

---

## Задача #4 — Ожог от горячих труб (урон при касании) 🩹

**Где находится:**
`src/main/java/com/yourname/myfirstmod/csgn/handler/HeatTickHandler.java`:
- закоментированный вызов `// applyBurnChance(living, heat);` внутри цикла;
- пустой метод-заглушка `applyBurnChance(LivingEntity, double)` внизу класса;
- готовый конфиг-параметр `Config.hotPipeBurnChance` (уже добавлен в `Config.java`, дефолт **0.0** = выключено).

**Что нужно сделать (логика):**
Сделать горячие зоны опасными: с шансом `hotPipeBurnChance` сущность, стоящая в зоне с теплом, получает полсердца урона. Игрок начинает бояться ходить по паровым цехам и строить теплоизоляцию.

**Подсказка по Java (по шагам):**
```java
private void applyBurnChance(LivingEntity entity, double heat) {
    if (Config.hotPipeBurnChance <= 0.0) return;                       // фича выключена
    if (entity.level().random.nextDouble() >= Config.hotPipeBurnChance) return; // монетка
    entity.hurt(entity.level().damageSources().generic(), 1.0f);       // полсердца
}
```
Потом раскомментируй вызов в цикле. Шанс меняй в `config/csgn-common.toml` → `gameplay.hotPipeBurnChance = 0.05`.

**Подводные камни Java:**
- Урон наносим на СЕРВЕРЕ (этот хендлер уже серверный — не переноси логику на клиент, иначе читеры обнулят урон).
- Второй параметр `hurt(...)` — `float`: пиши `1.0f`. `1.0` — double, компиляция упадёт.
- `nextDouble()` ∈ [0, 1): условие `>= chance` корректно даёт шанс ровно `chance`.
- Желательно добавить кулдаун (например, не чаще раза в 2 секунды на сущность) — держи `Map<UUID, Long>` последних ожогов; вот тут всплывёт разница Python/Java: ключи в HashMap должны быть `immutable` + правильные `hashCode/equals` (UUID подходит идеально).

**Зачем это круто:**
Появляется настоящий gameplay-loop риска: Cold Sweat считает температуру, а урон заставляет реагировать немедленно. Мост из «справочной таблички» превращается в механику выживания.

---

## Задача #5 — Блок «Медный радиатор» (быстрая конденсация) 🧱

**Где находится:**
- заготовка класса: `src/main/java/com/yourname/myfirstmod/csgn/block/CopperRadiatorBlock.java` (пустой, нигде не зарегистрирован);
- закоментированная регистрация: `init/ModBlocks.java` → `TODO ... [Медный радиатор]`;
- заглушка множителя: `handler/CondensationHandler.java` → метод `copperMultiplier(...)` (сейчас всегда `1`);
- подсказка применения: `handler/SteamPipeHeatHandler.java` → `TODO ... mult *= 1.5`.

**Что нужно сделать (логика):**
Апгрейд радиатора: медный вариант конденсирует пар вдвое быстрее (возвращает `2` из `copperMultiplier`) и даёт больший тепловой множитель. Классический паттерн «блок-маркер + проверка в обработчике» — тот же, что уже работает для обычного `csgn:radiator`.

**Подсказка по Java (порядок):**
1. Раскомментируй регистрацию `COPPER_RADIATOR` и метод `isCopperRadiator(state)` в `ModBlocks.java`.
2. Добавь предмет: `ITEMS.registerSimpleBlockItem("copper_radiator", ModBlocks.COPPER_RADIATOR);` в `ModItems.java`.
3. Наполни `copperMultiplier(...)`:
   ```java
   BlockState state = level.getBlockState(radiatorPos);
   if (ModBlocks.isCopperRadiator(state)) return 2;
   return 1;
   ```
4. Примени его в `condenseInto(...)`: замени `Config.condenseAmountMb` на `Config.condenseAmountMb * copperMultiplier(level, radiatorPos)`.
5. Ресурсы: `blockstates/copper_radiator.json`, модель, текстура, lang-строка, рецепт.

**Подводные камни Java:**
- Порядок статической инициализации: `ModBlocks.COPPER_RADIATOR` доступен только после регистрации DeferredRegister — никогда не кешируй `COPPER_RADIATOR.get()` в static-поле своего класса, получай лениво внутри методов.
- `return` обязателен на ВСЕХ ветках метода `int` (Python вернул бы None молча, Java — compile error).
- `state.is(block)` — правильный способ сравнения блока в 1.21.1 (не `==`).

**Зачем это круто:**
Первый полноценный кастомный блок с РЕАЛЬНОЙ механикой = прогрессия (железо → медь), мотивация майнить/крафтить и ощущение, что мод «живой», а не просто калькулятор температур.

---

## Задача #6 — Команда /csgn heat вместо клика по блоку ⌨️

**Где находится:**
`src/main/java/com/yourname/myfirstmod/csgn/debug/CsgnDebug.java` — `TODO` в конце метода (там же формируется diagnostic-строка, которую можно вынести в общий метод и переиспользовать).

**Что нужно сделать (логика):**
Сейчас диагностика печатается по клику пустой рукой — неудобно и мешает строить. Сделай команду `/csgn heat`, которая вызывает ровно тот же код формирования строки и отправляет её игроку.

**Подсказка по Java:**
```java
@SubscribeEvent // слушатель уже зарегистрирован вручную в Csgn — добавь метод сюда
public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("csgn")
        .then(Commands.literal("heat")
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                p.displayClientMessage(Component.literal(buildDebugLine(p)), false);
                return 1;
            })));
}
```
(Регистрация события команд — game-шина `NeoForge.EVENT_BUS`; метод `buildDebugLine` выдели из текущего кода `onLeftClickBlock`.)

**Подводные камни Java:**
- Builder-цепочки Brigadier (`literal(...).then(...).executes(...)`) типизированы: `executes` ждёт `Command<S>` — лямбду с возвратом `int` (это amount — сколько «сработало»).
- `getPlayerOrException()` кидает `CommandSyntaxException` — метод `run` обязан это пробрасывать (в Java checked-exceptions надо объявлять `throws`, Python просто кинет что угодно).
- Событие `RegisterCommandsEvent` приходит один раз на старте сервера — идеальное место, никаких утечек.

**Зачем это круто:**
Ты освоишь Brigadier — систему команд Mojang, без которой не обходится ни один крупный мод. А ещё `/csgn heat` станет твоим лучшим другом при отладке следующих фич.

---

## Чек-лист безопасности задач

| № | Файл | Тип заглушки | Статус по умолчанию |
|---|------|--------------|---------------------|
| 1 | SteamAmbience.applyTick | пустой метод | не вызывается |
| 2 | SteamAmbience.playHiss | пустой метод | не вызывается |
| 3 | ReactorCoreBlock.animateTick | TODO-комментарий | работают только штатные FLAME |
| 4 | HeatTickHandler.applyBurnChance | пустой метод + закомментированный вызов + config 0.0 | выключено тройственно |
| 5 | CopperRadiatorBlock + ModBlocks + copperMultiplier | незарегистрированный класс, закомментированная регистрация, `return 1` | блок отсутствует в игре |
| 6 | CsgnDebug | TODO-комментарий | клик-диагностика работает как раньше |

Ни одна задача не может сломать сборку, пока ты сам не раскомментируешь/не наполнишь код. Удачи, Артем! 🚀
