package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.TargetManager;

public class TargetEsp extends Module implements Config.DefaultOn {
    private final BoolSetting ring = add(new BoolSetting("Кольцо", "Анимированное кольцо, которое бегает вверх-вниз вокруг цели.", true));
    private final BoolSetting corners = add(new BoolSetting("Уголки", "Красные уголки вокруг цели на экране.", true));
    private final BoolSetting hpColor = add(new BoolSetting("Цвет по HP", "Цвет кольца зависит от здоровья цели. Выкл = радужное.", false));
    private final NumSetting speed = add(new NumSetting("Скорость", "Скорость вращения и движения кольца.", 1.0, 0.2, 3.0, 0.1));

    public TargetEsp() {
        super("TargetESP", "Выделяет текущую цель (на кого смотришь или кого ударил): кольцо и уголки.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        LivingEntity t = TargetManager.get();
        if (t == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        Vec3 pos = t.getPosition(pt);
        double w = t.getBbWidth(), h = t.getBbHeight();
        double time = System.currentTimeMillis() / 1000.0 * speed.get();

        if (ring.get()) {
            double[] o = new double[3];
            int n = 28;
            double radius = w * 0.75 + 0.15;
            for (int layer = 0; layer < 3; layer++) {
                double phase = time * 2.0 - layer * 0.35;
                double y = pos.y + h * (0.5 + 0.5 * Math.sin(phase));
                int alpha = layer == 0 ? 255 : (layer == 1 ? 140 : 70);
                for (int i = 0; i < n; i++) {
                    double ang = (double) i / n * Math.PI * 2 + time * 1.5;
                    double x = pos.x + Math.cos(ang) * radius;
                    double z = pos.z + Math.sin(ang) * radius;
                    if (!Projection.project(x, y, z, o)) continue;
                    int base = hpColor.get() ? Draw.healthColor(t.getHealth(), t.getMaxHealth()) : Draw.rainbow(0.3, (double) i / n);
                    Draw.dot(g, o[0], o[1], layer == 0 ? 3 : 2, Draw.withAlpha(base, alpha));
                }
            }
        }

        if (corners.get()) {
            double hw = w / 2.0;
            double[] r = Projection.boxRect(new AABB(pos.x - hw, pos.y, pos.z - hw, pos.x + hw, pos.y + h, pos.z + hw));
            if (r != null) {
                int x1 = (int) r[0] - 3, y1 = (int) r[1] - 3, x2 = (int) r[2] + 3, y2 = (int) r[3] + 3;
                int len = Math.max(4, Math.min(12, (x2 - x1) / 4));
                int c = 0xFFFF3030;
                // верх-лево
                Draw.rect(g, x1, y1, x1 + len, y1 + 1, c);
                Draw.rect(g, x1, y1, x1 + 1, y1 + len, c);
                // верх-право
                Draw.rect(g, x2 - len, y1, x2, y1 + 1, c);
                Draw.rect(g, x2 - 1, y1, x2, y1 + len, c);
                // низ-лево
                Draw.rect(g, x1, y2 - 1, x1 + len, y2, c);
                Draw.rect(g, x1, y2 - len, x1 + 1, y2, c);
                // низ-право
                Draw.rect(g, x2 - len, y2 - 1, x2, y2, c);
                Draw.rect(g, x2 - 1, y2 - len, x2, y2, c);
            }
        }
    }
}
