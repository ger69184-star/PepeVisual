package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.PlayerUtil;

public class Watermark extends HudModule implements Config.DefaultOn {
    private final BoolSetting stats = add(new BoolSetting("FPS и пинг", "Добавляет в плашку ник, FPS и пинг.", true));

    public Watermark() {
        super("Watermark", "Небольшая плашка с названием клиента в левом верхнем углу.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        String title = "Pepe Visual";
        String rest = stats.get() ? "  |  " + mc.getUser().getName() + "  |  " + mc.getFps() + " fps  |  " + PlayerUtil.ping(mc) + " ms" : "";
        int x = 4 + ox(), y = 4 + oy();
        int w = Draw.width(title) + Draw.width(rest) + 8;
        Draw.panel(g, x, y, x + w, y + 13, Draw.bg());
        Draw.rect(g, x + Draw.r(), y, x + w - Draw.r(), y + 1, Draw.bar(0));
        Draw.text(g, title, x + 4, y + 3, Draw.accent());
        Draw.text(g, rest, x + 4 + Draw.width(title), y + 3, Draw.GRAY);
    }
}
