package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.util.ArrayList;
import java.util.List;

public class Trails extends Module {
    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет следа. ПКМ - радужный.", 0x8A63FF));
    private final NumSetting life = add(new NumSetting("Длина (сек)", "Сколько секунд след остаётся за тобой.", 2.0, 0.5, 6.0, 0.5));

    private final List<double[]> pts = new ArrayList<>();

    public Trails() {
        super("Trails", "Светящийся след за тобой, пока ты двигаешься.", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        pts.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        double x = mc.player.getX(), y = mc.player.getY() + 0.1, z = mc.player.getZ();
        if (pts.isEmpty() || dist(pts.get(pts.size() - 1), x, y, z) > 0.1) {
            if (!pts.isEmpty() && dist(pts.get(pts.size() - 1), x, y, z) > 8) pts.clear(); // телепорт
            pts.add(new double[]{x, y, z, now});
        }
        long max = (long) (life.get() * 1000);
        pts.removeIf(p -> now - p[3] > max);
    }

    private static double dist(double[] p, double x, double y, double z) {
        double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        long now = System.currentTimeMillis();
        double max = life.get() * 1000.0;
        double[] a = new double[3], b = new double[3];
        boolean have = false;
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            boolean ok = Projection.project(p[0], p[1], p[2], b);
            if (ok && have) {
                int alpha = (int) ((1.0 - (now - p[3]) / max) * 230);
                Draw.dottedLine(g, a[0], a[1], b[0], b[1], Draw.withAlpha(color.argb(i * 0.02), alpha), 2);
            }
            if (ok) {
                a[0] = b[0];
                a[1] = b[1];
            }
            have = ok;
        }
    }
}
