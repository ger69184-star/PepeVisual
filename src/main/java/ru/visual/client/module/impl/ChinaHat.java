package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

/** "Китайская шляпа" - конус над головой (видно от 3-го лица и на других игроках). */
public class ChinaHat extends Module {
    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет шляпы. ПКМ - радуга.", 0x8A63FF));
    private final NumSetting radius = add(new NumSetting("Радиус", "Ширина шляпы в блоках.", 0.65, 0.3, 1.2, 0.05));
    private final NumSetting height = add(new NumSetting("Высота", "Высота конуса в блоках.", 0.3, 0.1, 0.8, 0.05));
    private final BoolSetting others = add(new BoolSetting("Другим игрокам", "Рисовать шляпу и на других игроках.", false));
    private final BoolSetting spin = add(new BoolSetting("Вращение", "Шляпа медленно вращается.", true));

    public ChinaHat() {
        super("ChinaHat", "Конус-шляпа над головой (как China Hat в визуальных клиентах). Видно от 3-го лица.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player p) || p.isInvisible()) continue;
            if (p == mc.player) {
                if (mc.options.getCameraType().isFirstPerson()) continue;
            } else if (!others.get() || mc.player.distanceTo(p) > 48) continue;
            draw(g, p, pt);
        }
    }

    private void draw(GuiGraphics g, Player p, float pt) {
        Vec3 pos = p.getPosition(pt);
        double baseY = pos.y + p.getBbHeight() + (p.isCrouching() ? -0.05 : 0.08);
        double r = radius.get(), hh = height.get();
        double rot = spin.get() ? (System.currentTimeMillis() % 6000) / 6000.0 * Math.PI * 2 : 0;
        double[] apex = new double[3], a = new double[3], b = new double[3];
        if (!Projection.project(pos.x, baseY + hh, pos.z, apex)) return;
        int n = 32;
        double[][] rim = new double[n][];
        for (int i = 0; i < n; i++) {
            double ang = rot + (double) i / n * Math.PI * 2;
            double[] o = new double[3];
            rim[i] = Projection.project(pos.x + Math.cos(ang) * r, baseY, pos.z + Math.sin(ang) * r, o) ? o : null;
        }
        for (int i = 0; i < n; i++) {
            double[] c = rim[i], d = rim[(i + 1) % n];
            int col = color.argb((double) i / n);
            if (c != null && d != null) Draw.dottedLine(g, c[0], c[1], d[0], d[1], col, 1);
            if (c != null && i % 2 == 0) Draw.dottedLine(g, apex[0], apex[1], c[0], c[1], Draw.withAlpha(col, 0x70), 2);
        }
    }
}
