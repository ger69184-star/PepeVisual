package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;

import java.util.List;

/** Лента компаса вверху экрана: стороны света, градусы и метки Waypoints. */
public class Compass extends HudModule {
    private static final String[] NAMES = {"Ю", "ЮЗ", "З", "СЗ", "С", "СВ", "В", "ЮВ"};

    private final NumSetting width = add(new NumSetting("Ширина", "Ширина ленты компаса в пикселях.", 200, 100, 400, 10));
    private final NumSetting fov = add(new NumSetting("Обзор (градусы)", "Сколько градусов помещается на ленте.", 160, 60, 360, 10));
    private final BoolSetting waypoints = add(new BoolSetting("Метки", "Показывать метки из Waypoints на ленте.", true));
    private final BoolSetting degrees = add(new BoolSetting("Градусы", "Показывать текущее направление в градусах.", true));

    public Compass() {
        super("Compass", "Лента компаса вверху экрана: стороны света, направление в градусах и метки Waypoints.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int sw = mc.getWindow().getGuiScaledWidth();
        int w = width.asInt(), h = 16;
        int x1 = sw / 2 - w / 2 + ox(), y1 = 4 + oy(), x2 = x1 + w, cx = (x1 + x2) / 2;
        float yaw = Mth.wrapDegrees(mc.player.getViewYRot(pt));
        double span = fov.get();
        double ppd = w / span; // пикселей на градус

        Draw.panel(g, x1, y1, x2, y1 + h, Draw.bg());
        int accent = Draw.accent();
        g.enableScissor(x1 + 2, y1, x2 - 2, y1 + h);
        try {
            for (int deg = 0; deg < 360; deg += 15) {
                double diff = Mth.wrapDegrees(deg - yaw);
                if (Math.abs(diff) > span / 2 + 10) continue;
                int x = (int) Math.round(cx + diff * ppd);
                float edge = 1f - (float) Math.min(1, Math.abs(diff) / (span / 2));
                int alpha = (int) (60 + 195 * edge);
                if (deg % 45 == 0) {
                    String n = NAMES[deg / 45];
                    boolean main = deg % 90 == 0;
                    int col = n.equals("С") ? 0xFFFF5050 : main ? 0xFFFFFFFF : 0xFFB8B8C8;
                    Draw.textCentered(g, n, x, y1 + 4, Draw.withAlpha(col, alpha));
                } else {
                    g.fill(x, y1 + 5, x + 1, y1 + 11, Draw.withAlpha(0xFFFFFF, alpha / 2));
                }
            }
            if (waypoints.get() && mc.level != null) {
                List<Waypoints.Point> list;
                synchronized (Waypoints.class) {
                    list = new java.util.ArrayList<>(Waypoints.current(mc));
                }
                for (Waypoints.Point p : list) {
                    double dx = p.x() - mc.player.getX(), dz = p.z() - mc.player.getZ();
                    double ang = Math.toDegrees(Math.atan2(-dx, dz));
                    double diff = Mth.wrapDegrees(ang - yaw);
                    if (Math.abs(diff) > span / 2) continue;
                    int x = (int) Math.round(cx + diff * ppd);
                    Draw.circle(g, x - 3, y1 + h - 6, 5, p.color() | 0xFF000000);
                }
            }
        } finally {
            g.disableScissor(); // иначе обрезался бы весь HUD после компаса
        }
        // указатель по центру
        g.fill(cx, y1 + h - 3, cx + 1, y1 + h, accent);
        Draw.round(g, cx - 3, y1 - 1, cx + 4, y1 + 1, 1, accent);
        if (degrees.get()) {
            int d = Math.floorMod(Math.round(yaw) + 180, 360); // 0 = север
            Draw.textCentered(g, d + "°", cx, y1 + h + 3, Draw.withAlpha(0xFFFFFF, 200));
        }
    }
}
