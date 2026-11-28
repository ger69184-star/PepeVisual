package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.visual.client.module.HudModule;
import ru.visual.client.util.Draw;

public class Keystrokes extends HudModule {
    public Keystrokes() {
        super("Keystrokes", "Показывает нажатые клавиши движения, прыжка и кнопок мыши.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        int x = 8 + ox(), y = left(mc.getWindow().getGuiScaledHeight() / 2 + 30);
        key(g, "W", x + 20, y, 18, mc.options.keyUp.isDown());
        key(g, "A", x, y + 20, 18, mc.options.keyLeft.isDown());
        key(g, "S", x + 20, y + 20, 18, mc.options.keyDown.isDown());
        key(g, "D", x + 40, y + 20, 18, mc.options.keyRight.isDown());
        key(g, Lang.t("ПРЫЖОК"), x, y + 40, 58, mc.options.keyJump.isDown());
        key(g, Lang.t("ЛКМ"), x, y + 60, 28, mc.options.keyAttack.isDown());
        key(g, Lang.t("ПКМ"), x + 30, y + 60, 28, mc.options.keyUse.isDown());
    }

    private void key(GuiGraphics g, String label, int x, int y, int w, boolean down) {
        Draw.panel(g, x, y, x + w, y + 18, down ? Draw.withAlpha(Draw.accent(), 0xC0) : Draw.bg());
        Draw.textCentered(g, label, x + w / 2, y + 5, down ? Draw.WHITE : Draw.GRAY);
    }
}
