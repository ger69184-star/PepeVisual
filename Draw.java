package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Мелкие помощники для рисования. Все цвета - ARGB (0xAARRGGBB). */
public final class Draw {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;

    private Draw() {}

    /** Основной цвет темы. */
    public static int accent() {
        return Theme.active() ? Theme.accent.argb() : 0xFF8A63FF;
    }

    /** Фон плашек HUD (прозрачность настраивается в Interface). */
    public static int bg() {
        int a = Theme.active() ? (int) (Theme.opacity.get() * 255) : 0xC0;
        return (Math.max(0, Math.min(255, a)) << 24) | 0x101018;
    }

    /** Цвет акцентной полоски: радужный или основной, i - сдвиг для градиента. */
    public static int bar(double i) {
        if (Theme.active() && Theme.rainbowBars.get()) return rainbow(Theme.rainbowSpeed.get(), i * 0.07);
        return accent();
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, s, x, y, color);
    }

    public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
        text(g, s, cx - width(s) / 2, y, color);
    }

    public static void textRight(GuiGraphics g, String s, int rightX, int y, int color) {
        text(g, s, rightX - width(s), y, color);
    }

    public static int width(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    public static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    public static void dot(GuiGraphics g, double x, double y, int size, int color) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y);
        int h = size / 2;
        g.fill(ix - h, iy - h, ix - h + size, iy - h + size, color);
    }

    /** Пунктирная линия (gap - расстояние между точками в пикселях). */
    public static void dottedLine(GuiGraphics g, double x0, double y0, double x1, double y1, int color, int gap) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        int n = (int) Math.min(len / Math.max(1, gap), 400);
        int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            double x = x0 + dx * t, y = y0 + dy * t;
            if (x < -2 || y < -2 || x > w + 2 || y > h + 2) continue;
            dot(g, x, y, 2, color);
        }
    }

    /** Цвет здоровья: зелёный -> красный. */
    public static int healthColor(float hp, float max) {
        float f = Mth.clamp(hp / Math.max(1f, max), 0f, 1f);
        return 0xFF000000 | Mth.hsvToRgb(f * 0.33f, 0.85f, 1f);
    }

    /** Затемняет цвет (f < 1) или осветляет (f > 1), сохраняя альфу. */
    public static int shade(int argb, float f) {
        int a = argb >>> 24;
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((argb & 255) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int rainbow(double speed, double offset) {
        float h = (float) (((System.currentTimeMillis() * speed / 1000.0) + offset) % 1.0);
        if (h < 0) h += 1f;
        return 0xFF000000 | Mth.hsvToRgb(h, 0.6f, 1f);
    }
}
