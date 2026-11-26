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

public class ItemCounter extends HudModule implements Config.DefaultOn {
    private static final Item[] TRACKED = {Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE,
            Items.ENDER_PEARL, Items.SPLASH_POTION, Items.FIREWORK_ROCKET};
    private ItemStack[] icons;

    public ItemCounter() {
        super("ItemCounter", "Считает важные предметы в инвентаре: тотемы, яблоки, жемчуги эндера, зелья, фейерверки.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (icons == null) {
            icons = new ItemStack[TRACKED.length];
            for (int i = 0; i < TRACKED.length; i++) icons[i] = new ItemStack(TRACKED[i]);
        }
        int[] counts = new int[TRACKED.length];
        var inv = mc.player.getInventory();
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (st.isEmpty()) continue;
            for (int i = 0; i < TRACKED.length; i++) {
                if (st.is(TRACKED[i])) counts[i] += st.getCount();
            }
        }
        int w = mc.getWindow().getGuiScaledWidth() + ox();
        int h = mc.getWindow().getGuiScaledHeight();
        int y = right(h / 2 - 60);
        for (int i = 0; i < TRACKED.length; i++) {
            if (counts[i] == 0) continue;
            String s = "x" + counts[i];
            int tw = Draw.width(s);
            Draw.panel(g, w - tw - 28, y - 1, w - 3, y + 17, Draw.bg(), false);
            g.renderItem(icons[i], w - tw - 26, y);
            Draw.text(g, s, w - tw - 7, y + 5, Draw.WHITE);
            y += 19;
        }
    }
}
