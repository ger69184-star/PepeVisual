package ru.visual.client.module.impl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Лягушонок Pepe - только в меню клиента (по центру шапки), на игровом экране его больше нет.
 * Картинка: assets/pepevisual/textures/gui/pepe.png (128x128), её можно заменить своей.
 */
public class PepeBackground extends Module implements Config.DefaultOn {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("pepevisual", "textures/gui/pepe.png");

    private final NumSetting size = add(new NumSetting("Размер", "Размер лягушонка в шапке меню.", 28, 16, 30, 1));
    private final NumSetting opacity = add(new NumSetting("Прозрачность", "1 - полностью видно, меньше - прозрачнее.", 1.0, 0.2, 1.0, 0.1));
    private final BoolSetting bob = add(new BoolSetting("Покачивание", "Лягушонок слегка покачивается.", true));
    private final BoolSetting glow = add(new BoolSetting("Свечение", "Мягкое свечение цветом темы за лягушонком.", true));

    public PepeBackground() {
        super("Pepe", "Лягушонок Pepe в шапке меню клиента (на экране игры не рисуется).", Category.HUD);
    }

    public int sizePx() {
        return size.asInt();
    }

    public double opacity() {
        return opacity.get();
    }

    public boolean bobbing() {
        return bob.get();
    }

    public boolean glow() {
        return glow.get();
    }

    /** Рисует лягушонка в указанном месте. */
    public static void draw(GuiGraphics g, int x, int y, int size, double alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f, size, size, 128, 128, 128, 128, (a << 24) | 0xFFFFFF);
    }
}
