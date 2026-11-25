package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.util.Draw;

public class Cooldowns extends HudModule implements Config.DefaultOn {
    private static final Item[] TRACKED = {Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.CHORUS_FRUIT, Items.SHIELD, Items.FIREWORK_ROCKET};
    private ItemStack[] icons;

    public Cooldowns() {
        super("Cooldowns", "Показывает перезарядку предметов: жемчуг эндера, яблоки, хорус, щит.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (icons == null) {
            icons = new ItemStack[TRACKED.length];
            for (int i = 0; i < TRACKED.length; i++) icons[i] = new ItemStack(TRACKED[i]);
        }
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int count = 0;
        float[] pct = new float[TRACKED.length];
        for (int i = 0; i < TRACKED.length; i++) {
            pct[i] = mc.player.getCooldowns().getCooldownPercent(icons[i], pt);
            if (pct[i] > 0f) count++;
        }
        if (count == 0) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 - count * 11 + ox();
        int y = h / 2 + 70 + oy();
        for (int i = 0; i < TRACKED.length; i++) {
            if (pct[i] <= 0f) continue;
            g.renderItem(icons[i], x, y);
            Draw.rect(g, x, y + 17, x + 16, y + 19, 0xFF202028);
            Draw.rect(g, x, y + 17, x + (int) (16 * (1f - pct[i])), y + 19, Draw.accent());
            x += 22;
        }
    }
}
