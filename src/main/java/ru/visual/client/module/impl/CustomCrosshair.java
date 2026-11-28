package ru.visual.client.module.impl;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Свой прицел. Рисуется ВМЕСТО ванильного (через замену элемента HUD в NoRender), поэтому:
 * - пропадает от 3-го лица, в F1, в режиме наблюдателя и при 3D-прицеле F3 - как обычный прицел;
 * - размеры в настоящих пикселях экрана: линии чёткие при любом "Масштабе интерфейса" и строго симметричные.
 * Раньше рисовался в onHud поверх всего, в единицах GUI (толщина 1 = 2-4 пикселя), руки "Плюса" были
 * несимметричны на 1 пиксель, обводка одной линии перекрывала другую, "Крест" и "Круг" были из квадратиков.
 */
public class CustomCrosshair extends Module {
    private final ModeSetting style = add(new ModeSetting("Стиль", "Вид прицела.", "Плюс", "Плюс", "Т", "Крест", "Точка", "Круг"));
    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет прицела. ПКМ - радужный.", 0xFFFFFF));
    private final NumSetting size = add(new NumSetting("Длина", "Длина линий в пикселях экрана.", 8, 2, 30, 1));
    private final NumSetting gap = add(new NumSetting("Зазор", "Расстояние от центра до линий в пикселях экрана.", 4, 0, 20, 1));
    private final NumSetting thick = add(new NumSetting("Толщина", "Толщина линий в пикселях экрана.", 2, 1, 6, 1));
    private final BoolSetting centerDot = add(new BoolSetting("Точка в центре", "Добавить точку в центр (для Плюса, Т, Креста и Круга).", false));
    private final BoolSetting outline = add(new BoolSetting("Обводка", "Тёмная обводка - прицел видно и на снегу, и на небе.", true));
    private final BoolSetting dynamic = add(new BoolSetting("Динамический", "Прицел плавно расходится при движении и прыжке.", false));
    private final BoolSetting hitMarker = add(new BoolSetting("Хит-маркер", "Крестик вокруг прицела при попадании по существу.", true));
    private final BoolSetting targetColor = add(new BoolSetting("Красный на цели", "Прицел краснеет, когда наведён на существо.", true));
    private final BoolSetting attackBar = add(new BoolSetting("Перезарядка удара", "Полоска под прицелом, пока оружие перезаряжается (если в настройках игры индикатор = \"Прицел\").", true));

    private long hitTime;
    private float spread;
    private long lastFrame;

    public CustomCrosshair() {
        super("Crosshair", "Свой прицел: стили, цвет, размер, динамика и хит-маркер.", Category.VISUAL);
    }

    @Override
    public void onAttack(Entity target) {
        if (target instanceof LivingEntity) hitTime = System.currentTimeMillis();
    }

    /** Вызывается вместо ванильного прицела. */
    public void renderInPlace(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        if (mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) return;

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float wantSpread = 0f;
        if (dynamic.get()) {
            Vec3 v = mc.player.getDeltaMovement();
            wantSpread = (float) Math.min(8.0, Math.sqrt(v.x * v.x + v.z * v.z) * 25.0);
            if (!mc.player.onGround()) wantSpread = Math.max(wantSpread, 6f);
        }
        spread += (wantSpread - spread) * Math.min(1f, dt * 12f);

        int col = color.argb() | 0xFF000000;
        if (targetColor.get() && mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive()) col = 0xFFFF4040;

        int scale = Math.max(1, mc.getWindow().getGuiScale());
        int cx = mc.getWindow().getWidth() / 2, cy = mc.getWindow().getHeight() / 2;

        g.pose().pushMatrix();
        g.pose().scale(1f / scale, 1f / scale);
        try {
            int th = thick.asInt(), len = size.asInt(), gp = gap.asInt() + Math.round(spread);
            int c0x = cx - th / 2, c0y = cy - th / 2; // центральный квадрат th x th: [c0, c0 + th)
            String st = style.get();
            if (st.equals("Точка")) {
                int d = th + 2;
                int x0 = cx - d / 2, y0 = cy - d / 2;
                box(g, x0, y0, x0 + d, y0 + d, col, true);
                box(g, x0, y0, x0 + d, y0 + d, col, false);
            } else if (st.equals("Круг")) {
                ring(g, cx, cy, gp + len / 2.0 + th, th, col);
                if (centerDot.get()) dot(g, c0x, c0y, th, col);
            } else if (st.equals("Крест")) {
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().rotate((float) Math.toRadians(45));
                g.pose().translate(-cx, -cy);
                plus(g, c0x, c0y, th, len, gp, col, true);
                g.pose().popMatrix();
                if (centerDot.get()) dot(g, c0x, c0y, th, col);
            } else {
                plus(g, c0x, c0y, th, len, gp, col, !st.equals("Т"));
                if (centerDot.get()) dot(g, c0x, c0y, th, col);
            }

            if (hitMarker.get()) {
                long age = now - hitTime;
                if (age < 300) {
                    int a = (int) (255 * (1.0 - age / 300.0));
                    int hc = (a << 24) | 0xFFFFFF;
                    g.pose().pushMatrix();
                    g.pose().translate(cx, cy);
                    g.pose().rotate((float) Math.toRadians(45));
                    g.pose().translate(-cx, -cy);
                    int hgap = gp + len + 3, hlen = Math.max(4, len / 2 + 2);
                    plusArms(g, c0x, c0y, th, hlen, hgap, hc, true, false);
                    g.pose().popMatrix();
                }
            }

            if (attackBar.get() && mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
                float k = mc.player.getAttackStrengthScale(0f);
                if (k < 1f) {
                    int w = 16 * scale, h = Math.max(2, scale);
                    int x = cx - w / 2, y = cy + th / 2 + Math.max(gp + len, 4 * scale) + 3 * scale;
                    g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xA0000000);
                    g.fill(x, y, x + (int) (w * k), y + h, col);
                }
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    /** "Плюс": сначала вся обводка, потом цвет - линии не перекрывают друг друга. */
    private void plus(GuiGraphics g, int c0x, int c0y, int th, int len, int gp, int col, boolean top) {
        if (outline.get()) plusArms(g, c0x, c0y, th, len, gp, col, top, true);
        plusArms(g, c0x, c0y, th, len, gp, col, top, false);
    }

    private void plusArms(GuiGraphics g, int c0x, int c0y, int th, int len, int gp, int col, boolean top, boolean shadow) {
        int c1x = c0x + th, c1y = c0y + th;
        if (top) box(g, c0x, c0y - gp - len, c1x, c0y - gp, col, shadow);
        box(g, c0x, c1y + gp, c1x, c1y + gp + len, col, shadow);
        box(g, c0x - gp - len, c0y, c0x - gp, c1y, col, shadow);
        box(g, c1x + gp, c0y, c1x + gp + len, c1y, col, shadow);
    }

    private void dot(GuiGraphics g, int c0x, int c0y, int th, int col) {
        box(g, c0x, c0y, c0x + th, c0y + th, col, true);
        box(g, c0x, c0y, c0x + th, c0y + th, col, false);
    }

    private void box(GuiGraphics g, int x1, int y1, int x2, int y2, int col, boolean shadow) {
        if (shadow) {
            if (outline.get()) g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, ((col >>> 24) * 0xB0 / 255) << 24);
        } else {
            g.fill(x1, y1, x2, y2, col);
        }
    }

    /** Кольцо: попиксельно, отрезками по строкам (ровное при любом радиусе). */
    private void ring(GuiGraphics g, int cx, int cy, double r, int th, int col) {
        double ro = r + th / 2.0, ri = r - th / 2.0;
        if (outline.get()) ringRows(g, cx, cy, ro + 1, Math.max(0, ri - 1), 0xB0000000);
        ringRows(g, cx, cy, ro, ri, col);
    }

    private static void ringRows(GuiGraphics g, int cx, int cy, double ro, double ri, int col) {
        int R = (int) Math.ceil(ro);
        for (int dy = -R; dy < R; dy++) {
            double y = dy + 0.5;
            double xo2 = ro * ro - y * y;
            if (xo2 <= 0) continue;
            int xo = (int) Math.round(Math.sqrt(xo2));
            double xi2 = ri * ri - y * y;
            if (xi2 <= 0) {
                g.fill(cx - xo, cy + dy, cx + xo, cy + dy + 1, col);
            } else {
                int xi = (int) Math.round(Math.sqrt(xi2));
                if (xi >= xo) continue;
                g.fill(cx - xo, cy + dy, cx - xi, cy + dy + 1, col);
                g.fill(cx + xi, cy + dy, cx + xo, cy + dy + 1, col);
            }
        }
    }
}
