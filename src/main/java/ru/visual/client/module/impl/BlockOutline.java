package ru.visual.client.module.impl;

import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;

/** Цвет и толщина обводки блока под прицелом (LevelRendererMixin). */
public class BlockOutline extends Module {
    private static BlockOutline instance;

    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет обводки. ПКМ по полосе - радуга.", 0x55FFFF));
    private final NumSetting alpha = add(new NumSetting("Прозрачность", "Непрозрачность обводки (255 = сплошная).", 220, 40, 255, 5));
    private final NumSetting width = add(new NumSetting("Толщина", "Толщина линий (множитель).", 2.0, 1.0, 6.0, 0.5));
    private final BoolSetting pulse = add(new BoolSetting("Пульсация", "Обводка плавно мерцает.", false));

    public BlockOutline() {
        super("Block Outline", "Своя обводка блока под прицелом: цвет, радуга, толщина, пульсация.", Category.VISUAL);
        instance = this;
    }

    public static int color(int vanilla) {
        BlockOutline m = instance;
        if (m == null || !m.isEnabled()) return vanilla;
        double a = m.alpha.get();
        if (m.pulse.get()) a *= 0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 250.0);
        return ((int) Math.max(0, Math.min(255, a)) << 24) | (m.color.argb() & 0xFFFFFF);
    }

    public static float width(float vanilla) {
        BlockOutline m = instance;
        if (m == null || !m.isEnabled()) return vanilla;
        return vanilla * m.width.asFloat();
    }
}
