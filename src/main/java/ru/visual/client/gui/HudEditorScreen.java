package ru.visual.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.util.Draw;

/**
 * Редактор HUD: элементы двигаются мышью прямо на экране.
 * ЛКМ + тянуть - переместить, ПКМ - вернуть на место по умолчанию.
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
        for (Module m : ModuleManager.MODULES) {
            if (!(m instanceof HudModule h) || !m.isEnabled()) continue;
            int[] b = h.lastBounds();
            if (b == null) continue;
            boolean hl = h == hover;
            Draw.rect(g, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, hl ? 0x308A63FF : 0x18FFFFFF);
            Draw.outline(g, b[0] - 2, b[1] - 2, b[2] + 2, b[3] + 2, hl ? Draw.accent() : 0x80FFFFFF);
            if (hl) {
                int ty = b[1] - 12 < 0 ? b[3] + 3 : b[1] - 12;
                Draw.text(g, m.name, b[0], ty, Draw.WHITE);
            }
        }
        String hint = "Редактор HUD: тяни элементы ЛКМ  |  ПКМ - сбросить позицию  |  ESC - выход";
        int w = Draw.width(hint);
        Draw.rect(g, this.width / 2 - w / 2 - 4, 4, this.width / 2 + w / 2 + 4, 17, 0xC0101018);
        Draw.textCentered(g, hint, this.width / 2, 7, 0xFFE0E0E0);
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
    public void onClose() {
        Config.save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
