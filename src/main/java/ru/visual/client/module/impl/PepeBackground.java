package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Лягушонок на заднем плане HUD. Рисуется ДО остальных элементов HUD (см. VisualClient).
 * Картинка: assets/pepevisual/textures/gui/pepe.png (128x128), её можно заменить своей.
 */
public class PepeBackground extends Module implements Config.DefaultOn {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("pepevisual", "textures/gui/pepe.png");

    private final ModeSetting position = add(new ModeSetting("Положение", "Где рисуется лягушонок на экране. ЛКМ - дальше, ПКМ - назад.", "Правый нижний угол",
            "Правый нижний угол", "Левый нижний угол", "Правый верхний угол", "Левый верхний угол", "По центру"));
    private final NumSetting size = add(new NumSetting("Размер", "Размер картинки в пикселях.", 110, 40, 300, 5));
    private final NumSetting opacity = add(new NumSetting("Прозрачность", "Насколько заметен лягушонок (1 = непрозрачный).", 0.35, 0.05, 1.0, 0.05));
    private final NumSetting offX = add(new NumSetting("Смещение X", "Сдвиг по горизонтали в пикселях.", 0, -500, 500, 1));
    private final NumSetting offY = add(new NumSetting("Смещение Y", "Сдвиг по вертикали в пикселях.", 0, -300, 300, 1));

    public PepeBackground() {
        super("PepeBackground", "Лягушонок Pepe на заднем плане HUD (рисуется под остальными элементами).", Category.HUD);
    }

    /** Вызывается как отдельный элемент HUD, самым первым. */
    public static void renderBackground(GuiGraphics g, DeltaTracker delta) {
        PepeBackground m = ModuleManager.pepe;
        Minecraft mc = Minecraft.getInstance();
        if (m == null || !m.isEnabled() || mc.player == null || mc.options.hideGui) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int s = m.size.asInt();
        int x, y;
        switch (m.position.get()) {
            case "Левый нижний угол" -> { x = 6; y = h - s - 6; }
            case "Правый верхний угол" -> { x = w - s - 6; y = 6; }
            case "Левый верхний угол" -> { x = 6; y = 6; }
            case "По центру" -> { x = (w - s) / 2; y = (h - s) / 2; }
            default -> { x = w - s - 6; y = h - s - 6; }
        }
        draw(g, x + m.offX.asInt(), y + m.offY.asInt(), s, m.opacity.get());
    }

    /** Рисует лягушонка в указанном месте (используется и в меню). */
    public static void draw(GuiGraphics g, int x, int y, int size, double alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f, size, size, 128, 128, 128, 128, (a << 24) | 0xFFFFFF);
    }
}
