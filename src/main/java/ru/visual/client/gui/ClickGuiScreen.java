package ru.visual.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import ru.visual.client.account.AccountScreen;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.setting.Setting;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Меню клиента (Right Shift). ЛКМ по функции - вкл/выкл, ПКМ - настройки и назначение клавиши.
 * При наведении на любую функцию или настройку рядом с курсором появляется описание.
 */
public class ClickGuiScreen extends Screen {
    private static final int HEAD = 16, ROW = 14, GAP = 6;

    private interface Drag {
        void apply(double mouseX, int x1, int x2);
    }

    private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right, Drag drag) {}

    private final Map<Category, Integer> scroll = new EnumMap<>(Category.class);
    private final Map<Category, int[]> panelRects = new EnumMap<>(Category.class);
    private final Set<Module> expanded = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private Drag dragging;
    private int dragX1, dragX2;
    private Module binding;
    private String tooltip;
    private int pw = 130;

    public ClickGuiScreen() {
        super(Component.literal("Pepe Visual"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        hits.clear();
        panelRects.clear();
        tooltip = null;

        g.fill(0, 0, this.width, this.height, 0x95000000);
        ru.visual.client.module.impl.PepeBackground.draw(g, this.width - 120, this.height - 120, 112, 0.45);
        Draw.textCentered(g, "Pepe Visual  |  ЛКМ - вкл/выкл  |  ПКМ - настройки и клавиша  |  наведи курсор, чтобы увидеть описание",
                this.width / 2, 7, 0xFFCCCCCC);

        Category[] cats = Category.values();
        pw = Math.max(88, Math.min(132, (this.width - 16 - (cats.length - 1) * GAP) / cats.length));
        int total = cats.length * pw + (cats.length - 1) * GAP;
        int x0 = (this.width - total) / 2;
        int top = 22;
        int panelMax = this.height - top - 34;
        for (int i = 0; i < cats.length; i++) {
            drawPanel(g, cats[i], x0 + i * (pw + GAP), top, panelMax, mouseX, mouseY);
        }

        int bw = 130, bh = 16, by = this.height - 24;
        bottomButton(g, this.width / 2 - bw - 4, by, bw, bh, mouseX, mouseY, "Оффлайн-аккаунты",
                "Открыть меню оффлайн-аккаунтов: сменить ник, добавить или удалить аккаунт.",
                () -> this.minecraft.setScreen(new AccountScreen(this)));
        bottomButton(g, this.width / 2 + 4, by, bw, bh, mouseX, mouseY, "Редактор HUD",
                "Двигать элементы HUD мышью прямо на экране (ПКМ по элементу - сбросить позицию).",
                () -> {
                    Config.save();
                    this.minecraft.setScreen(new HudEditorScreen(this));
                });

        if (tooltip != null) drawTooltip(g, mouseX, mouseY);
    }

    private void bottomButton(GuiGraphics g, int bx, int by, int bw, int bh, int mouseX, int mouseY, String label, String tip, Runnable action) {
        boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        Draw.rect(g, bx, by, bx + bw, by + bh, hov ? Draw.shade(Draw.accent(), 0.6f) : Draw.shade(Draw.accent(), 0.4f));
        Draw.textCentered(g, label, bx + bw / 2, by + 4, Draw.WHITE);
        hits.add(new Hit(bx, by, bx + bw, by + bh, action, null, null));
        if (hov) tooltip = tip;
    }

    private void drawPanel(GuiGraphics g, Category cat, int x, int top, int panelMax, int mx, int my) {
        List<Module> mods = ModuleManager.byCategory(cat);
        int content = 0;
        for (Module m : mods) {
            content += ROW;
            if (expanded.contains(m)) content += (m.settings.size() + 1) * ROW;
        }
        int visible = Math.min(content, panelMax - HEAD);
        int maxScroll = Math.max(0, content - visible);
        int sc = Math.max(0, Math.min(scroll.getOrDefault(cat, 0), maxScroll));
        scroll.put(cat, sc);

        int clipTop = top + HEAD, clipBottom = top + HEAD + visible;
        panelRects.put(cat, new int[]{x, top, x + pw, clipBottom});

        Draw.rect(g, x, top, x + pw, clipTop, Draw.shade(Draw.accent(), 0.45f));
        Draw.rect(g, x, top, x + pw, top + 1, Draw.accent());
        Draw.textCentered(g, cat.title, x + pw / 2, top + 4, Draw.WHITE);
        Draw.rect(g, x, clipTop, x + pw, clipBottom, 0xE0141420);

        g.enableScissor(x, clipTop, x + pw, clipBottom);
        int y = clipTop - sc;
        for (Module m : mods) {
            boolean vis = y + ROW > clipTop && y < clipBottom;
            boolean hover = inside(vis, mx, my, x, y, clipTop, clipBottom);
            if (m.isEnabled()) Draw.rect(g, x, y, x + pw, y + ROW, Draw.shade(Draw.accent(), 0.7f));
            else if (hover) Draw.rect(g, x, y, x + pw, y + ROW, 0xFF26263A);
            Draw.text(g, trim(m.name, pw - 22), x + 5, y + 3, m.isEnabled() ? Draw.WHITE : 0xFFB0B0B8);
            Draw.textRight(g, expanded.contains(m) ? "-" : "+", x + pw - 5, y + 3, 0xFF9A9AA8);
            if (vis) {
                final Module fm = m;
                hits.add(new Hit(x, Math.max(y, clipTop), x + pw, Math.min(y + ROW, clipBottom),
                        m::toggle, () -> toggleExpanded(fm), null));
            }
            if (hover) tooltip = m.description + " [ПКМ - настройки]";
            y += ROW;

            if (!expanded.contains(m)) continue;

            // строка назначения клавиши
            {
                boolean svis = y + ROW > clipTop && y < clipBottom;
                boolean shover = inside(svis, mx, my, x, y, clipTop, clipBottom);
                Draw.rect(g, x, y, x + pw, y + ROW, 0xFF1C1C2C);
                Draw.rect(g, x, y, x + 2, y + ROW, Draw.accent());
                String label = binding == m ? "Нажми клавишу..." : "Клавиша: " + ModuleManager.bindName(m);
                Draw.text(g, trim(label, pw - 10), x + 6, y + 3, binding == m ? 0xFFFFE070 : 0xFFD0D0E0);
                if (svis) {
                    final Module fm = m;
                    hits.add(new Hit(x, Math.max(y, clipTop), x + pw, Math.min(y + ROW, clipBottom),
                            () -> binding = fm, () -> ModuleManager.setBind(fm, -1), null));
                }
                if (shover) tooltip = "Клавиша быстрого включения функции. ЛКМ - назначить (затем нажми клавишу, Esc - отмена), ПКМ или Delete - сбросить.";
                y += ROW;
            }

            for (Setting<?> s : m.settings) {
                boolean svis = y + ROW > clipTop && y < clipBottom;
                boolean shover = inside(svis, mx, my, x, y, clipTop, clipBottom);
                int hy1 = Math.max(y, clipTop), hy2 = Math.min(y + ROW, clipBottom);
                Draw.rect(g, x, y, x + pw, y + ROW, 0xFF1C1C2C);
                Draw.rect(g, x, y, x + 2, y + ROW, Draw.accent());
                if (s instanceof BoolSetting b) {
                    Draw.text(g, trim(b.name, pw - 24), x + 6, y + 3, b.get() ? Draw.WHITE : 0xFF9A9AA8);
                    int bx = x + pw - 14;
                    Draw.outline(g, bx, y + 3, bx + 8, y + 11, 0xFF9A9AA8);
                    if (b.get()) Draw.rect(g, bx + 2, y + 5, bx + 6, y + 9, Draw.accent());
                    if (svis) hits.add(new Hit(x, hy1, x + pw, hy2, b::toggle, null, null));
                } else if (s instanceof NumSetting n) {
                    double f = (n.get() - n.min) / (n.max - n.min);
                    Draw.rect(g, x + 2, y, x + 2 + (int) ((pw - 2) * f), y + ROW, Draw.shade(Draw.accent(), 0.55f));
                    Draw.text(g, trim(n.name, pw - 40), x + 6, y + 3, Draw.WHITE);
                    Draw.textRight(g, n.display(), x + pw - 4, y + 3, 0xFFD8D0FF);
                    if (svis) {
                        Drag d = (mouseX, x1, x2) -> {
                            // Полоса рисуется от x1 + 2 до x2 - те же границы, что и при отрисовке
                            double k = Mth.clamp((mouseX - (x1 + 2)) / (double) (x2 - x1 - 2), 0, 1);
                            n.set(n.min + k * (n.max - n.min));
                        };
                        hits.add(new Hit(x, hy1, x + pw, hy2, null, null, d));
                    }
                } else if (s instanceof ModeSetting md) {
                    Draw.text(g, trim(md.name + ": " + md.get(), pw - 12), x + 6, y + 3, Draw.WHITE);
                    if (svis) {
                        hits.add(new Hit(x, hy1, x + pw, hy2, () -> md.cycle(1), () -> md.cycle(-1), null));
                    }
                } else if (s instanceof ColorSetting c) {
                    Draw.text(g, trim(c.name, pw - 12), x + 6, y + 1, Draw.WHITE);
                    int sx1 = x + 4, sx2 = x + pw - 4, sy1 = y + 10, sy2 = y + 13;
                    int segs = 24;
                    for (int k = 0; k < segs; k++) {
                        int a = sx1 + (sx2 - sx1) * k / segs, b = sx1 + (sx2 - sx1) * (k + 1) / segs;
                        float f = k / (float) segs;
                        int col;
                        if (c.rainbow) col = Draw.rainbow(0.3, f);
                        else if (k == 0) col = 0xFFFFFFFF;
                        else col = 0xFF000000 | Mth.hsvToRgb((f - 0.04f) / 0.96f, 0.8f, 1f);
                        Draw.rect(g, a, sy1, b, sy2, col);
                    }
                    if (!c.rainbow) {
                        double pos = c.sat < 0.05 ? 0.0 : 0.04 + c.hue * 0.96;
                        int mxp = sx1 + (int) ((sx2 - sx1) * pos);
                        Draw.rect(g, mxp, sy1 - 2, mxp + 1, sy2 + 1, 0xFFFFFFFF);
                    }
                    if (svis) {
                        Drag d = (mouseX, x1, x2) -> {
                            double k = Mth.clamp((mouseX - (x1 + 4)) / (double) (x2 - x1 - 8), 0, 1);
                            c.rainbow = false;
                            if (k < 0.04) {
                                c.sat = 0f;
                                c.bri = 1f;
                            } else {
                                c.sat = 0.8f;
                                c.bri = 1f;
                                c.hue = (float) ((k - 0.04) / 0.96);
                            }
                        };
                        hits.add(new Hit(x, hy1, x + pw, hy2, null, () -> c.rainbow = !c.rainbow, d));
                    }
                }
                if (shover) {
                    String extra = s instanceof ModeSetting ? " [ЛКМ/ПКМ - переключить]"
                            : s instanceof ColorSetting ? " [тяни мышью, ПКМ - радуга]" : "";
                    tooltip = s.description + extra;
                }
                y += ROW;
            }
        }
        g.disableScissor();
    }

    private boolean inside(boolean vis, int mx, int my, int x, int y, int clipTop, int clipBottom) {
        return vis && mx >= x && mx < x + pw && my >= Math.max(y, clipTop) && my < Math.min(y + ROW, clipBottom);
    }

    private String trim(String s, int maxWidth) {
        if (Draw.width(s) <= maxWidth) return s;
        while (s.length() > 1 && Draw.width(s + "..") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void toggleExpanded(Module m) {
        if (!expanded.remove(m)) expanded.add(m);
    }

    private void drawTooltip(GuiGraphics g, int mx, int my) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(tooltip), 200);
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, this.font.width(l));
        int h = lines.size() * 10;
        int x = mx + 10, y = my + 12;
        if (x + w + 8 > this.width) x = mx - w - 14;
        if (y + h + 6 > this.height) y = my - h - 10;
        Draw.rect(g, x - 3, y - 3, x + w + 4, y + h + 2, 0xF0100818);
        Draw.outline(g, x - 3, y - 3, x + w + 4, y + h + 2, Draw.accent());
        int yy = y;
        for (FormattedCharSequence l : lines) {
            g.drawString(this.font, l, x, yy, 0xFFFFFFFF);
            yy += 10;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        binding = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x1 && mx < h.x2 && my >= h.y1 && my < h.y2) {
                if (button == 0) {
                    if (h.drag != null) {
                        dragging = h.drag;
                        dragX1 = h.x1;
                        dragX2 = h.x2;
                        dragging.apply(mx, dragX1, dragX2);
                    } else if (h.left != null) {
                        h.left.run();
                    }
                } else if (button == 1 && h.right != null) {
                    h.right.run();
                }
                ModuleManager.markDirty(); // настройки не теряются, даже если игра вылетит до закрытия меню
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.apply(event.x(), dragX1, dragX2);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (Map.Entry<Category, int[]> e : panelRects.entrySet()) {
            int[] r = e.getValue();
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                scroll.merge(e.getKey(), (int) (-sy * 14), Integer::sum);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (binding != null) {
            int k = event.key();
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                binding = null;
            } else if (k == GLFW.GLFW_KEY_DELETE || k == GLFW.GLFW_KEY_BACKSPACE) {
                ModuleManager.setBind(binding, -1);
                binding = null;
            } else {
                ModuleManager.setBind(binding, k);
                binding = null;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
