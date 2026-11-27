package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Мелкие помощники для рисования. Все цвета - ARGB (0xAARRGGBB). */
public final class Draw {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;

    /** HUD-модуль, который сейчас рисуется: его область собирается автоматически (для редактора HUD). */
    public static ru.visual.client.module.HudModule tracking;

    private Draw() {}

    private static void track(int x1, int y1, int x2, int y2) {
        if (tracking != null) tracking.track(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
    }

    /** Предмет-иконка 16x16 (с учётом области для редактора HUD). */
    public static void item(GuiGraphics g, net.minecraft.world.item.ItemStack st, int x, int y) {
        g.renderItem(st, x, y);
        g.renderItemDecorations(Minecraft.getInstance().font, st, x, y);
        track(x, y, x + 16, y + 16);
    }

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

    // ---------- шрифт ----------

    private static final Style[] UI = new Style[7], BOLD = new Style[7];
    private static final Identifier CIRCLE = Identifier.fromNamespaceAndPath("pepevisual", "textures/gui/circle.png");
    private static final Identifier SHADOW = Identifier.fromNamespaceAndPath("pepevisual", "textures/gui/shadow.png");

    static {
        // Отдельный шрифт под каждый масштаб интерфейса: буквы растеризуются ровно в пиксели экрана и не "плывут".
        for (int i = 1; i <= 6; i++) {
            UI[i] = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("pepevisual", "ui" + i)));
            BOLD[i] = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("pepevisual", "bold" + i)));
        }
    }

    /** Меню выключает тень текста (на гладком шрифте она выглядит грязно). */
    public static boolean noShadow;

    private static int scale() {
        return Mth.clamp(Minecraft.getInstance().getWindow().getGuiScale(), 1, 6);
    }

    /** Текст в текущем шрифте клиента (гладкий Inter или стандартный). */
    public static Component comp(String s) {
        return comp(s, Theme.boldFont());
    }

    public static Component comp(String s, boolean bold) {
        MutableComponent c = Component.literal(Lang.t(s));
        if (!Theme.smoothFont()) return bold ? c.withStyle(net.minecraft.ChatFormatting.BOLD) : c;
        return c.setStyle(bold ? BOLD[scale()] : UI[scale()]);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static boolean shadow() {
        return !noShadow && Theme.textShadow();
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        if (s == null) return;
        s = Lang.t(s);
        if (Theme.smoothFont()) g.drawString(font(), comp(s), x, y, color, shadow());
        else g.drawString(font(), s, x, y, color, shadow());
        track(x, y, x + width(s), y + 9);
    }

    /** Жирный текст (заголовки). */
    public static void textBold(GuiGraphics g, String s, int x, int y, int color) {
        s = Lang.t(s);
        g.drawString(font(), comp(s, true), x, y, color, shadow());
        track(x, y, x + widthBold(s), y + 9);
    }

    public static int widthBold(String s) {
        s = Lang.t(s);
        return font().width(comp(s, true));
    }

    public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
        text(g, s, cx - width(s) / 2, y, color);
    }

    public static void textRight(GuiGraphics g, String s, int rightX, int y, int color) {
        text(g, s, rightX - width(s), y, color);
    }

    public static int width(String s) {
        if (s == null) return 0;
        s = Lang.t(s);
        return Theme.smoothFont() ? font().width(comp(s)) : font().width(s);
    }

    /** Обрезает строку с многоточием под ширину. */
    public static String trim(String s, int maxWidth) {
        s = Lang.t(s);
        if (width(s) <= maxWidth) return s;
        while (s.length() > 1 && width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    // ---------- скруглённые фигуры ----------

    /** Текущий радиус скругления из Interface. */
    public static int r() {
        return Theme.radius();
    }

    /** Плашка HUD: скругление и тень берутся из Interface. */
    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        panel(g, x1, y1, x2, y2, color, true);
    }

    /** То же, но можно без тени (для плашек, стоящих вплотную друг к другу). */
    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, int color, boolean withShadow) {
        if (withShadow && Theme.shadows()) shadow(g, x1, y1, x2, y2, 4, Math.min(0x70, (color >>> 24) / 2) << 24);
        round(g, x1, y1, x2, y2, Theme.radius(), color);
        track(x1, y1, x2, y2);
    }

    /** Прямоугольник со скруглёнными углами (сглаженными, через текстуру круга). */
    public static void round(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        g.fill(x1 + r, y1, x2 - r, y2, color);
        g.fill(x1, y1 + r, x1 + r, y2 - r, color);
        g.fill(x2 - r, y1 + r, x2, y2 - r, color);
        corner(g, x1, y1, r, 0, 0, color);
        corner(g, x2 - r, y1, r, 16, 0, color);
        corner(g, x1, y2 - r, r, 0, 16, color);
        corner(g, x2 - r, y2 - r, r, 16, 16, color);
    }

    /** Скруглённый прямоугольник с вертикальным градиентом. */
    public static void roundGradient(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int top, int bottom) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            g.fillGradient(x1, y1, x2, y2, top, bottom);
            return;
        }
        g.fill(x1 + r, y1, x2 - r, y1 + r, top);
        g.fill(x1 + r, y2 - r, x2 - r, y2, bottom);
        g.fillGradient(x1, y1 + r, x2, y2 - r, top, bottom);
        corner(g, x1, y1, r, 0, 0, top);
        corner(g, x2 - r, y1, r, 16, 0, top);
        corner(g, x1, y2 - r, r, 0, 16, bottom);
        corner(g, x2 - r, y2 - r, r, 16, 16, bottom);
    }

    /** Скруглённая рамка толщиной 1. */
    public static void roundOutline(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        g.fill(x1 + r, y1, x2 - r, y1 + 1, color);
        g.fill(x1 + r, y2 - 1, x2 - r, y2, color);
        g.fill(x1, y1 + r, x1 + 1, y2 - r, color);
        g.fill(x2 - 1, y1 + r, x2, y2 - r, color);
        if (r > 0) {
            // углы: внешняя четверть круга минус внутренняя дают тонкую дугу; для 1px достаточно точек
            for (int i = 0; i < r; i++) {
                double a = Math.PI / 2 * (i + 0.5) / r;
                int dx = (int) Math.round((1 - Math.cos(a)) * (r - 0.5));
                int dy = (int) Math.round((1 - Math.sin(a)) * (r - 0.5));
                g.fill(x1 + dx, y1 + dy, x1 + dx + 1, y1 + dy + 1, color);
                g.fill(x2 - dx - 1, y1 + dy, x2 - dx, y1 + dy + 1, color);
                g.fill(x1 + dx, y2 - dy - 1, x1 + dx + 1, y2 - dy, color);
                g.fill(x2 - dx - 1, y2 - dy - 1, x2 - dx, y2 - dy, color);
            }
        }
    }

    private static void corner(GuiGraphics g, int x, int y, int r, int u, int v, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE, x, y, u, v, r, r, 16, 16, 32, 32, color);
    }

    /** Сглаженный круг диаметром d с левым верхним углом в (x, y). */
    public static void circle(GuiGraphics g, int x, int y, int d, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE, x, y, 0, 0, d, d, 32, 32, 32, 32, color);
    }

    /** Мягкая тень/свечение вокруг прямоугольника (s - размытие в пикселях). */
    public static void shadow(GuiGraphics g, int x1, int y1, int x2, int y2, int s, int color) {
        if (s <= 0 || (color >>> 24) == 0) return;
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        var p = RenderPipelines.GUI_TEXTURED;
        g.blit(p, SHADOW, x1 - s, y1 - s, 0, 0, s, s, 32, 32, 64, 64, color);
        g.blit(p, SHADOW, x2, y1 - s, 32, 0, s, s, 32, 32, 64, 64, color);
        g.blit(p, SHADOW, x1 - s, y2, 0, 32, s, s, 32, 32, 64, 64, color);
        g.blit(p, SHADOW, x2, y2, 32, 32, s, s, 32, 32, 64, 64, color);
        g.blit(p, SHADOW, x1, y1 - s, 31, 0, w, s, 2, 32, 64, 64, color);
        g.blit(p, SHADOW, x1, y2, 31, 32, w, s, 2, 32, 64, 64, color);
        g.blit(p, SHADOW, x1 - s, y1, 0, 31, s, h, 32, 2, 64, 64, color);
        g.blit(p, SHADOW, x2, y1, 32, 31, s, h, 32, 2, 64, 64, color);
    }

    /** Плавное смешивание двух ARGB цветов. */
    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
        track(x1, y1, x2, y2);
    }

    public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
        track(x1, y1, x2, y2);
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

    /**
     * Верхний край ванильных полосок над хотбаром (сердца, броня, голод, воздух) в координатах GUI.
     * HUD-элементы по умолчанию ставятся выше этой линии, чтобы не налезать на хотбар.
     */
    public static int hudTop(Minecraft mc) {
        int h = mc.getWindow().getGuiScaledHeight();
        if (mc.player == null || mc.options.hideGui) return h;
        if (mc.player.isSpectator()) return h - 24;
        if (mc.player.isCreative()) return h - 30;
        float hp = mc.player.getMaxHealth() + mc.player.getAbsorptionAmount();
        int rows = Math.max(1, (int) Math.ceil(hp / 20f));
        int rowH = Math.max(10 - (rows - 2), 3);
        int top = h - 39 - (rows - 1) * rowH;
        if (mc.player.getArmorValue() > 0) top -= 10;
        // справа: голод (h-39) и пузырьки воздуха / сердца транспорта (h-49)
        if (mc.player.getAirSupply() < mc.player.getMaxAirSupply() || mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                || mc.player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity) top = Math.min(top, h - 49);
        return top - 1;
    }

    /** Подпись-"таблетка": текст по центру cx на тёмной скруглённой подложке. Возвращает {x1, y1, x2, y2}. */
    public static int[] pill(GuiGraphics g, String s, int cx, int y, int textColor) {
        int tw = width(s);
        int x1 = cx - tw / 2 - 3, x2 = cx + (tw + 1) / 2 + 3;
        int a = textColor >>> 24;
        round(g, x1, y - 2, x2, y + 10, 3, withAlpha(0x0E0E14, a * 0xA8 / 255));
        text(g, s, cx - tw / 2, y, textColor);
        return new int[]{x1, y - 2, x2, y + 10};
    }

    /** Уголки рамки (аккуратнее сплошного прямоугольника). */
    public static void corners(GuiGraphics g, int x1, int y1, int x2, int y2, int col) {
        int lx = Math.max(2, (x2 - x1) / 3), ly = Math.max(2, (y2 - y1) / 3);
        g.fill(x1, y1, x1 + lx, y1 + 1, col);
        g.fill(x1, y1, x1 + 1, y1 + ly, col);
        g.fill(x2 - lx, y1, x2, y1 + 1, col);
        g.fill(x2 - 1, y1, x2, y1 + ly, col);
        g.fill(x1, y2 - 1, x1 + lx, y2, col);
        g.fill(x1, y2 - ly, x1 + 1, y2, col);
        g.fill(x2 - lx, y2 - 1, x2, y2, col);
        g.fill(x2 - 1, y2 - ly, x2, y2, col);
        track(x1, y1, x2, y2);
    }

    /** Масштабированный рисунок вокруг точки (cx, cy). */
    public static void scaled(GuiGraphics g, float scale, float cx, float cy, Runnable r) {
        if (Math.abs(scale - 1f) < 0.01f) {
            r.run();
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);
        r.run();
        g.pose().popMatrix();
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
