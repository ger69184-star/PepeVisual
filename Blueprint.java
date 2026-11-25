package ru.visual.client.util;

import net.minecraft.world.level.block.Block;

import java.util.Map;

/** Чертёж постройки: слои снизу вверх, в слое строки по оси Z, символы по оси X. '.' и ' ' - любой блок. */
public final class Blueprint {
    public final String name;
    public final String description;
    public final int sx, sy, sz;
    private final String[][] layers;
    private final Map<Character, Block> legend;

    public Blueprint(String name, String description, Map<Character, Block> legend, String[][] layers) {
        this.name = name;
        this.description = description;
        this.legend = legend;
        this.layers = layers;
        this.sy = layers.length;
        int z = 0, x = 0;
        for (String[] layer : layers) {
            z = Math.max(z, layer.length);
            for (String row : layer) x = Math.max(x, row.length());
        }
        this.sz = z;
        this.sx = x;
    }

    /** Блок в клетке чертежа или null, если там "любой блок". */
    public Block blockAt(int x, int y, int z) {
        if (y < 0 || y >= layers.length) return null;
        String[] layer = layers[y];
        if (z < 0 || z >= layer.length) return null;
        String row = layer[z];
        if (x < 0 || x >= row.length()) return null;
        char c = row.charAt(x);
        if (c == '.' || c == ' ') return null;
        return legend.get(c);
    }
}
