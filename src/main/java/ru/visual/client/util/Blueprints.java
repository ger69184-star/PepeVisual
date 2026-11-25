package ru.visual.client.util;

import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Встроенные чертежи. Это простые планировки без редстоуна. */
public final class Blueprints {
    public static final List<Blueprint> ALL = new ArrayList<>();

    private Blueprints() {}

    static {
        ALL.add(new Blueprint("Зельеварня 7x5",
                "Комната 7x5x5: 4 зельеварки и котёл на стойке у задней стены, 2 сундука с запасами, вход по центру передней стены.",
                Map.of('S', Blocks.STONE_BRICKS, 'P', Blocks.OAK_PLANKS, 'B', Blocks.BREWING_STAND,
                        'T', Blocks.CAULDRON, 'C', Blocks.CHEST, 'L', Blocks.SEA_LANTERN),
                new String[][]{
                        {"SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS"},
                        {"SSSSSSS", "SPPPPPS", "S.....S", "SC...CS", "SSS.SSS"},
                        {"SSSSSSS", "SBBTBBS", "S.....S", "S.....S", "SSS.SSS"},
                        {"SSSSSSS", "S.....S", "S.....S", "S.....S", "SSSSSSS"},
                        {"SSSSSSS", "SSSSSSS", "SSSLSSS", "SSSSSSS", "SSSSSSS"}
                }));

        ALL.add(new Blueprint("Грядка 9x9 с водой",
                "Классическая грядка 9x9: земля со столбиком воды в центре орошает всё поле. Землю вскопай мотыгой, воду поставь ведром.",
                Map.of('D', Blocks.DIRT, 'W', Blocks.WATER),
                new String[][]{
                        {"DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD", "DDDDWDDDD",
                                "DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD", "DDDDDDDDD"}
                }));

        ALL.add(new Blueprint("Портал в Незер 4x5",
                "Рама портала 4x5 из 10 обсидиана (углы не нужны). Зажги внутри огнивом.",
                Map.of('O', Blocks.OBSIDIAN),
                new String[][]{
                        {".OO."},
                        {"O..O"},
                        {"O..O"},
                        {"O..O"},
                        {".OO."}
                }));

        ALL.add(new Blueprint("Мини-дом 5x5",
                "Простой дом 5x5x4: булыжный пол, стены из досок, 4 окна, дверной проём спереди, плоская крыша.",
                Map.of('C', Blocks.COBBLESTONE, 'P', Blocks.OAK_PLANKS, 'G', Blocks.GLASS),
                new String[][]{
                        {"CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC"},
                        {"PPPPP", "P...P", "P...P", "P...P", "PP.PP"},
                        {"PPPPP", "G...G", "P...P", "G...G", "PP.PP"},
                        {"PPPPP", "PPPPP", "PPPPP", "PPPPP", "PPPPP"}
                }));
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
