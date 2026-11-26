package ru.visual.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import org.lwjgl.glfw.GLFW;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.List;

/**
 * Редактор HUD: элементы двигаются мышью прямо на экране.
 * ЛКМ + тянуть - переместить, ПКМ - вернуть на место по умолчанию, R - сбросить все.
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudModule dragging;
    private double lastX, lastY;
    private double accX, accY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Редактор HUD"));
        this.parent = parent;
    }

    private HudModule at(double mx, double my) {
        HudModule found = null;
        for (Module m : ModuleManager.MODULES) {
            if (!(m instanceof HudModule h) || !m.isEnabled()) continue;
            int[] b = h.lastBounds();
            if (b != null && mx >= b[0] - 1 && mx < b[2] + 1 && my >= b[1] - 1 && my < b[3] + 1) found = h;
        }
        return found;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Без затемнения: HUD должен быть виден как в игре
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        HudModule hover = dragging != null ? dragging : at(mouseX, mouseY);
        int accent = Draw.accent();
        List<String> hidden = new ArrayList<>();
        for (Module m : ModuleManager.MODULES) {
            if (!(m instanceof HudModule h) || !m.isEnabled()) continue;
            int[] b = h.lastBounds();
            if (b == null) {
                hidden.add(m.name);
                continue;
            }
            boolean hl = h == hover;
            Draw.round(g, b[0] - 2, b[1] - 2, b[2] + 2, b[3] + 2, 4, hl ? Draw.withAlpha(accent, 0x40) : 0x14FFFFFF);
            Draw.roundOutline(g, b[0] - 2, b[1] - 2, b[2] + 2, b[3] + 2, 4, hl ? accent : 0x60FFFFFF);
            if (hl) {
                int ty = b[1] - 13 < 0 ? b[3] + 4 : b[1] - 13;
                int tw = Draw.width(m.name) + 8;
                Draw.round(g, b[0] - 2, ty - 2, b[0] - 2 + tw, ty + 10, 3, 0xE0101018);
                Draw.text(g, m.name, b[0] + 2, ty, Draw.WHITE);
            }
        }
        // направляющая по центру экрана - удобно выровнять элемент
        if (dragging != null) {
            int[] b = dragging.lastBounds();
            if (b != null && Math.abs((b[0] + b[2]) / 2 - this.width / 2) <= 1) {
                g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, Draw.withAlpha(accent, 0x90));
            }
        }
        String hint = "ЛКМ - тянуть   ПКМ - на место   R - сбросить все   Esc - назад";
        int w = Draw.width(hint);
        Draw.round(g, this.width / 2 - w / 2 - 6, 4, this.width / 2 + w / 2 + 6, 18, 5, 0xD0101018);
        Draw.textCentered(g, hint, this.width / 2, 7, 0xFFE0E0E0);
        if (!hidden.isEmpty()) {
            // элементы, которым сейчас нечего показать (нет цели, нет эффектов...), иначе непонятно, куда они делись
            String line = Draw.trim("Сейчас не видны: " + String.join(", ", hidden), this.width - 20);
            int lw = Draw.width(line);
            Draw.round(g, this.width / 2 - lw / 2 - 6, 21, this.width / 2 + lw / 2 + 6, 35, 5, 0xB0101018);
            Draw.textCentered(g, line, this.width / 2, 24, 0xFFA0A0B0);
        }
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HudModule h = at(event.x(), event.y());
        if (h != null) {
            if (event.button() == 0) {
                dragging = h;
                lastX = event.x();
                lastY = event.y();
                accX = accY = 0;
            } else if (event.button() == 1) {
                h.resetPosition();
                ModuleManager.markDirty();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            accX += event.x() - lastX;
            accY += event.y() - lastY;
            lastX = event.x();
            lastY = event.y();
            int ix = (int) accX, iy = (int) accY;
            if (ix != 0 || iy != 0) {
                dragging.moveBy(ix, iy);
                accX -= ix;
                accY -= iy;
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            ModuleManager.markDirty();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_R && dragging == null) {
            for (Module m : ModuleManager.MODULES) if (m instanceof HudModule h) h.resetPosition();
            ModuleManager.markDirty();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
