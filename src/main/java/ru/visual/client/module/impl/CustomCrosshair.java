package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;

public class CustomCrosshair extends Module {
    public final BoolSetting hideVanilla = add(new BoolSetting("Скрыть ванильный", "Убирает стандартный прицел Minecraft, чтобы не было двух сразу.", true));
    private final ModeSetting style = add(new ModeSetting("Стиль", "Вид прицела. ЛКМ - дальше, ПКМ - назад.", "Плюс", "Плюс", "Крест", "Точка", "Круг"));
    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет прицела. ПКМ - радужный.", 0xFFFFFF));
    private final NumSetting size = add(new NumSetting("Размер", "Длина линий прицела.", 5, 1, 14, 1));
    private final NumSetting gap = add(new NumSetting("Зазор", "Расстояние от центра до линий.", 3, 0, 12, 1));
    private final NumSetting thick = add(new NumSetting("Толщина", "Толщина линий в пикселях.", 1, 1, 4, 1));
    private final BoolSetting outline = add(new BoolSetting("Чёрная обводка", "Обводка вокруг прицела для читаемости на светлом фоне.", true));
    private final BoolSetting dynamic = add(new BoolSetting("Динамический", "Прицел расширяется, когда ты двигаешься.", true));
    private final BoolSetting hitMarker = add(new BoolSetting("Хит-маркер", "Показывает крестик в центре при попадании по существу.", true));
    private final BoolSetting targetColor = add(new BoolSetting("Красный на цели", "Прицел краснеет, когда ты наводишься на существо.", true));

    private long hitTime;

    public CustomCrosshair() {
        super("Crosshair", "Свой прицел: стили, цвет, размер, динамика и хит-маркер.", Category.VISUAL);
    }

    @Override
    public void onAttack(Entity target) {
        if (target instanceof LivingEntity) hitTime = System.currentTimeMillis();
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        int col = color.argb();
        if (targetColor.get() && mc.crosshairPickEntity instanceof LivingEntity) col = 0xFFFF4040;
        int gp = gap.asInt();
        if (dynamic.get()) {
            Vec3 v = mc.player.getDeltaMovement();
            gp += (int) Math.min(6, Math.sqrt(v.x * v.x + v.z * v.z) * 14);
        }
        int sz = size.asInt(), th = thick.asInt(), half = th / 2;

        switch (style.get()) {
            case "Плюс" -> {
                arm(g, cx - half, cy - gp - sz, th, sz, col);
                arm(g, cx - half, cy + gp, th, sz, col);
                arm(g, cx - gp - sz, cy - half, sz, th, col);
                arm(g, cx + gp, cy - half, sz, th, col);
            }
            case "Крест" -> {
                for (int i = gp; i < gp + sz; i++) {
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sy = -1; sy <= 1; sy += 2) {
                            int px = cx + sx * i, py = cy + sy * i;
                            if (outline.get()) Draw.rect(g, px - 1, py - 1, px + th + 1, py + th + 1, 0xFF000000);
                        }
                    }
                }
                for (int i = gp; i < gp + sz; i++) {
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sy = -1; sy <= 1; sy += 2) {
                            Draw.rect(g, cx + sx * i, cy + sy * i, cx + sx * i + th, cy + sy * i + th, col);
                        }
                    }
                }
            }
            case "Точка" -> {
                int d = th + 1;
                if (outline.get()) Draw.rect(g, cx - d / 2 - 1, cy - d / 2 - 1, cx - d / 2 + d + 1, cy - d / 2 + d + 1, 0xFF000000);
                Draw.rect(g, cx - d / 2, cy - d / 2, cx - d / 2 + d, cy - d / 2 + d, col);
            }
            case "Круг" -> {
                double r = gp + sz / 2.0 + 2;
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    int px = (int) Math.round(cx + Math.cos(a) * r), py = (int) Math.round(cy + Math.sin(a) * r);
                    if (outline.get()) Draw.rect(g, px - 1, py - 1, px + th + 1, py + th + 1, 0xFF000000);
                }
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    int px = (int) Math.round(cx + Math.cos(a) * r), py = (int) Math.round(cy + Math.sin(a) * r);
                    Draw.rect(g, px, py, px + th, py + th, col);
                }
            }
            default -> {}
        }

        if (hitMarker.get()) {
            long age = System.currentTimeMillis() - hitTime;
            if (age < 260) {
                int alpha = (int) (255 * (1.0 - age / 260.0));
                int c = Draw.withAlpha(0xFFFFFF, alpha);
                for (int i = 4; i < 10; i++) {
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sy = -1; sy <= 1; sy += 2) {
                            Draw.rect(g, cx + sx * i, cy + sy * i, cx + sx * i + 1, cy + sy * i + 1, c);
                        }
                    }
                }
            }
        }
    }

    private void arm(GuiGraphics g, int x, int y, int w, int h, int col) {
        if (outline.get()) Draw.rect(g, x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        Draw.rect(g, x, y, x + w, y + h, col);
    }
}
