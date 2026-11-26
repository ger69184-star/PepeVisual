package ru.visual.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
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
import ru.visual.client.module.impl.GraphicsTweak;
import ru.visual.client.module.impl.PepeBackground;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.setting.Setting;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Меню клиента (Right Shift): окно по центру экрана, слева вкладки разделов, справа карточки функций в 1-2 колонки.
 * ЛКМ по карточке - вкл/выкл, ПКМ - раскрыть настройки. Печатай с клавиатуры - поиск по всем функциям.
 * Окно всегда влезает в экран при любом масштабе интерфейса (раньше 7 колонок не помещались и первая уезжала за край).
 */
public class ClickGuiScreen extends Screen {
    private static final int TOP = 32, HEAD = 30, ROW = 16, ROW_TALL = 24, GAP = 6;

    private static final int C_WINDOW = 0xF2111118;
    private static final int C_SIDEBAR = 0x50000000;
    private static final int C_CARD = 0xFF1A1A24;
    private static final int C_CARD_HOVER = 0xFF23232F;
    private static final int C_FIELD = 0xFF2A2A38;
    private static final int C_TEXT = 0xFFE8E8F0;
    private static final int C_MUTED = 0xFF8A8A9A;
    private static final int C_DIM = 0xFF6A6A78;

    /** Выбранная вкладка и раскрытые карточки сохраняются между открытиями меню. */
    private static Category selected = Category.VISUAL;
    private static final Set<Module> expanded = new HashSet<>();

    private interface Drag {
        void apply(double mouseX);
    }

    private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right, Drag drag) {}

    private final List<Hit> hits = new ArrayList<>();
    /** Анимации карточек: [0] - включение, [1] - наведение, [2] - раскрытие. */
    private final Map<Module, float[]> anim = new HashMap<>();
    private final Map<Setting<?>, float[]> setAnim = new HashMap<>();
    /** Плавная прокрутка: ключ вкладки -> [текущее, цель]. */
    private final Map<String, float[]> scroll = new HashMap<>();

    private Drag dragging;
    /** Открытый выпадающий список режима и его якорь (правый край плашки, низ строки). */
    private ModeSetting dropdown;
    private boolean dropdownSeen;
    private int ddRight, ddTop, ddRowTop, ddMinW;
    private Module binding;
    private String tooltip;
    private String search = "";
    private long openTime, lastFrame, ignoreCharsUntil;
    private float dt;
    private float tabY = Float.NaN;
    private float scale = 1f, offY;
    private int contentX1, contentY1, contentX2, contentY2;

    public ClickGuiScreen() {
        super(Component.literal("Pepe Visual"));
    }

    // ---------------------------------------------------------------- координаты (анимация открытия масштабирует окно)

    private double localX(double x) {
        return (x - this.width / 2.0) / scale + this.width / 2.0;
    }

    private double localY(double y) {
        return (y - this.height / 2.0 - offY) / scale + this.height / 2.0;
    }

    private float approach(float cur, float target, float speed) {
        return cur + (target - cur) * Math.min(1f, dt * speed);
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Без ванильной "майнкрафтовской" подложки: только размытие мира, затемнение рисуем сами
        if (this.minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
        else this.renderBlurredBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (openTime == 0) {
            openTime = now;
            lastFrame = now;
        }
        dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float t = Mth.clamp((now - openTime) / 220f, 0f, 1f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);

        hits.clear();
        tooltip = null;

        g.fillGradient(0, 0, this.width, this.height, ((int) (0x70 * e) << 24) | 0x0A0614, ((int) (0xA8 * e) << 24) | 0x040208);

        scale = 0.92f + 0.08f * e;
        offY = (1f - e) * 12f;
        int lmx = (int) Math.floor(localX(mouseX)), lmy = (int) Math.floor(localY(mouseY));

        Draw.noShadow = true;
        try {
            g.pose().pushMatrix();
            g.pose().translate(this.width / 2f, this.height / 2f + offY);
            g.pose().scale(scale, scale);
            g.pose().translate(-this.width / 2f, -this.height / 2f);
            drawWindow(g, lmx, lmy, now);
            g.pose().popMatrix();
            if (tooltip != null) drawTooltip(g, mouseX, mouseY);
        } finally {
            Draw.noShadow = false;
        }
    }

    private void drawWindow(GuiGraphics g, int mx, int my, long now) {
        int accent = Draw.accent();
        int W = Math.min(this.width - 16, 600), H = Math.min(this.height - 16, 370);
        int x0 = (this.width - W) / 2, y0 = (this.height - H) / 2;
        int x1 = x0 + W, y1 = y0 + H;

        // тень и мягкое свечение цветом темы
        Draw.shadow(g, x0, y0, x1, y1, 16, 0xA0000000);
        Draw.shadow(g, x0, y0, x1, y1, 8, Draw.withAlpha(accent, 0x28));
        Draw.round(g, x0, y0, x1, y1, 9, C_WINDOW);
        Draw.roundGradient(g, x0, y0, x1, y0 + TOP, 9, Draw.withAlpha(accent, 0x30), 0x00000000);
        hLine(g, x0 + 10, x1 - 10, y0 + TOP - 1, accent);

        // ----- шапка: название слева, лягушонок ровно по центру, поиск справа
        Draw.textBold(g, "Pepe", x0 + 12, y0 + 12, accent);
        Draw.text(g, " Visual", x0 + 12 + Draw.widthBold("Pepe"), y0 + 12, C_TEXT);

        int cx = (x0 + x1) / 2;
        PepeBackground pepe = ModuleManager.pepe;
        if (pepe != null && pepe.isEnabled()) {
            // Лягушонок в меню один - по центру шапки, размер/прозрачность/покачивание из настроек модуля Pepe
            int bob = pepe.bobbing() ? (int) Math.round(Math.sin(now / 520.0) * 1.5) : 0;
            int ps = pepe.sizePx();
            double pa = pepe.opacity();
            if (pepe.glow()) Draw.shadow(g, cx - 5, y0 + 11, cx + 5, y0 + 21, 12, Draw.withAlpha(accent, (int) (0x55 * pa)));
            PepeBackground.draw(g, cx - ps / 2, y0 + (TOP - ps) / 2 + bob, ps, pa);
        }

        int sw = Math.min(140, W / 4), sx2 = x1 - 10, sx1 = sx2 - sw, sy1 = y0 + 8, sy2 = y0 + 24;
        boolean sHover = in(mx, my, sx1, sy1, sx2, sy2);
        Draw.round(g, sx1, sy1, sx2, sy2, 6, sHover ? 0xFF30303F : C_FIELD);
        if (!search.isEmpty()) Draw.roundOutline(g, sx1, sy1, sx2, sy2, 6, Draw.withAlpha(accent, 0xA0));
        if (search.isEmpty()) {
            Draw.text(g, "Поиск...", sx1 + 7, sy1 + 4, C_DIM);
        } else {
            String shown = search;
            while (shown.length() > 1 && Draw.width(shown) > sw - 18) shown = shown.substring(1);
            Draw.text(g, shown, sx1 + 7, sy1 + 4, C_TEXT);
            if ((now / 500) % 2 == 0) g.fill(sx1 + 8 + Draw.width(shown), sy1 + 3, sx1 + 9 + Draw.width(shown), sy2 - 3, C_TEXT);
        }
        hits.add(new Hit(sx1, sy1, sx2, sy2, null, () -> search = "", null));
        if (sHover) tooltip = "Просто начни печатать - поиск по названию и описанию всех функций. ПКМ или Esc - очистить.";

        // ----- боковая панель с разделами
        int sbW = W >= 480 ? 122 : 96;
        int sbX1 = x0 + 6, sbX2 = x0 + sbW, sbY1 = y0 + TOP + 6, sbY2 = y1 - 6;
        Draw.round(g, sbX1, sbY1, sbX2, sbY2, 7, C_SIDEBAR);

        Category[] cats = Category.values();
        int btnH = 16, buttonsH = btnH * 2 + 4 + 8;
        int tabH = Mth.clamp((sbY2 - sbY1 - 8 - buttonsH) / cats.length, 14, 22);
        int ty0 = sbY1 + 5;
        int selIndex = -1;
        for (int i = 0; i < cats.length; i++) if (cats[i] == selected) selIndex = i;
        float target = ty0 + selIndex * tabH;
        tabY = Float.isNaN(tabY) ? target : approach(tabY, target, 16f);
        int iy = Math.round(tabY);
        boolean searching = !search.isEmpty();
        Draw.round(g, sbX1 + 4, iy, sbX2 - 4, iy + tabH - 2, 5, Draw.withAlpha(accent, searching ? 0x18 : 0x40));
        Draw.round(g, sbX1 + 4, iy + 4, sbX1 + 6, iy + tabH - 6, 1, searching ? C_DIM : accent);

        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            int ty = ty0 + i * tabH;
            boolean hov = in(mx, my, sbX1 + 4, ty, sbX2 - 4, ty + tabH - 2);
            if (hov && c != selected) Draw.round(g, sbX1 + 4, ty, sbX2 - 4, ty + tabH - 2, 5, 0x14FFFFFF);
            List<Module> mods = ModuleManager.byCategory(c);
            int on = 0;
            for (Module m : mods) if (m.isEnabled()) on++;
            int textY = ty + (tabH - 2 - 8) / 2;
            String count = on + "/" + mods.size();
            Draw.text(g, Draw.trim(c.title, sbW - 30 - Draw.width(count)), sbX1 + 11, textY, c == selected && !searching ? C_TEXT : 0xFFA4A4B4);
            Draw.textRight(g, count, sbX2 - 9, textY, on > 0 ? Draw.withAlpha(accent, 0xE0) : C_DIM);
            final Category fc = c;
            hits.add(new Hit(sbX1 + 4, ty, sbX2 - 4, ty + tabH - 2, () -> {
                selected = fc;
                search = "";
            }, null, null));
        }

        int by = sbY2 - 6 - btnH * 2 - 4;
        sideButton(g, sbX1 + 4, by, sbX2 - 4, by + btnH, mx, my, "Аккаунты",
                "Оффлайн-аккаунты: сменить ник, добавить или удалить аккаунт.",
                () -> this.minecraft.setScreen(new AccountScreen(this)));
        sideButton(g, sbX1 + 4, by + btnH + 4, sbX2 - 4, by + btnH * 2 + 4, mx, my, "Редактор HUD",
                "Двигать элементы HUD мышью прямо на экране (ПКМ по элементу - сбросить позицию).",
                () -> {
                    Config.save();
                    this.minecraft.setScreen(new HudEditorScreen(this));
                });

        // ----- карточки функций
        contentX1 = sbX2 + 8;
        contentX2 = x1 - 8;
        contentY1 = y0 + TOP + 6;
        contentY2 = y1 - 18;
        String hint = binding != null ? "Нажми клавишу для " + binding.name + "   Esc - отмена   Delete - сбросить"
                : "ЛКМ - вкл/выкл   ПКМ - настройки   ПКМ по настройке - по умолчанию   печатай - поиск";
        Draw.text(g, Draw.trim(hint, contentX2 - contentX1), contentX1 + 2, y1 - 13, binding != null ? 0xFFFFE070 : C_DIM);

        if (!searching && selected == Category.OPTIMIZATION) {
            drawPresets(g, mx, my, accent);
            contentY1 += 24;
        }

        List<Module> mods = new ArrayList<>();
        if (searching) {
            String q = search.toLowerCase(Locale.ROOT);
            for (Module m : ModuleManager.MODULES) {
                if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) mods.add(m);
            }
        } else {
            mods.addAll(ModuleManager.byCategory(selected));
        }

        int areaW = contentX2 - contentX1;
        int cols = areaW >= 330 ? 2 : 1;
        int cw = (areaW - GAP * (cols - 1)) / cols;

        // анимации и высоты
        int[] colH = new int[cols];
        int[] cardX = new int[mods.size()], cardY = new int[mods.size()], cardH = new int[mods.size()];
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float[] a = anim.computeIfAbsent(m, k -> new float[]{k.isEnabled() ? 1 : 0, 0, expanded.contains(k) ? 1 : 0});
            a[0] = approach(a[0], m.isEnabled() ? 1 : 0, 14f);
            a[2] = approach(a[2], expanded.contains(m) ? 1 : 0, 12f);
            if (Math.abs(a[2] - (expanded.contains(m) ? 1 : 0)) < 0.004f) a[2] = expanded.contains(m) ? 1 : 0;
            int h = HEAD + Math.round(settingsHeight(m) * a[2]);
            int c = i % cols; // постоянная колонка: при раскрытии настроек карточки не прыгают между колонками
            cardX[i] = contentX1 + c * (cw + GAP);
            cardY[i] = colH[c];
            cardH[i] = h;
            colH[c] += h + GAP;
        }
        int total = 0;
        for (int v : colH) total = Math.max(total, v - GAP);
        String key = searching ? "#search" : selected.name();
        float[] sc = scroll.computeIfAbsent(key, k -> new float[2]);
        int maxScroll = Math.max(0, total - (contentY2 - contentY1));
        sc[1] = Mth.clamp(sc[1], 0, maxScroll);
        sc[0] = approach(sc[0], sc[1], 16f);
        int off = Math.round(sc[0]);

        g.enableScissor(contentX1 - 2, contentY1, contentX2 + 2, contentY2);
        for (int i = 0; i < mods.size(); i++) {
            int y = contentY1 + cardY[i] - off;
            if (y > contentY2 || y + cardH[i] < contentY1) continue;
            drawCard(g, mods.get(i), cardX[i], y, cw, cardH[i], mx, my, accent);
        }
        if (mods.isEmpty()) {
            Draw.textCentered(g, searching ? "Ничего не найдено" : "Пусто", (contentX1 + contentX2) / 2, (contentY1 + contentY2) / 2, C_DIM);
        }
        g.disableScissor();

        // полоса прокрутки и затухание краёв
        if (maxScroll > 0) {
            int trackH = contentY2 - contentY1;
            int barH = Math.max(18, trackH * trackH / (total));
            int barY = contentY1 + (int) ((trackH - barH) * (sc[0] / maxScroll));
            Draw.round(g, x1 - 5, barY, x1 - 3, barY + barH, 1, Draw.withAlpha(accent, 0x90));
            if (off > 1) g.fillGradient(contentX1, contentY1, contentX2, contentY1 + 8, C_WINDOW, 0x00111118);
            if (off < maxScroll - 1) g.fillGradient(contentX1, contentY2 - 8, contentX2, contentY2, 0x00111118, C_WINDOW);
        }

        if (dropdown != null && !dropdownSeen) dropdown = null; // строка уехала из вида - список закрываем
        if (dropdown != null) drawDropdown(g, mx, my, accent, y1);
        dropdownSeen = false;
    }

    /** Пресеты оптимизации: включают/выключают сразу несколько кнопок, ничего не нужно искать по файлам. */
    private void drawPresets(GuiGraphics g, int mx, int my, int accent) {
        List<GraphicsTweak<?>> tweaks = new ArrayList<>();
        for (Module m : ModuleManager.byCategory(Category.OPTIMIZATION)) if (m instanceof GraphicsTweak<?> t) tweaks.add(t);
        int state = presetState(tweaks);
        String[] names = {"Максимум FPS", "Баланс", "Всё выключить"};
        String[] tips = {
                "Включить все кнопки оптимизации. Больше всего FPS, картинка заметно проще.",
                "Включить только то, что почти не портит картинку: облака, частицы, тени, смешивание биомов и т.д.",
                "Выключить все кнопки - графика станет как в обычных настройках игры."};
        int gap = 6, n = names.length;
        int bw = (contentX2 - contentX1 - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            int bx1 = contentX1 + i * (bw + gap), bx2 = bx1 + bw, by1 = contentY1, by2 = contentY1 + 18;
            boolean hov = in(mx, my, bx1, by1, bx2, by2);
            boolean active = state == i;
            Draw.round(g, bx1, by1, bx2, by2, 6, active ? Draw.withAlpha(accent, 0x90) : hov ? 0xFF2C2C3A : C_CARD);
            if (!active && hov) Draw.roundOutline(g, bx1, by1, bx2, by2, 6, Draw.withAlpha(accent, 0x70));
            Draw.textCentered(g, Draw.trim(names[i], bw - 8), (bx1 + bx2) / 2, by1 + 5, active ? 0xFFFFFFFF : C_TEXT);
            final int preset = i;
            hits.add(new Hit(bx1, by1, bx2, by2, () -> applyPreset(tweaks, preset), null, null));
            if (hov) tooltip = tips[i];
        }
    }

    /** 0 - всё включено, 1 - ровно "Баланс", 2 - всё выключено, -1 - своё сочетание. */
    private static int presetState(List<GraphicsTweak<?>> tweaks) {
        boolean all = true, none = true, bal = true;
        for (GraphicsTweak<?> t : tweaks) {
            if (t.isEnabled()) none = false;
            else all = false;
            if (t.isEnabled() != t.balanced) bal = false;
        }
        return all ? 0 : bal ? 1 : none ? 2 : -1;
    }

    private static void applyPreset(List<GraphicsTweak<?>> tweaks, int preset) {
        for (GraphicsTweak<?> t : tweaks) {
            boolean want = preset == 0 || (preset == 1 && t.balanced);
            if (t.isEnabled() != want) t.setEnabled(want);
            t.applyNow();
        }
    }

    /** Выпадающий список поверх всего окна. */
    private void drawDropdown(GuiGraphics g, int mx, int my, int accent, int windowBottom) {
        ModeSetting md = dropdown;
        int itemH = 13;
        int w = ddMinW;
        for (String mode : md.modes) w = Math.max(w, Draw.width(mode) + 16);
        int h = md.modes.length * itemH + 4;
        int x2 = ddRight, x1 = Math.max(4, x2 - w);
        int y1 = ddTop;
        if (y1 + h > windowBottom - 4) y1 = Math.max(4, ddRowTop - h + 2); // не влезает вниз - открываем вверх
        // клик мимо списка закрывает его (и больше ничего не делает)
        hits.add(new Hit(-10000, -10000, 10000, 10000, () -> dropdown = null, () -> dropdown = null, null));
        Draw.shadow(g, x1, y1, x1 + w, y1 + h, 8, 0x90000000);
        Draw.round(g, x1, y1, x1 + w, y1 + h, 5, 0xFA1C1C27);
        Draw.roundOutline(g, x1, y1, x1 + w, y1 + h, 5, Draw.withAlpha(accent, 0x90));
        for (int i = 0; i < md.modes.length; i++) {
            int iy = y1 + 2 + i * itemH;
            boolean cur = i == md.index();
            boolean hov = in(mx, my, x1 + 2, iy, x1 + w - 2, iy + itemH);
            if (cur) Draw.round(g, x1 + 2, iy, x1 + w - 2, iy + itemH, 4, Draw.withAlpha(accent, 0x60));
            else if (hov) Draw.round(g, x1 + 2, iy, x1 + w - 2, iy + itemH, 4, 0x18FFFFFF);
            Draw.text(g, md.modes[i], x1 + 8, iy + 3, cur || hov ? 0xFFFFFFFF : 0xFFC0C0D0);
            final String mode = md.modes[i];
            hits.add(new Hit(x1 + 2, iy, x1 + w - 2, iy + itemH, () -> {
                md.set(mode);
                dropdown = null;
            }, () -> dropdown = null, null));
        }
        if (in(mx, my, x1, y1, x1 + w, y1 + h)) tooltip = null;
    }

    private void sideButton(GuiGraphics g, int bx1, int by1, int bx2, int by2, int mx, int my, String label, String tip, Runnable action) {
        boolean hov = in(mx, my, bx1, by1, bx2, by2);
        int accent = Draw.accent();
        Draw.round(g, bx1, by1, bx2, by2, 5, hov ? Draw.withAlpha(accent, 0x70) : Draw.withAlpha(accent, 0x30));
        Draw.textCentered(g, label, (bx1 + bx2) / 2, by1 + (by2 - by1 - 8) / 2, C_TEXT);
        hits.add(new Hit(bx1, by1, bx2, by2, action, null, null));
        if (hov) tooltip = tip;
    }

    private static int rowHeight(Setting<?> s) {
        return s instanceof NumSetting || s instanceof ColorSetting ? ROW_TALL : ROW;
    }

    private static int settingsHeight(Module m) {
        int h = ROW + 6; // строка клавиши + отступы
        for (Setting<?> s : m.settings) if (!s.hidden) h += rowHeight(s);
        return h;
    }

    private void drawCard(GuiGraphics g, Module m, int x, int y, int w, int h, int mx, int my, int accent) {
        float[] a = anim.get(m);
        boolean headHover = in(mx, my, x, y, x + w, y + HEAD) && my >= contentY1 && my < contentY2;
        a[1] = approach(a[1], headHover ? 1 : 0, 18f);

        int bg = Draw.mix(C_CARD, C_CARD_HOVER, a[1]);
        bg = Draw.mix(bg, accent | 0xFF000000, 0.12f * a[0]);
        Draw.round(g, x, y, x + w, y + h, 7, bg);
        if (a[0] > 0.02f) Draw.roundOutline(g, x, y, x + w, y + h, 7, Draw.withAlpha(accent, (int) (0x90 * a[0])));

        // название и описание
        int nameColor = Draw.mix(0xFFB8B8C8, 0xFFFFFFFF, a[0]);
        Draw.textBold(g, Draw.trim(m.name, w - 46), x + 9, y + 6, nameColor);
        String desc = Draw.trim(m.description, w - 46);
        Draw.text(g, desc, x + 9, y + 17, C_MUTED);

        // переключатель
        int swX = x + w - 32, swY = y + 9;
        Draw.round(g, swX, swY, swX + 22, swY + 12, 6, Draw.mix(0xFF3A3A4A, accent | 0xFF000000, a[0]));
        int knob = swX + 2 + Math.round(10 * a[0]);
        Draw.circle(g, knob, swY + 2, 8, 0xFFFFFFFF);

        if (headHover) {
            tooltip = (desc.equals(m.description) ? "" : m.description + "\n") + (expanded.contains(m) ? "ПКМ - свернуть настройки" : "ПКМ - настройки и клавиша");
        }
        hits.add(new Hit(x, Math.max(y, contentY1), x + w, Math.min(y + HEAD, contentY2), m::toggle, () -> {
            if (!expanded.remove(m)) expanded.add(m);
        }, null));

        if (a[2] <= 0.01f) return;

        // ----- настройки (раскрываются плавно)
        int cardBottom = y + h;
        g.enableScissor(x, y + HEAD - 1, x + w, cardBottom);
        g.fill(x + 8, y + HEAD - 1, x + w - 8, y + HEAD, 0x16FFFFFF);
        int rx1 = x + 10, rx2 = x + w - 10;
        int ry = y + HEAD + 2;

        // клавиша
        {
            boolean hov = rowHover(mx, my, x, ry, w, ROW, cardBottom);
            if (hov) Draw.round(g, x + 4, ry, x + w - 4, ry + ROW, 4, 0x10FFFFFF);
            String keyName = binding == m ? "..." : ModuleManager.bindName(m);
            int pw = Draw.width(keyName) + 12;
            Draw.text(g, "Клавиша", rx1, ry + 4, C_TEXT);
            Draw.round(g, rx2 - pw, ry + 2, rx2, ry + ROW - 2, 4, binding == m ? Draw.withAlpha(accent, 0xB0) : C_FIELD);
            Draw.textCentered(g, keyName, rx2 - pw / 2, ry + 4, binding == m ? 0xFFFFFFFF : 0xFFD0D0E0);
            if (rowVisible(ry, ROW, cardBottom)) {
                hits.add(new Hit(x, ry, x + w, ry + ROW, () -> binding = m, () -> ModuleManager.setBind(m, -1), null));
            }
            if (hov) tooltip = "Клавиша быстрого включения. ЛКМ - назначить (затем нажми клавишу, Esc - отмена), ПКМ или Delete - сбросить.";
            ry += ROW;
        }

        for (Setting<?> s : m.settings) {
            if (s.hidden) continue;
            int rh = rowHeight(s);
            boolean hov = rowHover(mx, my, x, ry, w, rh, cardBottom);
            boolean vis = rowVisible(ry, rh, cardBottom);
            if (hov) Draw.round(g, x + 4, ry, x + w - 4, ry + rh, 4, 0x10FFFFFF);
            float[] sa = setAnim.computeIfAbsent(s, k -> new float[]{k instanceof BoolSetting b && b.get() ? 1 : 0});

            if (s instanceof BoolSetting b) {
                sa[0] = approach(sa[0], b.get() ? 1 : 0, 16f);
                Draw.text(g, Draw.trim(b.name, rx2 - rx1 - 24), rx1, ry + 4, Draw.mix(C_MUTED, C_TEXT, sa[0]));
                int tx = rx2 - 16, tyy = ry + 4;
                Draw.round(g, tx, tyy, tx + 16, tyy + 8, 4, Draw.mix(0xFF3A3A4A, accent | 0xFF000000, sa[0]));
                Draw.circle(g, tx + 1 + Math.round(8 * sa[0]), tyy + 1, 6, 0xFFFFFFFF);
                if (vis) hits.add(new Hit(x, ry, x + w, ry + rh, b::toggle, () -> b.set(b.def), null));
            } else if (s instanceof NumSetting n) {
                float f = (float) ((n.get() - n.min) / (n.max - n.min));
                sa[0] = approach(sa[0], f, 20f);
                String val = n.display();
                Draw.text(g, Draw.trim(n.name, rx2 - rx1 - Draw.width(val) - 8), rx1, ry + 3, C_TEXT);
                Draw.textRight(g, val, rx2, ry + 3, Draw.mix(accent | 0xFF000000, 0xFFFFFFFF, 0.45f));
                int ty1 = ry + 15;
                Draw.round(g, rx1, ty1, rx2, ty1 + 3, 1, C_FIELD);
                int fx = rx1 + Math.round((rx2 - rx1) * sa[0]);
                Draw.round(g, rx1, ty1, Math.max(rx1 + 2, fx), ty1 + 3, 1, accent | 0xFF000000);
                if (hov) Draw.circle(g, fx - 5, ty1 - 3, 10, Draw.withAlpha(accent, 0x50));
                Draw.circle(g, fx - 3, ty1 - 1, 6, 0xFFFFFFFF);
                if (vis) {
                    final int a1 = rx1, a2 = rx2;
                    Drag d = mouseX -> {
                        double k = Mth.clamp((mouseX - a1) / (double) (a2 - a1), 0, 1);
                        n.set(n.min + k * (n.max - n.min));
                    };
                    hits.add(new Hit(x, ry, x + w, ry + rh, null, n::reset, d));
                }
            } else if (s instanceof ModeSetting md) {
                String val = md.get();
                int pw = Math.min(Draw.width(val) + 12, (rx2 - rx1) / 2 + 20);
                String shown = Draw.trim(val, pw - 10);
                Draw.text(g, Draw.trim(md.name, rx2 - rx1 - pw - 6), rx1, ry + 4, C_TEXT);
                boolean open = dropdown == md;
                Draw.round(g, rx2 - pw - 8, ry + 2, rx2, ry + rh - 2, 4, Draw.withAlpha(accent, open ? 0x80 : hov ? 0x60 : 0x38));
                Draw.textCentered(g, shown, rx2 - 8 - pw / 2, ry + 4, 0xFFFFFFFF);
                // стрелка списка (рисуем сами - в шрифте может не быть символа)
                int ax = rx2 - 8, ay = ry + rh / 2;
                for (int k = 0; k < 3; k++) {
                    int yy = open ? ay + 1 - k : ay - 1 + k;
                    g.fill(ax - 2 + k, yy, ax + 3 - k, yy + 1, 0xFFDDDDEE);
                }
                if (vis) {
                    hits.add(new Hit(x, ry, x + w, ry + rh, () -> {
                        dropdown = open ? null : md;
                        dropdownSeen = true;
                    }, () -> md.set(md.def), null));
                    if (open) {
                        dropdownSeen = true;
                        ddRight = rx2;
                        ddTop = ry + rh - 1;
                        ddRowTop = ry + 1;
                        ddMinW = pw + 8;
                    }
                }
            } else if (s instanceof ColorSetting c) {
                Draw.text(g, Draw.trim(c.name, rx2 - rx1 - 16), rx1, ry + 3, C_TEXT);
                Draw.circle(g, rx2 - 9, ry + 2, 9, c.argb() | 0xFF000000);
                int sy1 = ry + 15, sy2 = ry + 19;
                int segs = 32;
                for (int k = 0; k < segs; k++) {
                    int xa = rx1 + (rx2 - rx1) * k / segs, xb = rx1 + (rx2 - rx1) * (k + 1) / segs;
                    float ff = k / (float) segs;
                    int col;
                    if (c.rainbow) col = Draw.rainbow(0.3, ff);
                    else if (k == 0) col = 0xFFFFFFFF;
                    else col = 0xFF000000 | Mth.hsvToRgb((ff - 0.04f) / 0.96f, 0.8f, 1f);
                    g.fill(xa, sy1, xb, sy2, col);
                }
                if (!c.rainbow) {
                    double pos = c.sat < 0.05 ? 0.0 : 0.04 + c.hue * 0.96;
                    int mxp = rx1 + (int) ((rx2 - rx1) * pos);
                    Draw.circle(g, mxp - 3, sy1 - 1, 6, 0xFFFFFFFF);
                }
                if (vis) {
                    final int a1 = rx1, a2 = rx2;
                    Drag d = mouseX -> {
                        double k = Mth.clamp((mouseX - a1) / (double) (a2 - a1), 0, 1);
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
                    hits.add(new Hit(x, ry, x + w, ry + rh, null, () -> c.rainbow = !c.rainbow, d));
                }
            }
            if (hov) {
                String extra = s instanceof ModeSetting ? "\nЛКМ - выбрать из списка, ПКМ - по умолчанию (" + s.def + ")"
                        : s instanceof ColorSetting ? "\nТяни мышью по полосе, ПКМ - радуга"
                        : s instanceof NumSetting n2 ? "\nЗажми и тяни, ПКМ - по умолчанию (" + defText(n2) + ")"
                        : s instanceof BoolSetting b2 ? "\nПКМ - по умолчанию (" + (b2.def ? "вкл" : "выкл") + ")" : "";
                tooltip = s.description + extra;
            }
            ry += rh;
        }
        g.disableScissor();
    }

    private static String defText(NumSetting n) {
        return n.step >= 1 ? String.valueOf(Math.round(n.def)) : String.format(Locale.ROOT, n.step >= 0.1 ? "%.1f" : "%.2f", n.def);
    }

    private boolean rowVisible(int ry, int rh, int cardBottom) {
        return ry >= contentY1 && ry + rh <= contentY2 && ry + rh <= cardBottom;
    }

    private boolean rowHover(int mx, int my, int x, int ry, int w, int rh, int cardBottom) {
        return rowVisible(ry, rh, cardBottom) && in(mx, my, x, ry, x + w, ry + rh);
    }

    private static boolean in(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    /** Горизонтальная линия, затухающая к краям. */
    private static void hLine(GuiGraphics g, int x1, int x2, int y, int color) {
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            float c = (i + 0.5f) / segs;
            float alpha = 1f - Math.abs(c - 0.5f) * 2f;
            int xa = x1 + (x2 - x1) * i / segs, xb = x1 + (x2 - x1) * (i + 1) / segs;
            g.fill(xa, y, xb, y + 1, Draw.withAlpha(color, (int) (0xB0 * alpha)));
        }
    }

    private void drawTooltip(GuiGraphics g, int mx, int my) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String part : tooltip.split("\n")) lines.addAll(this.font.split(Draw.comp(part), 210));
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, this.font.width(l));
        int h = lines.size() * 10;
        int x = mx + 12, y = my + 12;
        if (x + w + 10 > this.width) x = mx - w - 14;
        if (y + h + 8 > this.height) y = my - h - 10;
        x = Math.max(4, x);
        y = Math.max(4, y);
        Draw.shadow(g, x - 5, y - 4, x + w + 5, y + h + 3, 8, 0x80000000);
        Draw.round(g, x - 5, y - 4, x + w + 5, y + h + 3, 5, 0xF5181822);
        Draw.roundOutline(g, x - 5, y - 4, x + w + 5, y + h + 3, 5, Draw.withAlpha(Draw.accent(), 0x90));
        int yy = y;
        for (FormattedCharSequence l : lines) {
            g.drawString(this.font, l, x, yy, 0xFFFFFFFF, false);
            yy += 10;
        }
    }

    // ---------------------------------------------------------------- ввод

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = localX(event.x()), my = localY(event.y());
        int button = event.button();
        Module wasBinding = binding;
        binding = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x1 && mx < h.x2 && my >= h.y1 && my < h.y2) {
                if (button == 0) {
                    if (h.drag != null) {
                        dragging = h.drag;
                        dragging.apply(mx);
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
        if (wasBinding != null) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.apply(localX(event.x()));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) ModuleManager.markDirty();
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double lx = localX(mx), ly = localY(my);
        if (lx >= contentX1 - 4 && lx < contentX2 + 8 && ly >= contentY1 && ly < contentY2) {
            String key = search.isEmpty() ? selected.name() : "#search";
            scroll.computeIfAbsent(key, k -> new float[2])[1] -= (float) (sy * 28);
            dropdown = null;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (binding != null) {
            ignoreCharsUntil = System.currentTimeMillis() + 150; // символ этой же клавиши не должен попасть в поиск
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
        if (k == GLFW.GLFW_KEY_ESCAPE && dropdown != null) {
            dropdown = null;
            return true;
        }
        if (k == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (System.currentTimeMillis() < ignoreCharsUntil) return true;
        if (binding == null && event.isAllowedChatCharacter() && search.length() < 32) {
            String ch = event.codepointAsString();
            if (search.isEmpty() && ch.isBlank()) return true;
            search += ch;
            scroll.remove("#search");
            return true;
        }
        return super.charTyped(event);
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
