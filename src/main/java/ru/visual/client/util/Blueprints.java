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

    static {
        // ---------------- Фермы ----------------
        ALL.add(new Blueprint("Ферма тростника (обсервер)", "Фермы",
                "Авто-ферма сахарного тростника на 6 кустов: наблюдатель видит, что тростник вырос до 3 блоков, и поршни срезают его. Урожай течёт по воде в воронку и сундук.",
                guide("1. Нижний слой уходит в землю: воронка и сундук для урожая.",
                        "2. Ручей: поставь одну воду в начале канала (у западной стены) - она сама дотечёт до воронки.",
                        "3. Поршни смотрят на тростник, над ними наблюдатели (глазом к тростнику).",
                        "4. За наблюдателями - линия редстоун-пыли: она включает все поршни сразу.",
                        "5. Тростник сажай последним, когда вода уже течёт.",
                        "Часть тростника может оставаться у основания - иногда подбирай руками."),
                1,
                legend('S', SOLID, 'G', block(Blocks.GLASS, Blocks.TINTED_GLASS, Blocks.WHITE_STAINED_GLASS),
                        'W', WATER, 'D', block(Blocks.SAND, Blocks.RED_SAND, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.MUD, Blocks.COARSE_DIRT),
                        'c', block(Blocks.SUGAR_CANE),
                        'p', facing(Blocks.PISTON, Direction.NORTH),
                        'o', facing(Blocks.OBSERVER, Direction.NORTH),
                        'r', block(Blocks.REDSTONE_WIRE),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'C', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL)),
                new String[][]{
                        {".........", ".SSSSSSeC", ".........", ".........", "........."},
                        {"SSSSSSSSS", "SW......G", ".DDDDDDS.", ".SSSSSS..", "........."},
                        {".GGGGGGG.", ".........", ".cccccc..", ".SSSSSS..", "........."},
                        {".GGGGGGG.", ".........", ".........", ".pppppp..", ".SSSSSS.."},
                        {".GGGGGGG.", ".........", ".........", ".oooooo..", ".rrrrrr.."}
                }));

        ALL.add(new Blueprint("Яйцефабрика (курицы)", "Фермы",
                "4 курицы сидят прямо в воронках: снесённые яйца сами уходят в сундук.",
                guide("1. Воронки ставь цепочкой: каждая сыплет в следующую, последняя - в сундук (Shift+ПКМ по боковой грани).",
                        "2. Стеклянный загон в 2 блока - курицы не выпрыгнут.",
                        "3. Брось внутрь яйца или заведи куриц на семенах: по одной на воронку.",
                        "Яйца можно сразу бросать для новых куриц или варить в печи."),
                1,
                legend('G', block(Blocks.GLASS, Blocks.TINTED_GLASS, Blocks.WHITE_STAINED_GLASS),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'C', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL)),
                new String[][]{
                        {"......", ".eeeeC", "......"},
                        {".GGGG.", "G....G", ".GGGG."},
                        {".GGGG.", "G....G", ".GGGG."}
                }));

        ALL.add(new Blueprint("Грядка 9x9 с водой", "Фермы",
                "Классическая грядка 9x9: одна вода в центре орошает всё поле. Землю вскопай мотыгой.",
                guide("1. Положи землю (или используй уже имеющуюся).",
                        "2. В центр - ведро воды.",
                        "3. Вскопай мотыгой и посади семена. AutoFarm соберёт урожай сам."),
                1,
                legend('D', block(Blocks.DIRT, Blocks.FARMLAND, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT), 'W', WATER),
                new String[][]{
                        {"DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD", "DDDDWDDDD",
                                "DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD"}
                }));

        // ---------------- Мобы ----------------
        ALL.add(spawnerFarm());

        // ---------------- Зельеварение ----------------
        ALL.add(new Blueprint("Зельеварка с подачей", "Зельеварение",
                "Полуавтоматическая зельеварка: сверху воронка подаёт ингредиенты, сбоку - бутылки и огненный порошок.",
                guide("1. Левый сундук: бутылки с водой и огненный порошок (топливо).",
                        "2. Верхний сундук: ингредиенты по одному на партию в порядке варки,",
                        "   например: незерский нарост, сахар, редстоун - получится долгая скорость.",
                        "3. Готовые зелья забирай руками (нижняя воронка вытащила бы бутылки раньше времени).",
                        "AutoBrew умеет обходить такие зельеварки сам."),
                0,
                legend('B', block(Blocks.BREWING_STAND),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        'K', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL),
                        'C', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL)),
                new String[][]{
                        {"eB"},
                        {"Kd"},
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
                        'K', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL),
                        'C', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL)),
                new String[][]{
                        {"eB.eB.eB"},
                        {"Kd.Kd.Kd"},
                        {".C..C..C"}
                }));

        ALL.add(new Blueprint("Зельеварня-комната 7x5", "Зельеварение",
                "Комната 7x5x5: 4 зельеварки и котёл на стойке у задней стены, 2 сундука с запасами, вход по центру передней стены.",
                guide("Стены из каменного кирпича, стойка из досок, освещение - морской фонарь в потолке."),
                0,
                legend('S', block(Blocks.STONE_BRICKS), 'P', block(Blocks.OAK_PLANKS), 'B', block(Blocks.BREWING_STAND),
                        'T', block(Blocks.CAULDRON, Blocks.WATER_CAULDRON), 'C', block(Blocks.CHEST, Blocks.BARREL), 'L', block(Blocks.SEA_LANTERN, Blocks.GLOWSTONE)),
                new String[][]{
                        {"SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS"},
                        {"SSSSSSS", "SPPPPPS", "S.....S", "SC...CS", "SSS.SSS"},
                        {"SSSSSSS", "SBBTBBS", "S.....S", "S.....S", "SSS.SSS"},
                        {"SSSSSSS", "S.....S", "S.....S", "S.....S", "SSSSSSS"},
                        {"SSSSSSS", "SSSSSSS", "SSSLSSS", "SSSSSSS", "SSSSSSS"}
                }));

        // ---------------- Плавка ----------------
        ALL.add(new Blueprint("Авто-печь", "Плавка",
                "Печь с тремя сундуками: сверху - что плавить, сбоку - топливо, снизу - готовое. Подходит и плавильня/коптильня.",
                guide("1. Сначала нижний сундук, потом воронка под печью (Shift+ПКМ по сундуку).",
                        "2. Печь, затем боковая воронка (Shift+ПКМ по боку печи) и сундук для топлива на ней.",
                        "3. Воронка на печь (сыплет вниз) и сундук на неё - туда руду/песок/еду.",
                        "Печь можно поставить любой стороной."),
                0,
                legend('F', block(Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER),
                        'e', facing(Blocks.HOPPER, Direction.EAST),
                        'd', facing(Blocks.HOPPER, Direction.DOWN),
                        'K', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL),
                        'C', block(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL)),
                new String[][]{
                        {".eC"},
                        {"eF."},
                        {"Kd."},
                        {".C."}
                }));

        // ---------------- Постройки ----------------
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

        ALL.add(new Blueprint("Мини-дом 5x5", "Постройки",
                "Простой дом 5x5x4: булыжный пол, стены из досок, 4 окна, дверной проём спереди, плоская крыша.",
                guide("Дверь поставь в проём спереди, факел - внутри."),
                0,
                legend('C', block(Blocks.COBBLESTONE, Blocks.STONE, Blocks.COBBLED_DEEPSLATE), 'P', block(Blocks.OAK_PLANKS),
                        'G', block(Blocks.GLASS, Blocks.GLASS_PANE)),
                new String[][]{
                        {"CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC"},
                        {"PPPPP", "P...P", "P...P", "P...P", "PP.PP"},
                        {"PPPPP", "G...G", "P...P", "G...G", "PP.PP"},
                        {"PPPPP", "PPPPP", "PPPPP", "PPPPP", "PPPPP"}
                }));
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
