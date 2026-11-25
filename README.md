# Pepe Visual (Fabric, Minecraft 1.21.11)

Визуальный клиент без PvP-функций. Весь код написан с нуля (аналоги функций FlyVisual и Pipizduci, не копия).
Лягушонок в `assets/pepevisual/textures/gui/pepe.png` нарисован специально для проекта, его можно заменить своей картинкой 128x128.

## Как собрать
1. JDK 21 и Gradle 9.x (или IntelliJ IDEA).
2. Один раз: `gradle wrapper --gradle-version 9.2.0`
3. Сборка: `./gradlew build` (Windows: `gradlew.bat build`)
4. Мод: `build/libs/PepeVisual-1.0.0.jar` -> в `mods/` вместе с Fabric Loader 0.18.4+ и Fabric API для 1.21.11.
5. Запуск из IDE: `./gradlew runClient`

Если Gradle не находит Fabric API, поменяй `fabric_version` в `gradle.properties`
(https://modrinth.com/mod/fabric-api/versions?g=1.21.11). Вшитые оптимизационные моды: `bundle_optimization=true/false`.

## Управление
- **Right Shift** - меню. ЛКМ - вкл/выкл, **ПКМ - настройки и клавиша функции**, колёсико - прокрутка.
- Привязка клавиши: ПКМ по функции -> "Клавиша" -> ЛКМ -> нажми кнопку (Esc - отмена, Delete - сброс).
- **C** (зажать) - Zoom. **Left Alt** (зажать) - FreeLook в режиме "Пока зажата клавиша".
- Описание появляется при наведении на любую функцию и любую настройку.

## Разделы
**Visual:** PlayerESP, TargetESP, **HitsToKill (сколько ударов до смерти цели)**, Trajectories (жемчуг эндера и др.), ItemESP, Tracers,
Glow, JumpCircles, Trails, DamageIndicators, HitParticles, HitAnimations, Crosshair, Fullbright, Zoom.
**Helper:** **AutoBrew** (бот сам ходит между зельеварками), BuildHelper, AutoFarm, DungeonFinder, BaseFinder.
**Movement:** AutoSprint, AutoWalk, **FreeLook** (камера от 3 лица вращается мышью, персонаж бежит вперёд и не поворачивается),
**AutoWindCharge** (авто-сфера воздуха: ветряной заряд под ноги).
**HUD:** **PepeBackground (лягушонок на заднем плане)**, Interface, Watermark, TargetHUD, ModuleList, Info, ArmorHUD, PotionHUD,
ItemCounter, Cooldowns, NearbyPlayers, Radar, Keystrokes. **NoRender**, **Utility** (AutoRespawn), **Optimization**.

### AutoBrew
Выбери зелье в настройках, положи в инвентарь водяные бутылки, адский нарост, нужный ингредиент и огненный порошок.
Бот находит зельеварки в радиусе обхода, идёт к каждой (поиск пути A*: ступеньки, спуски до 3 блоков, обход лавы и кактусов),
открывает, раскладывает, забирает готовое и возвращается на место. Двери не открывает, воду не переплывает, за припасами
в сундуки не ходит.

### FreeLook
Нужны миксины `EntityMixin` и `CameraMixin`. Если `CameraMixin` не подошёл версии игры, мышь не блокируется: FreeLook просто не включится.

### AutoWindCharge
Ветряной заряд должен лежать в хотбаре или во второй руке. Режим "По клавише": бросок вниз один раз, затем функция выключается.
Режим "При прыжке": бросок при каждом прыжке с земли.

## Если игра не запускается / не компилируется
Самые рискованные места (API 1.21.11): `KeyMapping.Category.MISC`, `VanillaHudElements.*`, `Options.setKey`, `MouseButtonEvent/KeyEvent`,
`GuiGraphics.blit` (PepeBackground), `handleInventoryMouseClick` (AutoBrew), `Inventory.setSelectedSlot` (AutoFarm, AutoWindCharge),
`getCooldownPercent` (Cooldowns). Миксины необязательные: неподошедший только пишет предупреждение в лог.
