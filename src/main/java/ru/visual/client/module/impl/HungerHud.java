package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.food.FoodData;
import ru.visual.client.module.HudModule;
import ru.visual.client.util.Draw;

import java.util.Locale;

/** Скрытое насыщение (saturation): сколько ещё голод не будет падать. */
public class HungerHud extends HudModule {
    public HungerHud() {
        super("Saturation", "Показывает сытость и скрытое насыщение над полоской голода.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.player.isCreative() || mc.player.isSpectator() || mc.options.hideGui) return;
        FoodData f = mc.player.getFoodData();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        String s = String.format(Locale.ROOT, "Голод %d/20  Насыщение %.1f", f.getFoodLevel(), f.getSaturationLevel());
        int x = w / 2 + 91 - Draw.width(s) + ox();
        int y = h - 60 + oy();
        int col = f.getSaturationLevel() <= 0 ? 0xFFFF8080 : 0xFFFFD27F;
        Draw.rect(g, x - 2, y - 1, x + Draw.width(s) + 2, y + 9, 0x70000000);
        Draw.text(g, s, x, y, col);
    }
}
