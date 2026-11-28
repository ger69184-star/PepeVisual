# Памятка для ИИ-агентов и разработчиков (PepeVisual)

Прочитай этот файл целиком перед работой. Здесь - что уже сделано, как устроен проект и чего хочет владелец.
История изменений по шагам - в `README.md` (разделы «Обновление N»), полный список функций и настроек - в `FEATURES.md`.

## Проект
- Визуальный клиент (мод) **Fabric для Minecraft 1.21.11**, ориентирован на сервер **FunTime** (анархия).
- Mojang mappings, Java 21, Fabric Loader 0.18.4, Fabric API 0.141.6+1.21.11, Loom 1.14-SNAPSHOT.
- Пакет: `src/main/java/ru/visual/client/`
  - `VisualClient.java` - точка входа: клавиши, события Fabric, подписки.
  - `module/` - `Module`, `HudModule`, `ModuleManager` (список модулей, тик/HUD/3D, обработка ошибок), `Config`.
  - `module/impl/` - все функции (~50 модулей + 13 кнопок Optimization в `GraphicsTweak`).
  - `setting/` - Bool/Num/Mode/Color настройки.
  - `gui/ClickGuiScreen.java` - меню (Right Shift), `gui/HudEditorScreen.java` - редактор HUD.
  - `util/` - `Draw` (2D), `R3D` (3D), `Projection`, `Lang` (перевод), `Human`/`Aim`/`Keys` (помощники),
    `TargetManager`, `HeldTracker`, `Cps`.
  - `mixin/` - 18 клиентских миксинов (`src/main/resources/pepevisual.mixins.json`).
- Разделы меню: Visual, HUD, NoRender, Movement, Utility, Optimization, Menu.

## Сборка и проверка
- Локально Java может не быть - проверка компиляции идёт через **GitHub Actions** (`.github/workflows/build.yml`) на каждый push.
- Ошибка CI «429 Too Many Requests» от Maven Central - это не ошибка кода: подождать ~1.5 мин и перезапустить (`gh run rerun <id>`).
- Сигнатуры методов Minecraft 1.21.11 удобно сверять по декомпилированным исходникам:
  `git clone --depth 1 https://github.com/rrrRex1024/minecraft-1-21-11-source`.
- Особенности 1.21.11: `Screen.hasControlDown()` нет - `this.minecraft.hasControlDown()`; `SoundEvents.UI_BUTTON_CLICK` - `Holder`,
  в `forUI` нужен `.value()`; 3D рисуется через `RenderTypes` (никаких ручных `RenderSystem`/GL-состояний).

## Правила кода (соблюдать)
- **Язык.** Внутренние значения (названия настроек, режимы, ключи конфига) - **на русском**, отображение идёт через `Lang.t(...)`
  и словарь `src/main/resources/assets/pepevisual/texts/en.json` (RU -> EN). Новая строка интерфейса = новая запись в `en.json`.
  Для новых модулей можно `Lang.of(ru, en)`. Формат `en.json`: по одной паре на строку, без завершающего перевода строки.
- **Модуль:** `onTick` / `onHud` / `onWorld` вызываются `ModuleManager` только в мире (проверка player/level уже есть) и в try/catch.
  Код, вызываемый из миксинов и событий экранов, работает вне этой защиты - проверяй null и не бросай исключений.
- **Смена мира:** всё, что привязано к миру (позиции, существа, очереди), сбрасывать в `onWorldChange(mc)`. Вызывается при выходе,
  смене измерения и переходе между серверами/лобби FunTime (там измерение не меняется - сравнивать мир по ссылке, а не по `dimension()`).
- **Настройки:** `add(...)`; видимые только при условии - `addIf(setting, () -> условие)`.
- **Рендер HUD:** `pushMatrix/popMatrix` и `enableScissor/disableScissor` - в try/finally.
- **Коллекции, пополняемые в реальном времени,** - обязательно с лимитом или очисткой.
- **Конфиги** пишутся через `Config.writeAtomic(...)`; при чтении каждое значение - отдельно, с безопасным значением по умолчанию.
- Не переносить строки внутри строковых литералов, сравнений режимов и ключей конфига.

## Пожелания владельца (обязательные)
- Отвечать **на русском**.
- Функции - практичные для реальных игроков FunTime, а не «удобные для ИИ».
- Меню: не квадратные коробки, красиво и плавно, без шрифта Minecraft; первая колонка не уходит за экран.
- Пепе - только в меню (по центру, один раз, реагирует на свои настройки), никогда на игровом экране.
- Предупреждения о взрывах - время в секундах, не проценты.
- Оптимизация - отдельные кнопки вкл/выкл в меню (без правки файлов).
- ArmorHUD - не на хотбаре; «100%» одним числом, пока всё целое; по отдельности - когда что-то повреждено.
- ESP и HUD - приятные глазу, есть полезные 3D-визуалы. Есть просмотр хотбара цели / игрока под прицелом.
- AutoSprint сделан миксином в `LocalPlayer.aiStep` - не возвращать старый вариант через `keySprint.setDown`.
- **Compliance с FunTime.** Убраны все функции, запрещённые на FunTime, и их аналоги: авто-кликеры/помощники
  (AutoBrew, AutoFarm, ChestSorter, AutoTool, AutoEat, AutoTotem, AutoArmor), BuildHelper (аккуратная установка блоков /
  Litematica-аналог), ищейки (DungeonFinder, BaseFinder), FreeLook (FreeCam), AutoWalk, AutoPearl, AutoWindCharge,
  AutoRespawn, AntiAFK, InventoryMove, Compass (Advanced Compass), Glow, ItemESP, StorageESP, ShulkerPreview (X-Ray/TrueSight-аналоги).
  Добавлять функцию - только если она точно не попадает под запретный список FunTime.

## Что сделано (кратко)
- Исправления всех функций, много новых функций по образцу FlyVisual, оффлайн-аккаунты.
- Обновления 4-8: удобство меню, редактор HUD, вкладка Menu, английский язык.
- Обновление 11: аудит стабильности - `onWorldChange`, безопасные конфиги, лимиты коллекций, защита рендер-стека.
- **Удалено по требованию владельца:** весь функционал, запрещённый на FunTime, и его аналоги (см. «Compliance с FunTime» выше).
- Подробности - `README.md`, список всех настроек - `FEATURES.md`. При новых функциях обновлять оба файла.
