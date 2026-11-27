package ru.visual.client.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

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

    private static final net.minecraft.world.level.block.Block[] WOOD_BUTTONS = {Blocks.SPRUCE_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.JUNGLE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.MANGROVE_BUTTON, Blocks.CHERRY_BUTTON,
            Blocks.BAMBOO_BUTTON, Blocks.CRIMSON_BUTTON, Blocks.WARPED_BUTTON, Blocks.PALE_OAK_BUTTON};

    static {
        // ================= FunTime =================
        ALL.add(ftSmelter());
        ALL.add(ftBrewer());
        ALL.add(ftSpawnerFarm());
        ALL.add(ftPumpkin());
        ALL.add(ftTrap());

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

        return new Blueprint("Ферма на спавнере из данжа", "Мобы",
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

    // =====================================================================
    //  Чертежи FunTime (tt.funtime.su) - схемы, которыми реально пользуются на сервере
    // =====================================================================

    /** Сетка для генерируемых чертежей: g[y][z][x]. */
    private static final class Grid {
        final char[][][] g;

        Grid(int sx, int sy, int sz) {
            g = new char[sy][sz][sx];
            for (char[][] l : g) for (char[] r : l) Arrays.fill(r, '.');
        }

        Grid set(int x, int y, int z, char c) {
            g[y][z][x] = c;
            return this;
        }

        Grid line(int y, int z, int x0, int x1, char c) {
            for (int x = x0; x <= x1; x++) g[y][z][x] = c;
            return this;
        }

        String[][] layers() {
            String[][] out = new String[g.length][];
            for (int y = 0; y < g.length; y++) {
                out[y] = new String[g[y].length];
                for (int z = 0; z < g[y].length; z++) out[y][z] = new String(g[y][z]);
            }
            return out;
        }
    }

    /** Полный непрозрачный блок (проводит редстоун-сигнал, держит факел/рычаг). */
    private static Blueprint.Spec opaque(String note) {
        return block(note, Blocks.COBBLESTONE, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE,
                Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.SMOOTH_STONE, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS,
                Blocks.BIRCH_PLANKS, Blocks.DIRT, Blocks.NETHERRACK, Blocks.POLISHED_DEEPSLATE);
    }


    /**
     * Супер-плавильня FT: 8 печей, над воронками - энергорельсы, по ним между упорами катаются вагонетки с сундуком
     * (верхняя - сырьё, задняя - топливо) и раздают всё поровну.
     * Две линии рельсов разнесены, чтобы рельсы не состыковались друг с другом в горку.
     */
    private static Blueprint ftSmelter() {
        int n = 8;
        Grid g = new Grid(n + 2, 5, 3);
        g.line(0, 0, 1, n, 'e').set(n + 1, 0, 0, 'C');
        g.line(1, 0, 1, n, 'F').set(n + 1, 1, 0, '_');
        g.line(1, 1, 1, n, 'n').line(1, 2, 1, n, 'n');
        g.line(2, 0, 1, n, 'd').line(2, 1, 1, n, '_');
        g.set(0, 2, 2, 'X').line(2, 2, 1, n, 'R').set(n + 1, 2, 2, 'X');
        g.set(0, 3, 0, 'X').line(3, 0, 1, n, 'R').set(n + 1, 3, 0, 'X');
        g.set(0, 3, 2, 'l');
        g.set(0, 4, 0, 'l');
        return new Blueprint("FT: Супер-плавильня на 8 печей", "Плавка",
                "Схема «супер-плавильни» с FunTime: вагонетки с сундуком катаются по энергорельсам над воронками и раздают сырьё и топливо поровну во все 8 печей. Одна вагонетка - это 27 стаков руды.",
                guide("1. Нижний ряд воронок уходит на 1 блок в землю и сыплет на восток в сундук готового.",
                        "2. 8 печей в ряд: плавильные печи - для руды (в 2 раза быстрее), коптильни - для еды.",
                        "3. За печами 2 ряда воронок, обе смотрят НА СЕВЕР, к печам - по ним идёт топливо.",
                        "4. На печи - воронки вниз. На них и на задний ряд воронок - энергорельсы, по краям - блоки-упоры (не стекло).",
                        "5. Рычаги - сверху на западные упоры: каждый включает свою линию рельсов.",
                        "6. Поставь на каждую линию вагонетку с сундуком: в переднюю (верхнюю) - руду/песок/еду, в заднюю - уголь, лаву в вёдрах или блоки ламинарии.",
                        "7. Включи рычаги - вагонетки катаются туда-сюда сами. Выключи рычаг, чтобы остановить вагонетку и дозагрузить её.",
                        "С FT: пустые вёдра из-под лавы сами уходят в сундук готового. Рельсы двух линий не должны касаться - так и задумано."),
                1,
                legend('F', block(Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'n', facing(Blocks.HOPPER, Direction.NORTH),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        '_', AIR,
                        'C', chest(),
                        'X', opaque("Упор для вагонетки: полный непрозрачный блок. От него вагонетка отскакивает обратно."),
                        'R', Blueprint.rail(Blocks.POWERED_RAIL, Direction.EAST, "Энергорельсы: ставь прямо на воронки, вагонетка будет выдавать в них предметы."),
                        'l', block("Рычаг сверху на упор: включает и выключает линию рельсов.", Blocks.LEVER)),
                g.layers());
    }

    /**
     * Лестничная автозельеварка FT ("пионерская"): 3 стойки ступеньками, воронка под стойкой ведёт в следующую.
     * Воронки заперты факелами; деревянная кнопка за стенкой гасит факел на 1.5 сек - зелья сдвигаются на ступень.
     */
    private static Blueprint ftBrewer() {
        Grid g = new Grid(6, 7, 4);
        // поток бутылок (z=0)
        g.set(1, 4, 0, 'C').set(1, 3, 0, 'e').set(1, 5, 0, '_');
        int[][] st = {{2, 3}, {3, 2}, {4, 1}};
        for (int[] s : st) {
            int x = s[0], y = s[1];
            g.set(x, y, 0, 'B').set(x, y + 1, 0, 'd').set(x, y + 2, 0, 'I').set(x, y + 3, 0, '_');
            g.set(x, y - 1, 0, 'e');
        }
        g.set(5, 0, 0, 'O').set(5, 1, 0, '_');
        // замки: факел на стенке (z=1), стенка (z=2), кнопка (z=3) - для входной воронки и трёх воронок под стойками
        int[][] locks = {{1, 3}, {2, 2}, {3, 1}, {4, 0}};
        for (int[] l : locks) {
            int x = l[0], y = l[1];
            g.set(x, y, 1, 'T').set(x, y, 2, 'W').set(x, y, 3, 'b');
        }
        // вокруг факелов пусто, иначе они запитают лишнее
        int[][] air = {{2, 3}, {3, 2}, {4, 1}, {5, 0}, {1, 4}, {1, 2}, {2, 1}, {3, 0}};
        for (int[] a : air) g.set(a[0], a[1], 1, '_');
        return new Blueprint("FT: Автозельеварка-лестница", "Зельеварение",
                "Лестничная автозельеварка с FunTime (как на /warp help): 3 стойки ступеньками, бутылки сами переходят со ступени на ступень и падают в сундук готовыми. Факелы держат воронки закрытыми, пока зелья варятся.",
                guide("1. Сначала стенка сзади и красные факелы на ней - они запирают воронки под стойками.",
                        "   С FT: факелы ставь ДО того, как положишь бутылки, иначе бутылки сразу провалятся в выход.",
                        "2. Стойки ступеньками, под каждой - воронка в следующую стойку, нижняя - в сундук готовых.",
                        "3. Над каждой стойкой воронка и сундук с ингредиентом СВОЕЙ ступени: верх - незерский нарост, середина - эффект, низ - светопыль/редстоун.",
                        "4. Сундук слева вверху - бутылки с водой. В каждую стойку руками положи огненный порошок (1 шт = 20 варок).",
                        "5. Деревянные кнопки за стенкой: жми по очереди СНИЗУ ВВЕРХ с паузой 2 сек - готовые зелья сдвинутся на ступень, сверху зайдут новые бутылки.",
                        "6. Подожди ~20 сек, пока стойки сварят, и снова прожми кнопки снизу вверх.",
                        "Рецепты с FT: сила II = нарост, огненный порошок, светопыль; скорость II = нарост, сахар, светопыль.",
                        "Если бутылки проскакивают ступень (бывает в некоторых чанках) - делай паузу между кнопками дольше."),
                0,
                legend('B', block(Blocks.BREWING_STAND),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        'C', block("Сундук с бутылками воды (воронка подаёт их в верхнюю стойку).", Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL),
                        'I', block("Сундук с ингредиентом для этой ступени.", Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL),
                        'O', block("Сундук для готовых зелий.", Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL),
                        'T', Blueprint.wallTorch(Blocks.REDSTONE_WALL_TORCH, Direction.NORTH, "Факел запирает воронку перед ним, пока не нажата кнопка."),
                        'W', opaque("Блок стенки: держит факел, кнопка сзади гасит факел."),
                        'b', Blueprint.wallButton(Blocks.OAK_BUTTON, Direction.SOUTH, "Только деревянная кнопка: она держит сигнал 1.5 сек - ровно на 3 бутылки.", WOOD_BUTTONS),
                        '_', AIR),
                g.layers());
    }

    /**
     * Ферма на купленных спавнерах FT: центр - ровно на стыке 4 чанков (лимит FunTime - 6 мобов на чанк),
     * по спавнеру в каждой четверти, вода по периметру сносит мобов в дыру 2x2, падение 11 блоков, добивание у окна.
     */
    private static Blueprint ftSpawnerFarm() {
        int s = 20, f0 = 11, sy = f0 + 6;
        Grid g = new Grid(s, sy, s);
        // низ: 4 воронки (каждая в своём чанке) -> сундук
        g.set(9, 0, 9, 'e').set(10, 0, 9, 's').set(9, 0, 10, 'e').set(10, 0, 10, 's').set(10, 0, 11, 'C').set(9, 0, 11, '#');
        // шахта 2x2 в кольце из блоков
        for (int y = 1; y < f0; y++)
            for (int z = 8; z <= 11; z++)
                for (int x = 8; x <= 11; x++)
                    g.set(x, y, z, x >= 9 && x <= 10 && z >= 9 && z <= 10 ? '_' : '#');
        // окно для добивания: верхние плиты, перед ними место игрока
        g.set(9, 1, 11, 'H').set(10, 1, 11, 'H');
        for (int y = 1; y <= 2; y++) g.set(9, y, 12, '_').set(10, y, 12, '_');
        // платформа с дырой в центре
        for (int z = 0; z < s; z++)
            for (int x = 0; x < s; x++)
                g.set(x, f0, z, x >= 9 && x <= 10 && z >= 9 && z <= 10 ? '_' : '#');
        // стены 5 блоков
        for (int y = f0 + 1; y < sy; y++)
            for (int z = 0; z < s; z++)
                for (int x = 0; x < s; x++)
                    if (x == 0 || z == 0 || x == s - 1 || z == s - 1) g.set(x, y, z, '#');
        // вода по периметру: от неё до дыры ровно 8 клеток - вода не льётся в шахту, мобы падают
        for (int z = 1; z < s - 1; z++)
            for (int x = 1; x < s - 1; x++)
                if (x == 1 || z == 1 || x == s - 2 || z == s - 2) g.set(x, f0 + 1, z, 'W');
        for (int z = 9; z <= 10; z++) for (int x = 9; x <= 10; x++) g.set(x, f0 + 1, z, '_');
        // спавнеры: по одному в центре каждой четверти (каждый в своём чанке), на 3 блока над полом
        g.set(6, f0 + 3, 6, 'X').set(13, f0 + 3, 6, 'X').set(6, f0 + 3, 13, 'X').set(13, f0 + 3, 13, 'X');
        return new Blueprint("FT: Ферма на спавнерах (стык 4 чанков)", "Мобы",
                "Ферма мобов на купленных спавнерах, как строят на FunTime: по спавнеру в каждом из 4 чанков (лимит FT - 6 мобов на чанк), вода сносит мобов в центр, они падают на 11 блоков в воронки, а ты добиваешь их у окна.",
                guide("1. Чертёж сам встаёт центром на стык 4 чанков (розовая линия, проверь F3+G).",
                        "2. Внизу 4 воронки в сундук. Окно - верхние плиты: мобов видно по ноги, но выйти они не могут (даже дети).",
                        "3. Шахта 2x2 высотой 10 и платформа 20x20 со стенами в 5 блоков.",
                        "4. Вода - по всему периметру платформы (можно через клетку, промежутки станут источниками). До дыры вода не дотекает - так и надо.",
                        "5. Спавнеры висят на 3 блока над полом, по одному в каждой четверти. Ставь с временной подпорки, потом её сломай.",
                        "6. Стой у окна: все спавнеры в 16 блоках от тебя и работают. Мобы падают с 8 из 20 сердец - добивай мечом с разящим клинком.",
                        "С FT: в каждую четверть можно ставить 2-3 спавнера одного моба вплотную. Проверь, что под фермой нет пещер - туда уходит лимит мобов.",
                        "Пауки не подойдут - лезут по стенам шахты. Нужен слизень? Стены выше 3 блоков уже есть."),
                1,
                legend('#', SOLID, '_', AIR, 'W', WATER,
                        'X', block("Спавнер (на FT покупается). Ставь с подпорки, потом подпорку убери.", Blocks.SPAWNER),
                        'H', Blueprint.state(Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP),
                                "Верхняя плита: снизу щель в полблока - бьёшь мобов по ногам.", Blocks.COBBLESTONE_SLAB, Blocks.SMOOTH_STONE_SLAB,
                                Blocks.STONE_BRICK_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.ANDESITE_SLAB, Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        's', facing(Blocks.HOPPER, Direction.SOUTH),
                        'C', chest()),
                g.layers()).withChunkCorner(10, 10);
    }

    /**
     * Тыквенная траншея FT на 11 стеблей: плоды растут на грязи над воронками (грязь ниже блока - предметы
     * проваливаются в воронку), обсерверы над стеблями запускают все поршни сразу.
     */
    private static Blueprint ftPumpkin() {
        int n = 11;
        Grid g = new Grid(n + 2, 5, 5);
        g.line(0, 1, 1, n, 'e').set(n + 1, 0, 1, 'C');
        g.line(0, 2, 1, n, 'n');
        g.line(0, 3, 1, n, 'K');
        g.line(1, 0, 1, n, 'K');
        g.line(1, 1, 1, n, 'M').set(n + 1, 1, 1, '_');
        g.line(1, 2, 1, n, 'F').set(0, 1, 2, 'K').set(n + 1, 1, 2, 'K');
        g.line(1, 3, 1, n, 'K').set(3, 1, 3, 'W').set(n - 2, 1, 3, 'W');
        g.line(1, 4, 1, n, 'K');
        g.line(2, 0, 1, n, 'P');
        g.line(2, 1, 1, n, '_');
        g.line(2, 2, 1, n, 't').set(0, 2, 2, 'K').set(n + 1, 2, 2, 'K');
        g.line(3, 0, 1, n, 'K').line(3, 1, 1, n, 'L').line(3, 2, 1, n, 'o');
        for (int z = 0; z <= 2; z++) g.line(4, z, 1, n, 'r');
        return new Blueprint("FT: Тыквы/арбузы - траншея на 11", "Фермы",
                "Тыквенно-арбузная траншея на 11 стеблей, как на FunTime: обсервер видит выросший плод, все поршни разом срубают урожай, а воронки под грязью и грядками собирают его в сундук.",
                guide("1. Ряд воронок в землю: передний сыплет на восток в сундук, задний - на север в передний.",
                        "2. На передние воронки - грязь (вылей бутылку воды на землю): плод растёт на ней, а предметы проваливаются сквозь неё в воронку.",
                        "3. На задние воронки - земля, вскопай мотыгой. За грядками ряд камня с 2 ведрами воды - поливают грядки.",
                        "4. Поршни смотрят на грязь, над ними - камень. Над грязью - светящиеся блоки: стеблям под обсерверами нужен свет.",
                        "5. Над стеблями обсерверы глазом ВНИЗ, сверху - сплошное поле редстоун-пыли 3x11.",
                        "6. Посади семена тыквы или арбуза. Края закрыты камнем - плод вырастет только на грязи.",
                        "С FT: ферма растёт, пока ты в 128 блоках (чанки прогружены) - ставь рядом с базой или АФК-местом."),
                1,
                legend('K', stone(), 'W', WATER, '_', AIR,
                        'M', block("Грязь: плод растёт на ней, а предметы проваливаются в воронку под ней.", Blocks.MUD),
                        'F', block(Blocks.FARMLAND),
                        't', block(Blocks.PUMPKIN_STEM, Blocks.MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.ATTACHED_MELON_STEM),
                        'L', block("Светящийся блок (стеблям нужен свет 9+).", Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT,
                                Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT),
                        'P', facing(Blocks.PISTON, Direction.SOUTH),
                        'o', facing(Blocks.OBSERVER, Direction.DOWN),
                        'r', block(Blocks.REDSTONE_WIRE),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'n', facing(Blocks.HOPPER, Direction.NORTH),
                        'C', chest()),
                g.layers());
    }

    /**
     * Трапка-яма в проходе FT: нить между крючками -> факел гаснет -> липкий поршень утягивает пол над шахтой 1x1
     * из обсидиана. Когда игрок упал, нить отпускается и пол возвращается над ним.
     */
    private static Blueprint ftTrap() {
        Grid g = new Grid(4, 10, 6);
        g.set(2, 0, 2, 'O');
        for (int y = 1; y <= 6; y++) {
            g.set(2, y, 2, '_');
            g.set(1, y, 2, 'O').set(3, y, 2, 'O').set(2, y, 1, 'O').set(2, y, 3, 'O');
        }
        // опоры пыли
        g.set(0, 6, 3, '#').set(0, 6, 4, '#').set(0, 6, 5, '#').set(1, 7, 5, '#');
        // уровень пола
        g.set(0, 7, 2, 'p').set(1, 7, 2, '_').set(2, 7, 2, 'F').set(3, 7, 2, '#').set(2, 7, 1, '#').set(2, 7, 3, '#');
        g.set(0, 7, 3, 'r').set(0, 7, 4, 'r').set(0, 7, 5, 'r');
        // уровень ног: стенки прохода, крючки, нить, блок с факелом
        g.set(2, 8, 0, '#').set(2, 8, 1, 'a').set(2, 8, 2, 'w').set(2, 8, 3, 'b').set(2, 8, 4, '#').set(2, 8, 5, 'T');
        g.set(1, 8, 5, 'r').set(0, 8, 5, '_').set(3, 8, 5, '_');
        g.set(1, 8, 1, '#').set(3, 8, 1, '#').set(1, 8, 3, '#').set(3, 8, 3, '#');
        for (int x = 0; x <= 3; x++) if (x != 2) g.set(x, 8, 2, '_');
        for (int x = 0; x <= 3; x++) g.set(x, 9, 2, '_');
        g.set(1, 9, 1, '#').set(2, 9, 1, '#').set(3, 9, 1, '#').set(1, 9, 3, '#').set(2, 9, 3, '#').set(3, 9, 3, '#');
        g.set(2, 9, 5, '_');
        return new Blueprint("FT: Трапка-яма в проходе", "Ловушки",
                "Ловушка для прохода к базе (FunTime): игрок задевает нить - липкий поршень убирает пол, игрок падает в обсидиановую шахту 1x1, и пол тут же закрывается над ним. Не путать с предметом «Трапка» - тот ставит клетку на 15 сек.",
                guide("1. Шахта 1x1 глубиной 6 из обсидиана - его не сломать быстро, а жемчуг упрётся в пол.",
                        "2. Липкий поршень лицом к яме, перед ним пусто, дальше - блок пола над шахтой (НЕ обсидиан: его поршень не сдвинет).",
                        "3. Стенки прохода, в них 2 крючка друг напротив друга и нить между ними - прямо над ямой.",
                        "4. За южным крючком - блок, на нём сзади красный факел. Пыль от факела идёт к поршню: пока факел горит, пол закрыт.",
                        "5. Игрок задел нить -> крючок гасит факел -> поршень утягивает пол -> игрок падает -> через полсекунды пол на месте.",
                        "Совет с FT: поставь сундук-приманку сразу за ловушкой - игрок остановится прямо на нити. В тёмном проходе нить почти не видно."),
                8,
                legend('O', block(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN), '#', SOLID, '_', AIR,
                        'F', block("Пол-крышка над ямой: любой блок, который двигает поршень (не обсидиан). Подбери под пол вокруг.",
                                Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE, Blocks.STONE, Blocks.COBBLESTONE, Blocks.STONE_BRICKS,
                                Blocks.DEEPSLATE_TILES, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS),
                        'p', facing(Blocks.STICKY_PISTON, Direction.EAST),
                        'r', block(Blocks.REDSTONE_WIRE),
                        'a', facing(Blocks.TRIPWIRE_HOOK, Direction.SOUTH),
                        'b', facing(Blocks.TRIPWIRE_HOOK, Direction.NORTH),
                        'w', block("Нить: ставь между крючками (ниткой из паутины/пауков).", Blocks.TRIPWIRE),
                        'T', Blueprint.wallTorch(Blocks.REDSTONE_WALL_TORCH, Direction.SOUTH, "Факел держит поршень выдвинутым (пол закрыт).")),
                g.layers());
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
