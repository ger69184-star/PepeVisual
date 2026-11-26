package ru.visual.client.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ru.visual.client.util.Blueprint.AIR;
import static ru.visual.client.util.Blueprint.SOLID;
import static ru.visual.client.util.Blueprint.WATER;
import static ru.visual.client.util.Blueprint.block;
import static ru.visual.client.util.Blueprint.existing;
import static ru.visual.client.util.Blueprint.facing;
import static ru.visual.client.util.Blueprint.legend;
import static ru.visual.client.util.Blueprint.state;

/**
 * Встроенные чертежи-гайды. Система координат чертежа: строка 0 каждого слоя - северная сторона (-Z),
 * символы слева направо - на восток (+X), слои - снизу вверх.
 */
public final class Blueprints {
    public static final List<Blueprint> ALL = new ArrayList<>();

    private Blueprints() {}

    private static List<String> guide(String... lines) {
        return Arrays.asList(lines);
    }

    private static final net.minecraft.world.level.block.Block[] GLASSES = {Blocks.TINTED_GLASS, Blocks.WHITE_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS, Blocks.LIME_STAINED_GLASS};
    private static final net.minecraft.world.level.block.Block[] CHESTS = {Blocks.TRAPPED_CHEST, Blocks.BARREL};

    private static Blueprint.Spec chest() {
        return block(Blocks.CHEST, CHESTS);
    }

    private static Blueprint.Spec glass() {
        return block(Blocks.GLASS, GLASSES);
    }

    /** Твёрдый блок, но не земля (чтобы тыква/арбуз не выросли не там). */
    private static Blueprint.Spec stone() {
        return block("Камень/булыжник/кирпич - НЕ земля, иначе плод вырастет здесь.", Blocks.COBBLESTONE,
                Blocks.STONE, Blocks.STONE_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE,
                Blocks.GRANITE, Blocks.SMOOTH_STONE, Blocks.GLASS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS);
    }

    static {
        // ================= Фермы =================
        ALL.add(new Blueprint("Тростник 2x7 (обсерверы)", "Фермы",
                "Полностью автоматическая ферма тростника на 14 кустов: обсерверы видят, что тростник вырос до 3 блоков, поршни срезают его, урожай падает в ручей и уплывает в воронку и сундук.",
                guide("1. Платформа и воронка в сундук (воронка сыплет на восток в сундук).",
                        "2. Два ряда песка/земли по бокам канала и стенки вокруг.",
                        "3. Одно ведро воды у западного края канала - вода сама дотечёт до воронки.",
                        "4. Поршни (лицом к тростнику), над ними обсерверы глазом к тростнику.",
                        "5. За обсерверами на блоках - линия редстоун-пыли: она включает все поршни ряда.",
                        "6. Посади тростник на песок. Сундук справа - урожай.",
                        "Масштабируется: удлини ряды, через каждые 7 блоков канала добавь ещё воду."),
                0,
                legend('S', SOLID, 'W', WATER, '_', AIR,
                        'D', block(Blocks.SAND, Blocks.RED_SAND, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.MUD, Blocks.MOSS_BLOCK),
                        'c', block(Blocks.SUGAR_CANE),
                        'p', facing(Blocks.PISTON, Direction.SOUTH),
                        'q', facing(Blocks.PISTON, Direction.NORTH),
                        'o', facing(Blocks.OBSERVER, Direction.SOUTH),
                        'u', facing(Blocks.OBSERVER, Direction.NORTH),
                        'r', block(Blocks.REDSTONE_WIRE),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'C', chest()),
                new String[][]{
                        {".SSSSSSS..", ".SSSSSSS..", "SSSSSSSSS.", "SSSSSSSSeC", "SSSSSSSSS.", ".SSSSSSS..", ".SSSSSSS.."},
                        {".SSSSSSS..", ".SSSSSSS..", "SDDDDDDDS.", "SW_______S", "SDDDDDDDS.", ".SSSSSSS..", ".SSSSSSS.."},
                        {".SSSSSSS..", ".SSSSSSS..", ".ccccccc..", ".________.", ".ccccccc..", ".SSSSSSS..", ".SSSSSSS.."},
                        {".SSSSSSS..", ".ppppppp..", "", "", "", ".qqqqqqq..", ".SSSSSSS.."},
                        {".rrrrrrr..", ".ooooooo..", "", "", "", ".uuuuuuu..", ".rrrrrrr.."}
                }));

        ALL.add(new Blueprint("Тыквы / арбузы (обсерверы)", "Фермы",
                "Автоматическая ферма на 9 стеблей: обсервер над стеблем видит, что вырос плод, поршень ломает его, и урожай падает на грядку, а воронки под грядкой забирают его в сундук.",
                guide("1. Ряд воронок (сыплют на восток) и сундук в конце - это и есть сбор урожая.",
                        "2. На воронки - земля, вскопай её мотыгой: воронка подбирает предметы сквозь грядку.",
                        "3. Перед грядкой ряд земли - здесь будут расти тыквы/арбузы. По краям - камень, не земля!",
                        "4. Сзади канал с одним ведром воды (поливает грядку), сверху закрой стеклом.",
                        "5. Поршни смотрят на клетки с плодами, над стеблями обсерверы смотрят ВНИЗ.",
                        "6. Сверху на блоках и обсерверах - редстоун-пыль сплошным полем 3x9.",
                        "7. Посади семена. Нужен свет: днём солнце, ночью поставь фонари вокруг."),
                0,
                legend('S', SOLID, 'K', stone(), 'W', WATER, '_', AIR, 'G', glass(),
                        'D', block("Земля: здесь вырастет плод.", Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MOSS_BLOCK),
                        'F', block(Blocks.FARMLAND),
                        't', block(Blocks.PUMPKIN_STEM, Blocks.MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.ATTACHED_MELON_STEM),
                        'P', facing(Blocks.PISTON, Direction.SOUTH),
                        'o', facing(Blocks.OBSERVER, Direction.DOWN),
                        'r', block(Blocks.REDSTONE_WIRE),
                        'h', facing(Blocks.HOPPER, Direction.EAST),
                        'C', chest()),
                new String[][]{
                        {".SSSSSSSSS.", ".SSSSSSSSS.", ".hhhhhhhhhC", "SSSSSSSSSSS", ""},
                        {".SSSSSSSSS.", "KDDDDDDDDDK", "KFFFFFFFFF_", "K____W____K", ".KKKKKKKKK."},
                        {".PPPPPPPPP.", "", ".ttttttttt.", ".GGGGGGGGG.", ""},
                        {".SSSSSSSSS.", ".SSSSSSSSS.", ".ooooooooo.", "", ""},
                        {".rrrrrrrrr.", ".rrrrrrrrr.", ".rrrrrrrrr.", "", ""}
                }));

        ALL.add(new Blueprint("Огород 8x6 со смывом", "Фермы",
                "Пшеница/морковь/картошка/свёкла 8x6: нажал кнопку - раздатчики выливают воду, она срывает урожай и сносит его в воронки. Нажал ещё раз - вода убирается обратно.",
                guide("1. Выдели зону на земле: грядки будут прямо в уровень с землёй.",
                        "2. Ряд воронок спереди (сыплют на восток в сундук), по бокам канавы с водой - они поливают грядки.",
                        "3. Стенка вокруг поля, сзади 4 раздатчика смотрят на поле. В каждый положи по ведру воды.",
                        "4. Сверху на раздатчиках - редстоун-пыль, за ними блок с кнопкой.",
                        "5. Кнопка: урожай смыт в воронки. Подожди пару секунд и нажми ещё раз - раздатчики заберут воду.",
                        "6. Пересади семена (их тоже собирают воронки) - удобно с AutoFarm."),
                1,
                legend('S', SOLID, 'W', WATER, '_', AIR,
                        'F', block(Blocks.FARMLAND),
                        'w', block("Посади семена: пшеница, морковь, картофель или свёкла.", Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS),
                        'd', facing(Blocks.DISPENSER, Direction.NORTH),
                        'r', block(Blocks.REDSTONE_WIRE),
                        'b', block("Любая кнопка на южной стороне блока.", Blocks.STONE_BUTTON, Blocks.OAK_BUTTON, Blocks.SPRUCE_BUTTON,
                                Blocks.BIRCH_BUTTON, Blocks.POLISHED_BLACKSTONE_BUTTON),
                        'h', facing(Blocks.HOPPER, Direction.EAST),
                        'C', chest()),
                new String[][]{
                        {"", "ShhhhhhhhhC", "_FFFFFFFF_.", "_FFFFFFFF_.", "WFFFFFFFFW.", "_FFFFFFFF_.", "_FFFFFFFF_.", "_FFFFFFFF_.", "S........S."},
                        {"SSSSSSSSSS.", "S________S_", "SwwwwwwwwS.", "SwwwwwwwwS.", "SwwwwwwwwS.", "SwwwwwwwwS.", "SwwwwwwwwS.", "SwwwwwwwwS.",
                                "SdSdSSdSdS.", "....S......"},
                        {"", "", "", "", "", "", "", "", ".rrrrrrrr..", "....S......", "....b......"}
                }));

        ALL.add(new Blueprint("Яйцефабрика на 8 куриц", "Фермы",
                "8 куриц стоят прямо в воронках: снесённые яйца сами уходят в сундук. Яйца - на новых куриц или в печь-коптильню.",
                guide("1. Задний ряд воронок сыплет на юг (в передний ряд), передний - на восток в сундук.",
                        "2. Стеклянный загон высотой 2 блока - курицы не выпрыгнут.",
                        "3. Заведи куриц на семенах или бросай яйца внутрь: по одной курице на воронку.",
                        "4. Сундук открывается с восточной стороны."),
                0,
                legend('G', glass(),
                        's', facing(Blocks.HOPPER, Direction.SOUTH),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'C', chest()),
                new String[][]{
                        {"", ".ssss.", ".eeeeC", ""},
                        {".GGGG.", "G....G", "G....G", ".GGGG."},
                        {".GGGG.", "G....G", "G....G", ".GGGG."}
                }));

        // ================= Мобы =================
        ALL.add(spawnerFarm());

        // ================= Ресурсы =================
        ALL.add(new Blueprint("Генератор булыжника", "Ресурсы",
                "Бесконечный булыжник: лава стекает к воде и превращается в камень на одной и той же клетке. Сломал - через секунду новый.",
                guide("1. Пол и стенки. В средней клетке пола - яма: туда стекает вода, поэтому она не доходит до лавы.",
                        "2. Справа ведро воды, слева ведро лавы. Лаву - последней!",
                        "3. Булыжник появляется на клетке между лавой и водой. Копай его сверху.",
                        "Если вместо булыжника появился обсидиан - вода дотекла до самой лавы, проверь яму."),
                0,
                legend('S', SOLID, 'W', WATER, 'L', Blueprint.LAVA, '_', AIR),
                new String[][]{
                        {"...S..", "SSS_SS", "...S.."},
                        {".SSSS.", "SL._WS", ".SSSS."},
                        {"", ".S....", ""}
                }));

        // ================= Плавка =================
        ALL.add(new Blueprint("Плавильня на 4 печи", "Плавка",
                "Четыре печи работают параллельно: сверху 2 двойных сундука с рудой/едой, сзади 2 двойных сундука с топливом, всё готовое собирается в один сундук.",
                guide("1. Нижний ряд воронок (на восток) и сундук для готового справа.",
                        "2. Печи на воронки. Сзади воронки смотрят В печи (Shift+ПКМ по задней стороне печи) - это топливо.",
                        "3. На печи - воронки вниз, на них сундуки: 4 сундука в ряд сами станут двумя двойными.",
                        "4. На задние воронки - сундуки для угля/ламинарии/лавы в вёдрах.",
                        "Каждый двойной сундук кормит 2 печи поровну. Печь можно заменить на плавильню или коптильню."),
                0,
                legend('F', block(Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'n', facing(Blocks.HOPPER, Direction.NORTH),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        '_', AIR,
                        'C', chest()),
                new String[][]{
                        {"eeeeC", ""},
                        {"FFFF_", "nnnn."},
                        {"dddd.", "CCCC."},
                        {"CCCC.", ""}
                }));

        ALL.add(new Blueprint("Авто-печь", "Плавка",
                "Одна печь с тремя сундуками: сверху - что плавить, сбоку - топливо, снизу - готовое. Подходит и плавильня/коптильня.",
                guide("1. Сначала нижний сундук, потом воронка в него (Shift+ПКМ по сундуку).",
                        "2. Печь, затем боковая воронка (Shift+ПКМ по боку печи) и сундук для топлива на ней.",
                        "3. Воронка на печь (сыплет вниз) и сундук на неё - туда руду/песок/еду."),
                0,
                legend('F', block(Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        'C', chest()),
                new String[][]{
                        {".eC"},
                        {"eF."},
                        {"Cd."},
                        {".C."}
                }));

        // ================= Зельеварение =================
        ALL.add(new Blueprint("Зельеварка с подачей", "Зельеварение",
                "Зельеварка с подачей: сверху воронка подаёт ингредиенты, сбоку - бутылки и огненный порошок.",
                guide("1. Боковой сундук: бутылки с водой и огненный порошок (топливо).",
                        "2. Верхний сундук: ингредиенты по одному на партию в порядке варки,",
                        "   например: незерский нарост, сахар, редстоун - получится долгая скорость.",
                        "3. Готовые зелья забирай руками (воронка снизу вытащила бы бутылки раньше времени).",
                        "AutoBrew запоминает такие стойки и обходит их сам."),
                0,
                legend('B', block(Blocks.BREWING_STAND),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        'C', chest()),
                new String[][]{
                        {"eB"},
                        {"Cd"},
                        {".C"}
                }));

        ALL.add(new Blueprint("Зельеварня на 3 стойки", "Зельеварение",
                "Три зельеварки с подачей в ряд - варят сразу 9 зелий.",
                guide("1. У каждой стойки свой сундук сбоку (бутылки + порошок) и сверху (ингредиенты).",
                        "2. Боковая воронка кладёт бутылки и топливо, верхняя - ингредиент.",
                        "3. Ингредиенты клади по одному на партию в порядке варки."),
                0,
                legend('B', block(Blocks.BREWING_STAND),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        'C', block(Blocks.BARREL, Blocks.CHEST, Blocks.TRAPPED_CHEST)),
                new String[][]{
                        {"eB.eB.eB"},
                        {"Cd.Cd.Cd"},
                        {".C..C..C"}
                }));

        // ================= Хранение =================
        ALL.add(sorter());

        // ================= Постройки =================
        ALL.add(new Blueprint("Пузырьковый лифт вверх", "Постройки",
                "Водяной лифт на 8 блоков: песок душ внизу создаёт пузырьковый столб, который быстро поднимает наверх. Вход - через дверь, выход - на север.",
                guide("1. Песок душ в основание, стеклянная труба вокруг, дверь внизу (дверь не пропускает воду).",
                        "2. Вода должна быть ИСТОЧНИКОМ в каждом блоке трубы: ставь вёдра снизу вверх.",
                        "   Совет: вылей ведро наверху, потом снизу кликай ведром по текущей воде - она станет источником.",
                        "3. Наверху выходи на север (на стену трубы).",
                        "Хочешь лифт вниз - вместо песка душ поставь магмовый блок."),
                1,
                legend('S', SOLID, 'W', WATER, '_', AIR, 'G', glass(),
                        's', block(Blocks.SOUL_SAND),
                        'D', block("Любая деревянная дверь (железную не открыть рукой).", Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR,
                                Blocks.JUNGLE_DOOR, Blocks.ACACIA_DOOR, Blocks.DARK_OAK_DOOR, Blocks.MANGROVE_DOOR, Blocks.CHERRY_DOOR,
                                Blocks.BAMBOO_DOOR, Blocks.CRIMSON_DOOR, Blocks.WARPED_DOOR)),
                new String[][]{
                        {".S.", "SsS", ".S."},
                        {".G.", "GWG", ".D."},
                        {".G.", "GWG", ".D."},
                        {".G.", "GWG", ".G."},
                        {".G.", "GWG", ".G."},
                        {".G.", "GWG", ".G."},
                        {".G.", "GWG", ".G."},
                        {".G.", "GWG", ".G."},
                        {".G.", "GWG", ".G."},
                        {"._.", "G_G", ".G."},
                        {"._.", "._.", ""}
                }));

        ALL.add(new Blueprint("Портал в Незер 4x5", "Постройки",
                "Рама портала 4x5 из 10 обсидиана (углы не нужны). Зажги внутри огнивом.",
                guide("Углы рамки не обязательны. Внутри рамки должно быть пусто."),
                0,
                legend('O', block(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN), '_', AIR),
                new String[][]{
                        {".OO."},
                        {"O__O"},
                        {"O__O"},
                        {"O__O"},
                        {".OO."}
                }));
    }

    /**
     * Сортировщик на 4 вида предметов (классическая схема: воронка-фильтр + компаратор + факел).
     * Ячейки через одну по X, чтобы пыль соседних ячеек не соединялась.
     */
    private static Blueprint sorter() {
        int n = 4, sx = n * 2 + 1;
        String[][] layers = new String[5][];
        char[][][] g = new char[5][5][sx];
        for (char[][] l : g) for (char[] r : l) Arrays.fill(r, '.');
        for (int i = 0; i < n; i++) {
            int x = i * 2;
            // L0 (под землёй): блок под нижней воронкой, повторитель, факел, блок под пылью
            g[0][1][x] = 'S';
            g[0][2][x] = 'R';
            g[0][3][x] = 'T';
            g[0][4][x] = 'S';
            // L1: сундук, нижняя воронка (в сундук), опора компаратора, стекло под пылью, пыль ступенькой вниз
            g[1][0][x] = 'C';
            g[1][1][x] = 'n';
            g[1][2][x] = 'S';
            g[1][3][x] = 'g';
            g[1][4][x] = 'r';
            // L2: воронка-фильтр (смотрит на компаратор), компаратор, пыль, пусто над ступенькой
            g[2][1][x] = 'f';
            g[2][2][x] = 'k';
            g[2][3][x] = 'r';
            g[2][4][x] = '_';
        }
        for (int x = 0; x < sx - 1; x++) g[3][1][x] = 'e';
        g[3][1][sx - 1] = 'O';
        g[4][1][0] = 'I';
        for (int y = 0; y < 5; y++) {
            layers[y] = new String[5];
            for (int z = 0; z < 5; z++) layers[y][z] = new String(g[y][z]);
        }
        return new Blueprint("Сортировщик на 4 предмета", "Хранение",
                "Классический сортировщик: кидаешь всё в верхний сундук, каждый из 4 видов предметов сам уходит в свой сундук, остальное - в сундук в конце линии.",
                guide("1. Нижний слой уходит в землю: блок, повторитель (на север), факел на стене блока, блок.",
                        "2. Сундуки спереди, в них воронки смотрят НА СЕВЕР (в сундук).",
                        "3. Над ними воронки-фильтры смотрят НА ЮГ - в компаратор. Компаратор в режиме сравнения.",
                        "4. Пыль за компаратором лежит на стекле и ступенькой спускается на блок над факелом.",
                        "5. Сверху линия воронок на восток, в начале - сундук для загрузки, в конце - сундук для остального.",
                        "6. ВАЖНО: в каждый фильтр положи 18 нужных предметов в 1-й слот и по 1 \"пустышке\" (переименованная палка) в слоты 2-5.",
                        "Для предметов по 16 (жемчуг, яйца) - 1 шт в первый слот и по 1 пустышке."),
                1,
                legend('S', SOLID, '_', AIR, 'g', glass(),
                        'R', Blueprint.diode(Blocks.REPEATER, Direction.NORTH),
                        'T', Blueprint.wallTorch(Blocks.REDSTONE_WALL_TORCH, Direction.NORTH, "Факел держит нижнюю воронку закрытой, пока в фильтре нет лишнего предмета."),
                        'k', Blueprint.diode(Blocks.COMPARATOR, Direction.SOUTH),
                        'r', block(Blocks.REDSTONE_WIRE),
                        'n', facing(Blocks.HOPPER, Direction.NORTH),
                        'f', state(Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, Direction.SOUTH),
                                "Фильтр: 18 сортируемых предметов + 4 пустышки."),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'C', chest(), 'O', chest(), 'I', chest()),
                layers);
    }

    /**
     * Ферма лута на спавнере зомби/скелетов/пауков: комната 9x9x3 вокруг спавнера, вода смывает мобов в канаву,
     * в центре канавы - табличка (вода через неё не течёт) и шахта 24 блока. Мобы разбиваются, лут - в воронку.
     */
    private static Blueprint spawnerFarm() {
        int sx = 11, sz = 11, sy = 30, f0 = 25;
        char[][][] g = new char[sy][sz][sx];
        for (char[][] l : g) for (char[] r : l) Arrays.fill(r, '.');
        // дно: воронка в сундук
        g[0][1][5] = 'h';
        g[0][2][5] = 'C';
        g[0][1][4] = '#';
        g[0][1][6] = '#';
        g[0][0][5] = '#';
        // шахта
        for (int y = 1; y <= f0 - 1; y++) {
            g[y][1][5] = '_';
            g[y][1][4] = '#';
            g[y][1][6] = '#';
            g[y][0][5] = '#';
            g[y][2][5] = y == 1 ? 'G' : '#';
        }
        // дно канавы
        for (int x = 1; x <= 9; x++) if (x != 5) g[f0 - 1][1][x] = '#';
        // уровень пола: передняя стена, канава с водой, табличка, пол комнаты
        for (int x = 0; x <= 10; x++) {
            g[f0][0][x] = '#';
            g[f0][10][x] = '#';
        }
        for (int z = 1; z <= 9; z++) {
            g[f0][z][0] = '#';
            g[f0][z][10] = '#';
        }
        g[f0][1][1] = 'W';
        g[f0][1][9] = 'W';
        g[f0][1][5] = 'N';
        for (int z = 2; z <= 9; z++) for (int x = 1; x <= 9; x++) g[f0][z][x] = '#';
        // комната 3 блока в высоту
        for (int y = f0 + 1; y <= f0 + 3; y++) {
            for (int x = 0; x <= 10; x++) {
                g[y][0][x] = '#';
                g[y][10][x] = '#';
            }
            for (int z = 0; z <= 10; z++) {
                g[y][z][0] = '#';
                g[y][z][10] = '#';
            }
            for (int z = 1; z <= 9; z++) for (int x = 1; x <= 9; x++) g[y][z][x] = '_';
            for (int z = 6; z <= 9; z++) g[y][z][5] = '#'; // стенка за спавнером: там мобы застревали бы
        }
        for (int x = 1; x <= 9; x++) if (x != 5) g[f0 + 1][9][x] = 'W';
        g[f0 + 1][5][5] = 'S';
        // потолок
        for (int z = 0; z <= 10; z++) for (int x = 0; x <= 10; x++) g[f0 + 4][z][x] = '#';

        String[][] layers = new String[sy][sz];
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) layers[y][z] = new String(g[y][z]);

        return new Blueprint("Ферма мобов на спавнере", "Мобы",
                "Ферма лута на спавнере (зомби/скелеты/пауки): вода смывает мобов в шахту 24 блока, они разбиваются, лут собирается в сундук.",
                guide("1. Выдели зону вокруг данжа - чертёж сам выровняется по спавнеру.",
                        "2. Сначала выкопай комнату 9x9x3 и шахту (красные клетки 'убрать').",
                        "3. Вода: 8 вёдер у задней стены и 2 по краям канавы. Табличка - в центр канавы.",
                        "4. Не ставь факелы внутри: мобы спавнятся только в темноте.",
                        "5. Внизу шахты воронка в сундук - прокопай к сундуку проход сбоку.",
                        "Для опыта: подними дно шахты на 3 блока и добивай мобов рукой."),
                f0,
                legend('#', SOLID, '_', AIR, 'W', WATER, 'G', block(Blocks.GLASS, Blocks.TINTED_GLASS),
                        'S', existing(Blocks.SPAWNER, "Спавнер уже стоит в данже - по нему выравнивается чертёж."),
                        'N', state(Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
                                "Табличка на передней стене канавы: вода не течёт в шахту, а мобы проходят."),
                        'h', facing(Blocks.HOPPER, Direction.SOUTH),
                        'C', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL)),
                layers);
    }

    public static String[] names() {
        String[] n = new String[ALL.size()];
        for (int i = 0; i < n.length; i++) n[i] = ALL.get(i).name;
        return n;
    }

    public static Blueprint byName(String name) {
        for (Blueprint b : ALL) if (b.name.equals(name)) return b;
        return ALL.get(0);
    }
}
