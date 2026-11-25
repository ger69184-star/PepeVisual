package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JumpCircles extends Module {
    private record Circle(double x, double y, double z, long t) {}

    private final BoolSetting others = add(new BoolSetting("Другие игроки", "Рисовать круги и под другими игроками, не только под тобой.", true));
    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет круга. ПКМ - радужный.", 0x8A63FF));
    private final NumSetting radius = add(new NumSetting("Радиус", "Максимальный радиус круга в блоках.", 1.6, 0.8, 4.0, 0.1));
    private final NumSetting duration = add(new NumSetting("Длительность (сек)", "Сколько секунд круг расходится и гаснет.", 1.0, 0.4, 3.0, 0.1));

    private final List<Circle> circles = new ArrayList<>();
    private final Map<Integer, Boolean> ground = new HashMap<>();

    public JumpCircles() {
        super("JumpCircles", "Расходящийся круг под ногами в момент прыжка.", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        circles.clear();
        ground.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player p) || p.isRemoved()) continue;
            if (!others.get() && p != mc.player) continue;
            boolean on = p.onGround();
            Boolean prev = ground.put(p.getId(), on);
            if (prev != null && prev && !on && p.getDeltaMovement().y > 0.1) {
                circles.add(new Circle(p.getX(), p.getY() + 0.03, p.getZ(), now));
            }
        }
        if (ground.size() > 300) ground.clear();
        long life = (long) (duration.get() * 1000);
        circles.removeIf(c -> now - c.t > life);
        while (circles.size() > 40) circles.remove(0);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        long now = System.currentTimeMillis();
        double life = duration.get() * 1000.0;
        double[] o = new double[3];
        for (Circle c : circles) {
            double age = Math.min(1.0, (now - c.t) / life);
            double r = 0.3 + age * radius.get();
            int alpha = (int) ((1.0 - age) * 255);
            int n = 28;
            for (int i = 0; i < n; i++) {
                double a = (double) i / n * Math.PI * 2;
                if (!Projection.project(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, o)) continue;
                Draw.dot(g, o[0], o[1], 2, Draw.withAlpha(color.argb((double) i / n), alpha));
            }
        }
    }
}
